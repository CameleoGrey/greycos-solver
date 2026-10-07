package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmMetricsTest {

  @Test
  void moveTypeCountsReconcileWithCompletedSeedingAndOffspringAttempts() {
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(5)
                        .withTerminationConfig(new TerminationConfig().withMoveCountLimit(24L)))
                .withMonitoringConfig(
                    new MonitoringConfig()
                        .withSolverMetricList(
                            List.of(
                                SolverMetric.MOVE_COUNT_PER_TYPE,
                                SolverMetric.MOVE_COUNT_PER_STEP,
                                SolverMetric.STEP_SCORE,
                                SolverMetric.MOVE_EVALUATION_COUNT,
                                SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF,
                                SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF))));
    var steps = recordSteps(solver);
    var phase = recordPhase(solver);

    assertReplay(solver.solve(problem(4, 8)));

    var expected =
        steps.stream()
            .collect(
                Collectors.groupingBy(
                    GeneticAlgorithmStepScope::getMoveTypeDescription, Collectors.counting()));
    assertThat(solver.getSolverScope().getMoveEvaluationCountPerType()).isEqualTo(expected);
    assertThat(expected.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(24);
    assertThat(phase.get().getPhaseMoveEvaluationCount()).isEqualTo(24);
    assertThat(phase.get().getPopulationSize()).isEqualTo(5);
    assertThat(phase.get().getDistinctPopulationSize()).isBetween(1, 5);
    assertThat(steps)
        .allSatisfy(
            step -> {
              assertThat(step.getOutcome()).isNotNull();
              assertThat(step.getChangedAssignmentCount()).isBetween(0, 8);
              assertThat(step.getBeforeScore()).isNotNull();
              assertThat(step.getBestBeforeScore()).isNotNull();
              assertThat(step.getCandidateScore()).isNotNull();
              if (!step.isSeeding()) {
                assertThat(step.getFirstParentId()).isGreaterThanOrEqualTo(0);
                assertThat(step.getSecondParentId()).isGreaterThanOrEqualTo(0);
                assertThat(step.getNativeId()).isGreaterThanOrEqualTo(0);
              }
            });
  }
}
