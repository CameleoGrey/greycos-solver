package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.withPrecision;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Clock;
import java.time.Duration;

import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.MockClock;

import org.junit.jupiter.api.Test;

class UnimprovedTimeMillisSpentTerminationTest {

  @Test
  void forNegativeUnimprovedTimeMillis_exceptionIsThrown() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UnimprovedTimeMillisSpentTermination<>(-1L))
        .withMessageContaining("cannot be negative");
  }

  @Test
  void solverTermination() {
    SolverScope<TestdataSolution> solverScope = spy(new SolverScope<>());
    AbstractPhaseScope<TestdataSolution> phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    Clock clock = mock(Clock.class);

    UniversalTermination<TestdataSolution> termination =
        new UnimprovedTimeMillisSpentTermination<>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(phaseScope);
    termination.stepStarted(mock(AbstractStepScope.class));

    doReturn(1000L).when(clock).millis();
    doReturn(500L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(0.5, withPrecision(0.0));

    doReturn(2000L).when(clock).millis();
    doReturn(1000L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(1.0, withPrecision(0.0));
  }

  @Test
  void phaseTermination() {
    var solverScope = new SolverScope<TestdataSolution>();
    var phaseScope = spy(new LocalSearchPhaseScope<TestdataSolution>(solverScope, 0));
    var stepScope = mock(AbstractStepScope.class);

    var clock = mock(Clock.class);

    var termination = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(phaseScope);
    termination.stepStarted(stepScope);

    doReturn(1000L).when(clock).millis();
    doReturn(500L).when(phaseScope).getPhaseBestSolutionTimeMillis();
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope))
        .isEqualTo(0.5, withPrecision(0.0));

    doReturn(2000L).when(clock).millis();
    doReturn(1000L).when(phaseScope).getPhaseBestSolutionTimeMillis();
    assertThat(termination.isPhaseTerminated(phaseScope)).isTrue();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope))
        .isEqualTo(1.0, withPrecision(0.0));
  }

  @Test
  void solverTerminationWithConstructionHeuristic() { // CH ignores unimproved time spent
    // termination.
    SolverScope<TestdataSolution> solverScope = spy(new SolverScope<>());
    Clock clock = mock(Clock.class);

    UniversalTermination<TestdataSolution> termination =
        new UnimprovedTimeMillisSpentTermination<>(1000L, clock);
    termination.solvingStarted(solverScope);

    AbstractPhaseScope<TestdataSolution> chPhaseScope =
        new ConstructionHeuristicPhaseScope<>(solverScope, 0);
    termination.phaseStarted(chPhaseScope);

    // During the construction heuristic, the unimproved termination should not trigger.
    doReturn(1000L).when(clock).millis();
    doReturn(0L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(0.0, withPrecision(0.0));

    doReturn(2000L).when(clock).millis();
    doReturn(0L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(0.0, withPrecision(0.0));

    termination.phaseEnded(chPhaseScope);

    AbstractPhaseScope<TestdataSolution> lsPhaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    termination.phaseStarted(lsPhaseScope);
    termination.stepStarted(mock(AbstractStepScope.class));

    // When local search starts, the unimproved termination should start triggering,
    // but the start time should act as if reset to zero.
    doReturn(3000L).when(clock).millis();
    doReturn(2500L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(0.5, withPrecision(0.0));

    doReturn(4000L).when(clock).millis();
    doReturn(3000L).when(solverScope).getBestSolutionTimeMillis();
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
    assertThat(termination.calculateSolverTimeGradient(solverScope))
        .isEqualTo(1.0, withPrecision(0.0));

    termination.phaseEnded(lsPhaseScope);
    termination.solvingEnded(solverScope);
  }

  @Test
  void
      phaseTerminationWithConstructionHeuristic() { // CH ignores unimproved time spent termination.
    SolverScope<TestdataSolution> solverScope = new SolverScope<>();
    AbstractPhaseScope<TestdataSolution> phaseScope =
        spy(new ConstructionHeuristicPhaseScope<>(solverScope, 0));
    Clock clock = mock(Clock.class);

    UniversalTermination<TestdataSolution> termination =
        new UnimprovedTimeMillisSpentTermination<>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(phaseScope);

    doReturn(1000L).when(clock).millis();
    doReturn(500L).when(phaseScope).getPhaseBestSolutionTimeMillis();
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope))
        .isEqualTo(0.0, withPrecision(0.0));

    doReturn(2000L).when(clock).millis();
    doReturn(1000L).when(phaseScope).getPhaseBestSolutionTimeMillis();
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(phaseScope))
        .isEqualTo(0.0, withPrecision(0.0));
  }

  @Test
  void phaseTerminationAndLongInitializationPeriod() {
    // The goal is to verify termination after a long initialization,
    // ensuring the termination does not occur until all necessary resources,
    // such as distance matrices, are fully loaded.
    var solverScope = new SolverScope<TestdataSolution>();
    var phaseScope = spy(new LocalSearchPhaseScope<>(solverScope, 0));
    var stepScope = mock(AbstractStepScope.class);

    var clock = new MockClock(Clock.systemUTC());

    var termination = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(phaseScope);

    // The termination is not started yet
    clock.tick(Duration.ofMillis(10000L));
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();

    // The counter is started
    termination.stepStarted(stepScope);
    clock.tick(Duration.ofMillis(500L));
    doReturn(clock.millis()).when(phaseScope).getPhaseBestSolutionTimeMillis();
    assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();

    // Timeout
    clock.tick(Duration.ofMillis(1100L));
    assertThat(termination.isPhaseTerminated(phaseScope)).isTrue();
  }

  @Test
  void repeatedSolvesAndRestartsDoNotSpendTheirIdleBudgetOnInitialization() {
    var clock = new MockClock(Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
    SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
    var phaseScope = spy(new LocalSearchPhaseScope<>(solverScope, 0));
    var termination = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(1000L, clock);
    for (int run = 0; run < 3; run++) {
      doReturn(clock.millis()).when(solverScope).getBestSolutionTimeMillis();
      doReturn(clock.millis()).when(phaseScope).getPhaseBestSolutionTimeMillis();
      termination.solvingStarted(solverScope);
      termination.phaseStarted(phaseScope);
      clock.tick(Duration.ofSeconds(10));
      assertThat(termination.isSolverTerminated(solverScope)).isFalse();
      assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
      assertThat(termination.calculateSolverTimeGradient(solverScope)).isZero();
      assertThat(termination.calculatePhaseTimeGradient(phaseScope)).isZero();
      termination.stepStarted(mock(AbstractStepScope.class));
      clock.tick(Duration.ofMillis(999));
      assertThat(termination.isSolverTerminated(solverScope)).isFalse();
      assertThat(termination.isPhaseTerminated(phaseScope)).isFalse();
      clock.tick(Duration.ofMillis(1));
      assertThat(termination.isSolverTerminated(solverScope)).isTrue();
      assertThat(termination.isPhaseTerminated(phaseScope)).isTrue();
      termination.phaseEnded(phaseScope);
      termination.solvingEnded(solverScope);
    }
  }

  @Test
  void successiveImprovementPhasesPreserveOnlyGlobalIdleHistory() {
    var clock = new MockClock(Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
    SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
    doReturn(0L).when(solverScope).getBestSolutionTimeMillis();
    var first = spy(new LocalSearchPhaseScope<>(solverScope, 0));
    var second = spy(new LocalSearchPhaseScope<>(solverScope, 1));
    doReturn(0L).when(first).getPhaseBestSolutionTimeMillis();
    var termination = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(first);
    termination.stepStarted(mock(AbstractStepScope.class));
    clock.tick(Duration.ofMillis(700));
    termination.phaseEnded(first);
    doReturn(clock.millis()).when(second).getPhaseBestSolutionTimeMillis();
    termination.phaseStarted(second);
    clock.tick(Duration.ofMillis(100));
    assertThat(termination.calculateSolverTimeGradient(solverScope)).isEqualTo(0.8);
    assertThat(termination.calculatePhaseTimeGradient(second)).isZero();
    termination.stepStarted(mock(AbstractStepScope.class));
    clock.tick(Duration.ofMillis(200));
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
    assertThat(termination.isPhaseTerminated(second)).isFalse();
    assertThat(termination.calculatePhaseTimeGradient(second)).isEqualTo(0.2);
  }

  @Test
  void constructionStepsDoNotStartTheNextImprovementIdleClock() {
    var clock = new MockClock(Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
    SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
    doReturn(0L).when(solverScope).getBestSolutionTimeMillis();
    var search = spy(new LocalSearchPhaseScope<>(solverScope, 0));
    doReturn(0L).when(search).getPhaseBestSolutionTimeMillis();
    var termination = new UnimprovedTimeMillisSpentTermination<TestdataSolution>(1000L, clock);
    termination.solvingStarted(solverScope);
    termination.phaseStarted(search);
    termination.stepStarted(mock(AbstractStepScope.class));
    clock.tick(Duration.ofMillis(900));
    termination.phaseEnded(search);
    var construction = new ConstructionHeuristicPhaseScope<>(solverScope, 1);
    termination.phaseStarted(construction);
    termination.stepStarted(mock(AbstractStepScope.class));
    clock.tick(Duration.ofSeconds(10));
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    termination.phaseEnded(construction);
    termination.phaseStarted(search);
    clock.tick(Duration.ofSeconds(10));
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    termination.stepStarted(mock(AbstractStepScope.class));
    clock.tick(Duration.ofMillis(999));
    assertThat(termination.isSolverTerminated(solverScope)).isFalse();
    clock.tick(Duration.ofMillis(1));
    assertThat(termination.isSolverTerminated(solverScope)).isTrue();
  }
}
