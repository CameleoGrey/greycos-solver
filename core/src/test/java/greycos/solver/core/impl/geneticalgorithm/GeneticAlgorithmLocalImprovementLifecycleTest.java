package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmLocalImprovementIntegrationTest.phase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmLocalImprovementLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previousPhaseWorkIsIncludedOnlyInTheSolverMoveBudget(boolean phaseLocal) {
    var first = phase(0L, 3);
    var second = phase(20L, 100).withPopulationSize(1);
    var solverConfig = config(first).withPhases(first, second);
    var termination = new TerminationConfig().withMoveCountLimit(5L);
    if (phaseLocal) second.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    var solver = solver(solverConfig);
    var phases = new ArrayList<GeneticAlgorithmPhaseScope<TestdataSolution>>();
    var entry = new AtomicReference<List<String>>();
    var handoff = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            if (scope.getPhaseIndex() == 1) {
              assertThat(assignments(scope.getWorkingSolution()))
                  .isEqualTo(assignments(scope.getSolverScope().getBestSolution()));
              assertThat(scope.getSolverScope().getMoveEvaluationCount()).isEqualTo(3);
              handoff.set(true);
            }
          }

          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            if (step.getPhaseScope().getPhaseIndex() == 1) {
              entry.set(assignments(step.getWorkingSolution()));
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            phases.add((GeneticAlgorithmPhaseScope<TestdataSolution>) scope);
            if (scope.getPhaseIndex() == 1) {
              assertThat(assignments(scope.getWorkingSolution())).isEqualTo(entry.get());
              assertReplay(scope.getWorkingSolution());
            }
          }
        });

    assertReplay(solver.solve(problem(10, 12)));

    assertThat(handoff).isTrue();
    assertThat(phases).hasSize(2);
    assertThat(phases.getFirst().getPhaseMoveEvaluationCount()).isEqualTo(3);
    assertThat(phases.getFirst().getLocalImprovementProbeCount()).isZero();
    assertThat(phases.getLast().getPhaseMoveEvaluationCount()).isEqualTo(phaseLocal ? 5 : 2);
    assertThat(phases.getLast().getLocalImprovementProbeCount()).isPositive();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(phaseLocal ? 8 : 5);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void moveBudgetIncludesCompletedProbesAndExcludesTheInterruptedOffspring(boolean phaseLocal) {
    var phaseConfig = phase(20L, 100).withPopulationSize(1);
    var solverConfig = config(phaseConfig);
    var termination = new TerminationConfig().withMoveCountLimit(5L);
    if (phaseLocal) phaseConfig.setTerminationConfig(termination);
    else solverConfig.setTerminationConfig(termination);
    solverConfig.withMonitoringConfig(
        new MonitoringConfig().withSolverMetricList(List.of(SolverMetric.MOVE_COUNT_PER_TYPE)));
    var solver = solver(solverConfig);
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var restoration = requireInterruptedAttemptRestored(solver);

    var result = solver.solve(problem(10, 12));

    assertThat(restoration).isTrue();
    assertThat(phase.get().getLocalImprovementProbeCount()).isPositive().isLessThanOrEqualTo(5);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
    assertThat(phase.get().getPhaseMoveEvaluationCount()).isEqualTo(5);
    assertThat(completed.size() + phase.get().getLocalImprovementProbeCount()).isEqualTo(5);
    assertThat(completed)
        .allSatisfy(
            step -> assertThat(step.getOutcome()).isEqualTo(GeneticAlgorithmOutcome.NO_CHANGE));
    assertThat(
            solver.getSolverScope().getMoveEvaluationCountPerType().values().stream()
                .mapToLong(Long::longValue)
                .sum())
        .isEqualTo(5);
    assertReplay(result);
  }

  @Test
  void phaseScoreBudgetReachedByAProbeRetainsItsCreditAndCountsRequiredRestoration() {
    var solver =
        solver(
            config(
                phase(20L, 100)
                    .withPopulationSize(1)
                    .withTerminationConfig(
                        new TerminationConfig().withScoreCalculationCountLimit(3L))));
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var restoration = requireInterruptedAttemptRestored(solver);

    var result = solver.solve(problem(10, 12));

    assertThat(restoration).isTrue();
    assertThat(phase.get().getLocalImprovementProbeCount()).isPositive();
    // One initial score, one child score, one probe and one required rollback score.
    assertThat(phase.get().getPhaseScoreCalculationCount()).isEqualTo(4);
    assertThat(solver.getSolverScope().getMoveEvaluationCount())
        .isEqualTo(completed.size() + phase.get().getLocalImprovementProbeCount());
    assertThat(completed)
        .allSatisfy(
            step -> assertThat(step.getOutcome()).isEqualTo(GeneticAlgorithmOutcome.NO_CHANGE));
    assertReplay(result);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationDuringProbeRestoresEntryStateAndPreservesCompletedProbe(boolean interrupt) {
    var solver = controlledSolver();
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var restoration = requireInterruptedAttemptRestored(solver);
    var movesAtCancellation = new AtomicLong();
    var fired =
        armFirstProbe(
            solver,
            solution -> {
              movesAtCancellation.set(solver.getSolverScope().getMoveEvaluationCount());
              if (interrupt) Thread.currentThread().interrupt();
              else assertThat(solver.terminateEarly()).isTrue();
            });

    try {
      var result = solver.solve(problem(10, 12));

      assertThat(fired).isTrue();
      assertThat(restoration).isTrue();
      assertThat(phase.get().getLocalImprovementProbeCount()).isPositive();
      assertThat(solver.getSolverScope().getMoveEvaluationCount())
          .isEqualTo(movesAtCancellation.get() + 1L)
          .isEqualTo(completed.size() + phase.get().getLocalImprovementProbeCount());
      assertThat(solver.isSolving()).isFalse();
      assertReplay(result);
    } finally {
      ControlledScoreCalculator.ON_SCORE.remove();
      if (interrupt) Thread.interrupted();
    }
  }

  @Test
  void scoringFailureDuringProbeKeepsItsIdentityAndDoesNotCreditTheFailedProbe() {
    var solver = controlledSolver();
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var failure = new IllegalStateException("intentional local improvement scoring failure");
    var movesAtFailure = new AtomicLong();
    var fired =
        armFirstProbe(
            solver,
            solution -> {
              movesAtFailure.set(solver.getSolverScope().getMoveEvaluationCount());
              throw failure;
            });

    try {
      assertThatThrownBy(() -> solver.solve(problem(10, 12))).isSameAs(failure);

      assertThat(fired).isTrue();
      assertThat(solver.getSolverScope().getMoveEvaluationCount())
          .isEqualTo(movesAtFailure.get())
          .isEqualTo(completed.size() + phase.get().getLocalImprovementProbeCount());
      assertThat(solver.isSolving()).isFalse();
    } finally {
      ControlledScoreCalculator.ON_SCORE.remove();
    }
  }

  @Test
  void bestPublishedFromTemporaryProbeRemainsValidAfterCancellationAndUndo() {
    var solver = controlledSolver();
    var completed = recordSteps(solver);
    var phase = recordPhase(solver);
    var restoration = requireInterruptedAttemptRestored(solver);
    var probeReached = new AtomicBoolean();
    var cancelled = new AtomicBoolean();
    var published = new ArrayList<TestdataSolution>();
    var snapshots = new ArrayList<List<String>>();
    armFirstProbe(solver, solution -> probeReached.set(true));
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertReplay(best);
          published.add(best);
          snapshots.add(assignments(best));
          if (probeReached.get() && cancelled.compareAndSet(false, true)) {
            assertThat(solver.terminateEarly()).isTrue();
          }
        });

    try {
      var result = solver.solve(problem(10, 12));

      assertThat(cancelled).isTrue();
      assertThat(restoration).isTrue();
      assertThat(phase.get().getLocalImprovementProbeCount()).isPositive();
      assertThat(solver.getSolverScope().getMoveEvaluationCount())
          .isEqualTo(completed.size() + phase.get().getLocalImprovementProbeCount());
      for (var i = 0; i < published.size(); i++) {
        assertThat(assignments(published.get(i))).isEqualTo(snapshots.get(i));
        assertReplay(published.get(i));
      }
      assertThat(result.getScore()).isEqualTo(published.getLast().getScore());
      assertThat(assignments(result)).isEqualTo(snapshots.getLast());
      assertReplay(result);
    } finally {
      ControlledScoreCalculator.ON_SCORE.remove();
    }
  }

  private static DefaultSolver<TestdataSolution> controlledSolver() {
    return solver(
        config(phase(40L, 100).withPopulationSize(1))
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(ControlledScoreCalculator.class)));
  }

  private static AtomicBoolean armFirstProbe(
      DefaultSolver<TestdataSolution> solver, Consumer<TestdataSolution> action) {
    var fired = new AtomicBoolean();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            var calls = new AtomicInteger();
            ControlledScoreCalculator.ON_SCORE.set(
                solution -> {
                  // NO_ASSERT: the child is scored once before the first doable temporary probe.
                  if (calls.incrementAndGet() == 2 && fired.compareAndSet(false, true)) {
                    action.accept(solution);
                  }
                });
          }
        });
    return fired;
  }

  private static AtomicBoolean requireInterruptedAttemptRestored(
      DefaultSolver<TestdataSolution> solver) {
    var restored = new AtomicBoolean();
    var entryAssignments = new AtomicReference<List<String>>();
    var entryScore = new AtomicReference<InnerScore<?>>();
    var lastStarted = new AtomicInteger(-1);
    var lastCompleted = new AtomicInteger(-1);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            lastStarted.set(step.getStepIndex());
            entryAssignments.set(assignments(step.getWorkingSolution()));
            entryScore.set(step.getBeforeScore());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            lastCompleted.set(step.getStepIndex());
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            assertThat(lastStarted.get()).isGreaterThan(lastCompleted.get());
            assertThat(assignments(scope.getWorkingSolution())).isEqualTo(entryAssignments.get());
            assertThat(scope.getWorkingSolution().getScore()).isEqualTo(entryScore.get().raw());
            assertReplay(scope.getWorkingSolution());
            restored.set(true);
          }
        });
    return restored;
  }

  public static final class ControlledScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    static final ThreadLocal<Consumer<TestdataSolution>> ON_SCORE = new ThreadLocal<>();

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var callback = ON_SCORE.get();
      if (callback != null) callback.accept(solution);
      return SimpleScore.of(
          solution.getEntityList().stream()
              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
              .sum());
    }
  }
}
