package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmOutcome;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmPhaseScope;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmStepScope;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class GAIslandWorkAccountingTest {

  @Test
  void provisionalOuterCreditCannotLatchQuotaWhenCompletionFails() {
    var fixture = new Fixture(new TerminationConfig().withMoveCountLimit(5L));
    fixture.scope.addMoveEvaluationCount(4L);
    var phase = fixture.startPhase();
    var step = fixture.startStep(phase);
    fixture.calculations.set(3L);
    fixture.scope.addMoveEvaluationCount(1L);

    fixture.termination.stepEnded(step);
    assertThat(fixture.scope.getMoveEvaluationCount()).isEqualTo(5L);
    assertThat(fixture.termination.isPhaseTerminated(phase)).isFalse();
    assertThat(fixture.termination.workProgress().completedSteps()).isZero();
    assertThat(fixture.termination.workProgress().moveEvaluationCount()).isEqualTo(4L);
    assertThat(fixture.termination.workProgress().scoreCalculationCount()).isEqualTo(3L);

    // Ordinary callback failure removes only the provisional outer credit.
    fixture.scope.addMoveEvaluationCount(-1L);
    fixture.termination.phaseEnded(phase);
    fixture.termination.solvingEnded(fixture.scope);
    assertThat(fixture.termination.isSolverTerminated(fixture.scope)).isFalse();
    assertThat(fixture.termination.workProgress().moveEvaluationCount()).isEqualTo(4L);
    assertThat(phase.getNextStepIndex()).isZero();
  }

  @Test
  void completedProbesAndPhysicalScoringSurviveAnUncommittedOuterAttempt() {
    var fixture = new Fixture(new TerminationConfig().withMoveCountLimit(5L));
    fixture.scope.addMoveEvaluationCount(2L);
    var phase = fixture.startPhase();
    var step = fixture.startStep(phase);
    for (int i = 0; i < 2; i++) {
      fixture.scope.addMoveEvaluationCount(1L);
      step.recordLocalImprovementProbe();
    }
    fixture.calculations.set(8L);
    fixture.scope.addMoveEvaluationCount(1L);
    fixture.termination.stepEnded(step);
    assertThat(fixture.termination.isPhaseTerminated(phase)).isFalse();

    fixture.scope.addMoveEvaluationCount(-1L);
    fixture.termination.phaseEnded(phase);
    assertThat(fixture.termination.workProgress())
        .isEqualTo(new IslandWorkQuota.Snapshot(0L, 4L, 8L));
    assertThat(phase.getCommittedMoveEvaluationCount()).isEqualTo(4L);
    assertThat(phase.getLocalImprovementProbeCount()).isEqualTo(2L);
  }

  @Test
  void commitAndRepeatedLifecycleCallsPublishEachCompletedStepOnce() {
    var fixture = new Fixture(new TerminationConfig().withStepCountLimit(2));
    var phase = fixture.startPhase();
    fixture.termination.phaseStarted(phase);
    var step = fixture.startStep(phase);
    fixture.scope.addMoveEvaluationCount(1L);
    fixture.termination.stepEnded(step);
    fixture.termination.stepEnded(step);
    assertThat(fixture.termination.workProgress().completedSteps()).isZero();

    phase.commitStep(step);
    phase.commitStep(step);
    fixture.termination.stepEnded(step);
    assertThat(fixture.termination.workProgress())
        .isEqualTo(new IslandWorkQuota.Snapshot(1L, 1L, 0L));
    assertThat(fixture.termination.isPhaseTerminated(phase)).isFalse();

    var second = fixture.startStep(phase);
    fixture.scope.addMoveEvaluationCount(1L);
    fixture.termination.stepEnded(second);
    phase.commitStep(second);
    assertThat(fixture.termination.isPhaseTerminated(phase)).isTrue();
    assertThat(fixture.termination.workProgress().completedSteps()).isEqualTo(2L);
  }

  @Test
  void commitListenerFailureRetainsWorkAndNotifiesRemainingAccountingListeners() {
    var fixture = new Fixture(new TerminationConfig().withMoveCountLimit(1L));
    var phase = fixture.newPhase();
    var failure = new IllegalStateException("Committed listener failed.");
    phase.addCommittedStepListener(
        step -> {
          throw failure;
        });
    fixture.termination.phaseStarted(phase);
    var secondary = new IllegalArgumentException("Later committed listener failed.");
    phase.addCommittedStepListener(
        step -> {
          throw secondary;
        });
    var step = fixture.startStep(phase);
    fixture.scope.addMoveEvaluationCount(1L);
    fixture.termination.stepEnded(step);

    assertThatThrownBy(() -> phase.commitStep(step))
        .isSameAs(failure)
        .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(secondary));
    assertThat(phase.getLastCompletedStepScope()).isSameAs(step);
    assertThat(phase.getCommittedMoveEvaluationCount()).isEqualTo(1L);
    assertThat(fixture.termination.workProgress())
        .isEqualTo(new IslandWorkQuota.Snapshot(1L, 1L, 0L));
    assertThat(fixture.termination.isPhaseTerminated(phase)).isTrue();
  }

  @Test
  void committedWorkRemainsCumulativeAcrossGaAndLaterSearchPhases() {
    var fixture = new Fixture(new TerminationConfig().withMoveCountLimit(6L));
    fixture.scope.addMoveEvaluationCount(3L);
    var phase = fixture.startPhase();
    var step = fixture.startStep(phase);
    fixture.scope.addMoveEvaluationCount(1L);
    step.recordLocalImprovementProbe();
    fixture.scope.addMoveEvaluationCount(1L);
    fixture.termination.stepEnded(step);
    phase.commitStep(step);
    fixture.termination.phaseEnded(phase);
    assertThat(fixture.termination.workProgress().moveEvaluationCount()).isEqualTo(5L);

    var nextPhase = new LocalSearchPhaseScope<>(fixture.scope, 1);
    nextPhase.startingNow();
    fixture.termination.phaseStarted(nextPhase);
    fixture.scope.addMoveEvaluationCount(1L);
    assertThat(fixture.termination.isPhaseTerminated(nextPhase)).isTrue();
    assertThat(fixture.termination.workProgress().moveEvaluationCount()).isEqualTo(6L);
  }

  @Test
  void geneticSearchStartsTheSharedIdleAndScoreDifferenceThresholdClocks() {
    for (var config :
        new TerminationConfig[] {
          new TerminationConfig().withUnimprovedSpentLimit(Duration.ofMillis(100L)),
          new TerminationConfig()
              .withUnimprovedSpentLimit(Duration.ofMillis(100L))
              .withUnimprovedScoreDifferenceThreshold("1")
        }) {
      var fixture = new Fixture(config);
      fixture.clock.now = 2_000L;
      assertThat(fixture.termination.isSolverTerminated(fixture.scope)).isFalse();
      var phase = fixture.startPhase();
      fixture.clock.now = 2_099L;
      assertThat(fixture.termination.isPhaseTerminated(phase)).isFalse();
      fixture.clock.now = 2_100L;
      // The score-difference threshold keeps its established strict deadline comparison.
      assertThat(fixture.termination.isPhaseTerminated(phase))
          .isEqualTo(config.getUnimprovedScoreDifferenceThreshold() == null);
      fixture.clock.now = 2_101L;
      assertThat(fixture.termination.isPhaseTerminated(phase)).isTrue();
    }
  }

  private static final class Fixture {
    private final MutableClock clock = new MutableClock();
    private final SolverScope<TestdataSolution> scope = new SolverScope<>(clock);
    private final AtomicLong calculations = new AtomicLong();
    private final IslandSequenceTermination<TestdataSolution> termination;

    @SuppressWarnings("unchecked")
    private Fixture(TerminationConfig config) {
      var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
      when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
      var budget = new IslandTerminationBudget<>(config, policy, clock, clock.now);
      budget.bestScoreImproved(InnerScore.fullyAssigned(SimpleScore.ZERO), clock.now, 0L);
      var director =
          (InnerScoreDirector<TestdataSolution, SimpleScore>) mock(InnerScoreDirector.class);
      when(director.getCalculationCount()).thenAnswer(ignored -> calculations.get());
      scope.setScoreDirector(director);
      scope.setInitializedBestScore(SimpleScore.ZERO);
      scope.setBestSolutionTimeMillis(clock.now);
      termination = budget.createIslandTermination(scope);
      termination.solvingStarted(scope);
    }

    private GeneticAlgorithmPhaseScope<TestdataSolution> newPhase() {
      var phase = new GeneticAlgorithmPhaseScope<>(scope, 0);
      phase.startingNow();
      return phase;
    }

    private GeneticAlgorithmPhaseScope<TestdataSolution> startPhase() {
      var phase = newPhase();
      termination.phaseStarted(phase);
      return phase;
    }

    private GeneticAlgorithmStepScope<TestdataSolution> startStep(
        GeneticAlgorithmPhaseScope<TestdataSolution> phase) {
      var step = new GeneticAlgorithmStepScope<>(phase);
      step.setOutcome(GeneticAlgorithmOutcome.EVALUATED);
      step.setScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      termination.stepStarted(step);
      return step;
    }
  }

  private static final class MutableClock extends Clock {
    private long now = 1_000L;

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
      return Instant.ofEpochMilli(now);
    }

    @Override
    public long millis() {
      return now;
    }
  }
}
