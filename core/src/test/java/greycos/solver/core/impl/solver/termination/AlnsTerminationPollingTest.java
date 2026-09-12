package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class AlnsTerminationPollingTest {

  @Test
  void consumedProbesResetIdleChecksAndKeepTheLogicalSamplingCadence() {
    var fixture = new Fixture();
    var polling = fixture.polling(new TimeMillisSpentTermination<>(100));
    assertThat(polling.checkProbe()).isFalse();
    fixture.clock.now = 1_200;
    for (int i = 0; i < 30; i++) assertThat(polling.checkProbe()).isFalse();
    for (int i = 0; i < 31; i++) {
      polling.logicalProbeConsumed();
      assertThat(polling.checkProbe()).isFalse();
    }
    assertThat(fixture.clock.reads).isEqualTo(1);
    polling.logicalProbeConsumed();
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(2);
  }

  @Test
  void customLoopWithoutProbesRefreshesTimeOnTheThirtySecondCheck() {
    var fixture = new Fixture();
    var polling = fixture.polling(new TimeMillisSpentTermination<>(100));
    assertThat(polling.checkProbe()).isFalse();
    fixture.clock.now = 1_200;
    for (int check = 2; check <= 31; check++) assertThat(polling.checkProbe()).isFalse();
    assertThat(fixture.clock.reads).isEqualTo(1);
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(2);
    assertThat(fixture.calculations).hasValue(0);
  }

  @Test
  void forcedWaitCheckAndInvalidationRefreshWithoutConsumingProbes() {
    var fixture = new Fixture();
    var polling = fixture.polling(new TimeMillisSpentTermination<>(100));
    assertThat(polling.checkProbe()).isFalse();
    fixture.clock.now = 1_200;
    assertThat(polling.checkNow()).isTrue();
    fixture.clock.now = 1_150;
    polling.invalidate();
    assertThat(fixture.clock.reads).isEqualTo(2);
    assertThat(polling.checkProbe()).isFalse();
    assertThat(fixture.clock.reads).isEqualTo(3);
  }

  @Test
  void preservesSolverAndPhaseOriginsForASharedBridgedTimeLeaf() {
    var fixture = new Fixture();
    var time = new TimeMillisSpentTermination<TestdataSolution>(200);
    var polling =
        fixture.polling(new OrCompositeTermination<>(time, PhaseTermination.bridge(time)));
    fixture.clock.now = 1_199;
    assertThat(polling.checkProbe()).isFalse();
    // The bridge's phase fallback shares the phase observation, not the solver observation.
    assertThat(fixture.clock.reads).isEqualTo(2);
    fixture.clock.now = 1_200;
    assertThat(polling.checkProbe()).isFalse();
    assertThat(polling.checkNow()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(4);
  }

  @Test
  void nestedAndOrKeepsScoreBudgetsLiveAndPreservesShortCircuiting() {
    var fixture = new Fixture();
    var polling =
        fixture.polling(
            new OrCompositeTermination<>(
                new AndCompositeTermination<>(
                    new TimeMillisSpentTermination<>(100),
                    new ScoreCalculationCountTermination<>(5)),
                new ScoreCalculationCountTermination<>(3)));
    assertThat(polling.checkProbe()).isFalse();
    fixture.calculations.set(2);
    fixture.clock.now = 1_200;
    assertThat(polling.checkProbe()).isFalse();
    fixture.calculations.set(3);
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(1);

    var shortCircuit =
        fixture.polling(
            new OrCompositeTermination<>(
                new ScoreCalculationCountTermination<>(3), new TimeMillisSpentTermination<>(100)));
    assertThat(shortCircuit.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(1);
  }

  @Test
  void filtersInapplicablePhaseChildrenWithoutChangingEmptyAndOrResults() {
    var fixture = new Fixture();
    var customScope = new CustomPhaseScope<>(fixture.solverScope, 0);
    var inapplicable = new UnimprovedStepCountTermination<TestdataSolution>(0);
    var andPolling =
        new AlnsTerminationPolling<>(customScope, new AndCompositeTermination<>(inapplicable));
    var orPolling =
        new AlnsTerminationPolling<>(customScope, new OrCompositeTermination<>(inapplicable));
    assertThat(andPolling.supportedForRepairAttempts()).isTrue();
    assertThat(andPolling.checkProbe()).isTrue();
    assertThat(orPolling.checkProbe()).isFalse();
  }

  @Test
  void customLeafMakesTheEntireGraphStrictIncludingOriginalShortCircuiting() {
    var fixture = new Fixture();
    @SuppressWarnings("unchecked")
    var custom = (MockablePhaseTermination<TestdataSolution>) mock(MockablePhaseTermination.class);
    when(custom.isApplicableTo(AlnsPhaseScope.class)).thenReturn(true);
    when(custom.isPhaseTerminated(fixture.phaseScope)).thenReturn(false);
    var polling =
        fixture.polling(
            new OrCompositeTermination<>(new TimeMillisSpentTermination<>(100), custom));
    assertThat(polling.supportedForRepairAttempts()).isFalse();
    assertThat(polling.checkProbe()).isFalse();
    fixture.clock.now = 1_150;
    assertThat(polling.checkProbe()).isFalse();
    fixture.clock.now = 1_200;
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(3);
    verify(custom, times(2)).isPhaseTerminated(fixture.phaseScope);
  }

  @Test
  void customSolverBridgeAndStatefulTerminationUseStrictFallback() {
    var fixture = new Fixture();
    @SuppressWarnings("unchecked")
    var custom =
        (MockableSolverTermination<TestdataSolution>) mock(MockableSolverTermination.class);
    when(custom.isSolverTerminated(fixture.solverScope)).thenReturn(false, true);
    var polling = fixture.polling(PhaseTermination.bridge(custom));
    assertThat(polling.supportedForRepairAttempts()).isFalse();
    assertThat(polling.checkProbe()).isFalse();
    assertThat(polling.checkProbe()).isTrue();
    verify(custom, times(2)).isSolverTerminated(fixture.solverScope);
    var stateful =
        fixture.polling(
            new DiminishedReturnsTermination<TestdataSolution, SimpleScore>(1_000, 0.1));
    assertThat(stateful.supportedForRepairAttempts()).isFalse();
  }

  @Test
  void usesUnimprovedTerminationOwnClockAndInvalidatesAfterImprovement() {
    var fixture = new Fixture();
    var timerClock = new CountingClock(1_100);
    var time = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(100, timerClock);
    time.phaseStarted(fixture.phaseScope);
    time.stepStarted(new AlnsStepScope<>(fixture.phaseScope));
    timerClock.reads = 0;
    var polling = fixture.polling(time);
    assertThat(polling.checkProbe()).isFalse();
    timerClock.now = 1_200;
    assertThat(polling.checkProbe()).isFalse();
    assertThat(polling.checkNow()).isTrue();
    fixture.solverScope.setBestSolutionTimeMillis(1_200L);
    polling.invalidate();
    assertThat(polling.checkProbe()).isFalse();
    assertThat(timerClock.reads).isEqualTo(3);
    assertThat(fixture.clock.reads).isZero();
  }

  @Test
  void thresholdTimeKeepsItsOwnClockAndStrictGreaterThanBoundary() {
    var fixture = new Fixture();
    var timerClock = new CountingClock(1_100);
    var time =
        new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<TestdataSolution>(
            100, SimpleScore.ONE, timerClock);
    time.solvingStarted(fixture.solverScope);
    time.phaseStarted(fixture.phaseScope);
    timerClock.reads = 0;
    var polling = fixture.polling(time);
    assertThat(polling.supportedForRepairAttempts()).isTrue();
    timerClock.now = 1_200;
    assertThat(polling.checkProbe()).isFalse();
    timerClock.now = 1_201;
    assertThat(polling.checkProbe()).isFalse();
    assertThat(polling.checkNow()).isTrue();
    assertThat(timerClock.reads).isEqualTo(2);
    assertThat(fixture.clock.reads).isZero();
  }

  @Test
  void yieldingRefreshesAfterResumingEvenWithoutConsumedProbes() {
    var fixture = new Fixture();
    fixture.solverScope.setRunnableThreadSemaphore(
        new Semaphore(0) {
          @Override
          public void acquire() throws InterruptedException {
            super.acquire();
            fixture.clock.now += 50;
          }
        });
    var polling = fixture.polling(new TimeMillisSpentTermination<>(100));
    assertThat(polling.checkProbe()).isFalse();
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(2);
  }

  @Test
  void cancellationAndProblemChangesStayLiveBetweenTimeSamples() {
    var fixture = new Fixture();
    var plumbing = new BasicPlumbingTermination<TestdataSolution>(false);
    var polling =
        fixture.polling(
            new OrCompositeTermination<>(new TimeMillisSpentTermination<>(100), plumbing));
    assertThat(polling.checkProbe()).isFalse();
    plumbing.terminateEarly();
    assertThat(polling.checkProbe()).isTrue();
    plumbing.resetTerminateEarly();
    assertThat(polling.checkProbe()).isFalse();
    plumbing.addProblemChanges(List.of((solution, director) -> {}));
    assertThat(polling.checkProbe()).isTrue();
    assertThat(fixture.clock.reads).isEqualTo(1);
  }

  @Test
  void interruptionRemainsLiveAndDoesNotClearTheFlag() {
    var fixture = new Fixture();
    var polling =
        fixture.polling(
            new OrCompositeTermination<>(
                new TimeMillisSpentTermination<>(100), new BasicPlumbingTermination<>(false)));
    assertThat(polling.checkProbe()).isFalse();
    try {
      Thread.currentThread().interrupt();
      assertThat(polling.checkProbe()).isTrue();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(fixture.clock.reads).isEqualTo(1);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void strictDiagnosticPropertyDoesNotChangeRepairAttemptSupport() {
    var property = "greycos.solver.alns.strictTimePolling";
    var previous = System.getProperty(property);
    try {
      System.setProperty(property, "true");
      var fixture = new Fixture();
      var polling = fixture.polling(new TimeMillisSpentTermination<>(100));
      assertThat(polling.supportedForRepairAttempts()).isTrue();
      assertThat(polling.checkProbe()).isFalse();
      fixture.clock.now = 1_200;
      assertThat(polling.checkProbe()).isTrue();
      assertThat(fixture.clock.reads).isEqualTo(2);
    } finally {
      if (previous == null) System.clearProperty(property);
      else System.setProperty(property, previous);
    }
  }

  private static final class Fixture {
    private final CountingClock clock = new CountingClock(1_000);
    private final AtomicLong calculations = new AtomicLong();
    private final SolverScope<TestdataSolution> solverScope = new SolverScope<>(clock);
    private final AlnsPhaseScope<TestdataSolution> phaseScope;

    @SuppressWarnings("unchecked")
    private Fixture() {
      var director =
          (InnerScoreDirector<TestdataSolution, SimpleScore>) mock(InnerScoreDirector.class);
      when(director.getCalculationCount()).thenAnswer(ignored -> calculations.get());
      solverScope.setScoreDirector(director);
      solverScope.startingNow();
      clock.now = 1_100;
      phaseScope = new AlnsPhaseScope<>(solverScope, 0);
      phaseScope.startingNow();
      solverScope.setBestSolutionTimeMillis(1_100L);
      clock.reads = 0;
    }

    private AlnsTerminationPolling<TestdataSolution> polling(
        PhaseTermination<TestdataSolution> termination) {
      return new AlnsTerminationPolling<>(phaseScope, termination);
    }
  }

  private static final class CountingClock extends Clock {
    private long now;
    private int reads;

    private CountingClock(long now) {
      this.now = now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis());
    }

    @Override
    public long millis() {
      reads++;
      return now;
    }
  }
}
