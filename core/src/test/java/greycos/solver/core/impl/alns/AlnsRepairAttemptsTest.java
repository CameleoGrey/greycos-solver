package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(120)
class AlnsRepairAttemptsTest {
  @ParameterizedTest
  @ValueSource(strings = {"phase", "repair"})
  @Timeout(10)
  void customRepairWithoutScoreQueriesStillObservesTimeBudgets(String budget) {
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
            workload,
            "NONE",
            0L,
            3,
            AlnsRepairOperatorType.GREEDY,
            new TerminationConfig().withStepCountLimit(2),
            EnvironmentMode.NO_ASSERT);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    phase.withRepairOperatorConfigList(
        List.of(new AlnsRepairOperatorConfig().withCustomClass(CheckingRepair.class)));
    if (budget.equals("phase"))
      phase.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(20)));
    else phase.withRepairSpentLimit(Duration.ofMillis(20));
    var problem = workload.createProblem(24);
    var original = workload.state(problem);
    var solution = SolverFactory.<BasicSolution>create(config).buildSolver().solve(problem);
    assertThat(workload.state(solution)).isEqualTo(original);
    assertThat(workload.score(solution)).isEqualTo(workload.recompute(solution));
  }

  public static final class CheckingRepair
      implements AlnsRepairOperator<BasicSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<BasicSolution, SimpleScore> context, List<AlnsTarget<BasicSolution>> targets) {
      while (true) context.checkTermination();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void fixedAttemptCountPreservesTrialTraceAcrossWorkerCounts(String shape) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    var expected = run(workload, "NONE", EnvironmentMode.NO_ASSERT, "none", 0);
    assertThat(expected.trace()).hasSize(8);
    for (String workers : List.of("1", "2", "4", "8")) {
      var actual = run(workload, workers, EnvironmentMode.NO_ASSERT, "none", 0);
      assertThat(actual).as("%s %s workers", shape, workers).isEqualTo(expected);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void fullAssertionsReproduceWinnerAndEveryWorkerRollback(String shape) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    assertThat(run(workload, "2", EnvironmentMode.FULL_ASSERT, "none", 0))
        .isEqualTo(run(workload, "NONE", EnvironmentMode.FULL_ASSERT, "none", 0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"repair", "solver", "phase_or", "solver_and"})
  void aggregateLogicalLimitsCutTheSamePrefixInEveryExecutionMode(String budget) {
    var workload = AlnsMoveThreadingWorkload.named("list");
    for (long limit : new long[] {2, 5, 31, 32, 33, 95, 96, 97, 255, 256, 257}) {
      var expected = run(workload, "NONE", EnvironmentMode.NO_ASSERT, budget, limit);
      for (String workers : List.of("1", "2", "4")) {
        assertThat(run(workload, workers, EnvironmentMode.NO_ASSERT, budget, limit))
            .as("%s limit=%d workers=%s", budget, limit, workers)
            .isEqualTo(expected);
      }
    }
  }

  @Test
  void incompatibleModeAndAttemptCountFailBeforeSolving() {
    var workload = AlnsMoveThreadingWorkload.named("basic");
    var config =
        AlnsMoveThreadingWorkload.config(
            workload,
            "2",
            0L,
            3,
            AlnsRepairOperatorType.RANDOMIZED_GREEDY,
            new TerminationConfig().withStepCountLimit(1),
            EnvironmentMode.NO_ASSERT);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    phase.withRepairAttemptCount(4);
    assertThatThrownBy(() -> solveUnchecked(workload, config))
        .hasMessageContaining("repairAttemptCount");
    phase.withMoveThreadingMode(AlnsMoveThreadingMode.REPAIR_ATTEMPTS).withRepairAttemptCount(1);
    assertThatThrownBy(() -> solveUnchecked(workload, config)).hasMessageContaining("at least two");
    phase.withRepairAttemptCount(4);
    phase.getRepairOperatorConfigList().getFirst().withType(AlnsRepairOperatorType.GREEDY);
    assertThatThrownBy(() -> solveUnchecked(workload, config))
        .hasMessageContaining("RANDOMIZED_GREEDY");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void solveUnchecked(
      Workload workload, greycos.solver.core.config.solver.SolverConfig config) {
    SolverFactory.create(config).buildSolver().solve(workload.createProblem(24));
  }

  private static <S> Run run(
      Workload<S> workload, String workers, EnvironmentMode mode, String budget, long limit) {
    var config =
        AlnsMoveThreadingWorkload.config(
            workload,
            workers,
            19L,
            3,
            AlnsRepairOperatorType.RANDOMIZED_GREEDY,
            new TerminationConfig().withStepCountLimit(8),
            mode);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    phase.withMoveThreadingMode(AlnsMoveThreadingMode.REPAIR_ATTEMPTS).withRepairAttemptCount(4);
    // topK=1 still randomizes repair target order and is part of the supported positive range.
    phase.getRepairOperatorConfigList().getFirst().withTopK(1);
    switch (budget) {
      case "none" -> {}
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
                          new TerminationConfig().withStepCountLimit(8))));
      case "solver_and" ->
          config.withTerminationConfig(
              new TerminationConfig()
                  .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
                  .withTerminationConfigList(
                      List.of(
                          new TerminationConfig().withScoreCalculationCountLimit(limit),
                          new TerminationConfig().withMoveCountLimit(2L))));
      default -> throw new IllegalArgumentException(budget);
    }
    var solver = (DefaultSolver<S>) SolverFactory.<S>create(config).buildSolver();
    var trace = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<S> step) {
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
                    + trial.probeCount()
                    + ":"
                    + step.getScoreDirector().getCalculationCount()
                    + ":"
                    + workload.state(step.getWorkingSolution()));
          }
        });
    S result = solver.solve(workload.createProblem(24));
    return new Run(List.copyOf(trace), workload.state(result), workload.recompute(result));
  }

  private record Run(List<String> trace, String state, SimpleScore score) {}
}
