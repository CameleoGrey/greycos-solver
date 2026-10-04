package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PartitionTerminationBridgeTest {

  @ParameterizedTest
  @CsvSource({
    "step, false, false",
    "step, true, false",
    "step, false, true",
    "step, true, true",
    "unimproved, false, false",
    "unimproved, true, false",
    "unimproved, false, true",
    "unimproved, true, true",
    "diminished, false, false",
    "diminished, true, false",
    "diminished, false, true",
    "diminished, true, true"
  })
  void inheritedPhaseOnlyPredicatesKeepTheSuppliedPhaseAcrossBridges(
      String kind, boolean solverOrigin, boolean nested) {
    PhaseTermination<TestdataSolution> definition =
        switch (kind) {
          case "step" -> new StepCountTermination<>(2);
          case "unimproved" -> new UnimprovedStepCountTermination<>(2);
          case "diminished" -> new DiminishedReturnsTermination<>(0, 0.1);
          default -> throw new IllegalArgumentException(kind);
        };
    var childScope = scope();
    var child = bind(definition, childScope, solverOrigin, nested);
    var bridge = PhaseTermination.bridge(child);
    child.solvingStarted(childScope);
    for (int index = 0; index < 2; index++) {
      // A completed child phase must not turn a phase-only limit into a sequence limit.
      assertThat(child.isSolverTerminated(childScope)).isFalse();
      var phase = new LocalSearchPhaseScope<>(childScope, index);
      phase.reset();
      phase.startingNow();
      child.phaseStarted(phase);
      assertThat(bridge.isPhaseTerminated(phase)).isFalse();
      assertThat(bridge.calculatePhaseTimeGradient(phase))
          .isEqualTo(kind.equals("diminished") ? -1.0 : 0.0);
      var step = new LocalSearchStepScope<>(phase, 0);
      child.stepStarted(step);
      if (!kind.equals("diminished")) {
        child.stepEnded(step);
        phase.setLastCompletedStepScope(step);
        assertThat(bridge.isPhaseTerminated(phase)).isFalse();
        assertThat(bridge.calculatePhaseTimeGradient(phase)).isEqualTo(0.5);
        step = new LocalSearchStepScope<>(phase, 1);
        child.stepStarted(step);
        child.stepEnded(step);
        phase.setLastCompletedStepScope(step);
      }
      assertThat(child.isPhaseTerminated(phase)).isTrue();
      assertThat(bridge.isPhaseTerminated(phase)).isTrue();
      assertThat(child.isSolverTerminated(childScope)).isFalse();
      assertThat(bridge.calculatePhaseTimeGradient(phase))
          .isEqualTo(kind.equals("diminished") ? -1.0 : 1.0);
      child.phaseEnded(phase);
      assertThat(child.isSolverTerminated(childScope)).isFalse();
    }
    child.solvingEnded(childScope);
  }

  @ParameterizedTest
  @CsvSource({"moves, false", "moves, true", "scores, false", "scores, true"})
  void bridgeRetainsSolverOrPhaseWorkCountOriginsAcrossChildPhases(
      String kind, boolean solverOrigin) {
    var childScope = scope();
    var count = new AtomicLong(7);
    when(childScope.getScoreDirector().getCalculationCount()).thenAnswer(ignored -> count.get());
    childScope.addMoveEvaluationCount(7);
    PhaseTermination<TestdataSolution> definition =
        kind.equals("moves")
            ? new MoveCountTermination<>(10)
            : new ScoreCalculationCountTermination<>(10);
    var child = bind(definition, childScope, solverOrigin, true);
    var bridge = PhaseTermination.bridge(child);
    child.solvingStarted(childScope);
    var first = new LocalSearchPhaseScope<>(childScope, 0);
    first.startingNow();
    child.phaseStarted(first);
    assertThat(bridge.isPhaseTerminated(first)).isFalse();
    assertThat(bridge.calculatePhaseTimeGradient(first)).isEqualTo(solverOrigin ? 0.7 : 0.0);
    childScope.addMoveEvaluationCount(3);
    count.addAndGet(3);
    assertThat(bridge.isPhaseTerminated(first)).isEqualTo(solverOrigin);
    assertThat(bridge.calculatePhaseTimeGradient(first)).isEqualTo(solverOrigin ? 1.0 : 0.3);
    childScope.addMoveEvaluationCount(7);
    count.addAndGet(7);
    assertThat(bridge.isPhaseTerminated(first)).isTrue();
    child.phaseEnded(first);
    var second = new LocalSearchPhaseScope<>(childScope, 1);
    second.startingNow();
    child.phaseStarted(second);
    assertThat(bridge.isPhaseTerminated(second)).isEqualTo(solverOrigin);
    assertThat(bridge.calculatePhaseTimeGradient(second)).isEqualTo(solverOrigin ? 1.0 : 0.0);
    child.phaseEnded(second);
    child.solvingEnded(childScope);
  }

  @ParameterizedTest
  @CsvSource({"step, false", "step, true", "diminished, false", "diminished, true"})
  void alnsPollingMatchesTheBridgeWithNestedAndDirectPartitionAdapters(
      String kind, boolean nested) {
    var childScope = scope();
    PhaseTermination<TestdataSolution> definition =
        kind.equals("step")
            ? new StepCountTermination<>(1)
            : new DiminishedReturnsTermination<>(0, 0.1);
    var child = bind(definition, childScope, true, nested);
    var bridge = PhaseTermination.bridge(child);
    var phase = new AlnsPhaseScope<>(childScope, 0);
    phase.reset();
    phase.startingNow();
    child.solvingStarted(childScope);
    child.phaseStarted(phase);
    var polling = new AlnsTerminationPolling<>(phase, bridge);
    assertThat(polling.supportedForRepairAttempts()).isEqualTo(kind.equals("step"));
    assertThat(bridge.isPhaseTerminated(phase)).isFalse();
    assertThat(polling.checkProbe()).isFalse();
    var step = new AlnsStepScope<>(phase, 0);
    child.stepStarted(step);
    child.stepEnded(step);
    phase.setLastCompletedStepScope(step);
    assertThat(bridge.isPhaseTerminated(phase)).isTrue();
    assertThat(polling.checkProbe()).isTrue();
    assertThat(polling.checkNow()).isTrue();
    assertThat(child.isSolverTerminated(childScope)).isFalse();
    child.phaseEnded(phase);
    child.solvingEnded(childScope);
  }

  private static UniversalTermination<TestdataSolution> bind(
      PhaseTermination<TestdataSolution> definition,
      SolverScope<TestdataSolution> childScope,
      boolean solverOrigin,
      boolean nested) {
    var parentScope = scope();
    var parentPhase = new PartitionedSearchPhaseScope<>(parentScope, 0);
    parentPhase.startingNow();
    var budget =
        new PartitionTerminationBudget<>(
            solverOrigin
                ? PhaseTermination.bridge(UniversalTermination.and(definition))
                : definition,
            parentPhase);
    var child = budget.createChildTermination(childScope);
    if (nested) {
      // Compile a solver-origin partition leaf inside a second partition's AND expression.
      var nestedBudget =
          new PartitionTerminationBudget<>(
              PhaseTermination.bridge(UniversalTermination.and(child)), parentPhase);
      child = nestedBudget.createChildTermination(childScope);
    }
    return child;
  }

  @SuppressWarnings("unchecked")
  private static SolverScope<TestdataSolution> scope() {
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(mock(InnerScoreDirector.class));
    scope.setInitializedBestScore(SimpleScore.ZERO);
    scope.startingNow();
    return scope;
  }
}
