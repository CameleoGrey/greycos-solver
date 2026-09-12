package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(60)
class AlnsThreadingBudgetTest {
  static Stream<Arguments> calculationBoundaries() {
    return Stream.of(AlnsRepairOperatorType.RANDOMIZED_GREEDY, AlnsRepairOperatorType.REGRET_2)
        .flatMap(
            repair ->
                Stream.of("repair", "solver", "phase_or", "solver_and")
                    .flatMap(
                        scope ->
                            Stream.of(2L, 5L, 6L, 7L, 8L, 9L, 17L, 18L, 25L)
                                .map(limit -> Arguments.of(repair, scope, limit))));
  }

  @ParameterizedTest
  @MethodSource("calculationBoundaries")
  void logicalBudgetsAndFollowingTrialsMatchAcrossWorkerCounts(
      AlnsRepairOperatorType repair, String budgetScope, long limit) {
    var sequential = trace("NONE", repair, budgetScope, limit);
    for (var threads : List.of("1", "2", "4")) {
      assertThat(trace(threads, repair, budgetScope, limit))
          .as("repair=%s budget=%s limit=%s workers=%s", repair, budgetScope, limit, threads)
          .isEqualTo(sequential);
    }
  }

  private static List<String> trace(
      String threads, AlnsRepairOperatorType repair, String budgetScope, long limit) {
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                threads,
                17L,
                3,
                repair,
                new TerminationConfig().withStepCountLimit(5),
                EnvironmentMode.NO_ASSERT)
            .withMoveThreadBufferSize(1);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    switch (budgetScope) {
      case "repair" -> phase.withRepairScoreCalculationLimit(limit);
      case "solver" ->
          config.withTerminationConfig(
              new TerminationConfig().withScoreCalculationCountLimit(limit));
      case "phase_or" ->
          phase.withTerminationConfig(
              new TerminationConfig()
                  .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
                  .withTerminationConfigList(
                      List.of(
                          new TerminationConfig().withScoreCalculationCountLimit(limit),
                          new TerminationConfig().withStepCountLimit(4))));
      case "solver_and" ->
          config.withTerminationConfig(
              new TerminationConfig()
                  .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
                  .withTerminationConfigList(
                      List.of(
                          new TerminationConfig().withScoreCalculationCountLimit(limit),
                          new TerminationConfig().withMoveCountLimit(2L))));
      default -> throw new IllegalArgumentException(budgetScope);
    }
    var trace = new ArrayList<String>();
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            var trial = ((AlnsStepScope<?>) step).getTrialResult();
            assertThat(workload.recompute(step.getWorkingSolution())).isEqualTo(trial.afterScore());
            trace.add(
                trial.trialIndex()
                    + ":"
                    + trial.destroyId()
                    + ":"
                    + trial.repairId()
                    + ":"
                    + trial.outcome()
                    + ":"
                    + trial.beforeScore()
                    + ":"
                    + trial.candidateScore()
                    + ":"
                    + trial.afterScore()
                    + ":"
                    + trial.bestAfterScore()
                    + ":"
                    + trial.probeCount()
                    + ":"
                    + step.getScoreDirector().getCalculationCount()
                    + ":"
                    + workload.state(step.getWorkingSolution()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<BasicSolution> phaseScope) {
            assertThat(workload.recompute(phaseScope.getWorkingSolution()))
                .isEqualTo(phaseScope.getWorkingSolution().getScore());
            trace.add(
                "ended:"
                    + phaseScope.getScoreDirector().getCalculationCount()
                    + ":"
                    + workload.state(phaseScope.getWorkingSolution()));
          }
        });
    var solution = solver.solve(workload.createProblem(60));
    assertThat(workload.recompute(solution)).isEqualTo(solution.getScore());
    trace.add("best:" + solution.getScore() + ":" + workload.state(solution));
    return trace;
  }
}
