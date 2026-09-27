package greycos.solver.benchmark.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.config.ProblemBenchmarksConfig;
import greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import greycos.solver.benchmark.config.statistic.SingleStatisticType;
import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.StatisticPoint;
import greycos.solver.benchmark.impl.statistic.SubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.bestscore.BestScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.common.LongStatisticPoint;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import io.micrometer.core.instrument.Metrics;

class IslandBenchmarkTest extends AbstractMeterTest {

  @TempDir Path directory;

  @Test
  @Timeout(30)
  void benchmarkIncludesIslandWorkFinalBestAndSeparateStepHistories() {
    var benchmark = benchmark(config());
    benchmark.benchmark();
    var result = result(benchmark);
    assertThat(result.getScoreCalculationCount()).isGreaterThan(1);
    assertThat(result.getMoveEvaluationCount()).isPositive();
    for (var type :
        List.of(
            ProblemStatisticType.SCORE_CALCULATION_SPEED,
            ProblemStatisticType.MOVE_EVALUATION_SPEED)) {
      var points = points(result.getEffectiveSubSingleStatisticMap().get(type));
      assertThat(points)
          .isNotEmpty()
          .anySatisfy(point -> assertThat(((LongStatisticPoint) point).getValue()).isPositive());
    }
    var best =
        points(result.getEffectiveSubSingleStatisticMap().get(ProblemStatisticType.BEST_SCORE));
    assertThat(best).isNotEmpty();
    assertThat(((BestScoreStatisticPoint) best.getLast()).getScore()).isEqualTo(result.getScore());
    for (var type :
        List.of(ProblemStatisticType.STEP_SCORE, ProblemStatisticType.MOVE_COUNT_PER_STEP)) {
      assertThat(points(result.getEffectiveSubSingleStatisticMap().get(type)))
          .extracting(StatisticPoint::getSource)
          .contains("phase-0/island-0", "phase-0/island-1");
    }
    assertThat(
            points(
                result
                    .getEffectiveSubSingleStatisticMap()
                    .get(SingleStatisticType.PICKED_MOVE_TYPE_STEP_SCORE_DIFF)))
        .extracting(StatisticPoint::getSource)
        .contains("phase-0/island-0", "phase-0/island-1");
  }

  @Test
  @Timeout(30)
  void failureUnregistersBenchmarkRegistryAndClearsLoggingContext() {
    var initialRegistries = Set.copyOf(Metrics.globalRegistry.getRegistries());
    var benchmark = benchmark(config().withEasyScoreCalculatorClass(FailingScoreCalculator.class));
    benchmark.benchmarkingStarted();
    var runner = new SubSingleBenchmarkRunner<TestdataSolution>(result(benchmark), false);
    assertThatThrownBy(runner::call).hasMessageContaining("Deliberate benchmark failure.");
    assertThat(Metrics.globalRegistry.getRegistries())
        .containsExactlyInAnyOrderElementsOf(initialRegistries);
    assertThat(MDC.get(SubSingleBenchmarkRunner.NAME_MDC)).isNull();
  }

  private DefaultPlannerBenchmark benchmark(SolverConfig solverConfig) {
    var config =
        PlannerBenchmarkConfig.createFromSolverConfig(solverConfig)
            .withBenchmarkDirectory(directory.toFile())
            .withWarmUpMillisecondsSpentLimit(0L);
    config
        .getSolverBenchmarkConfigList()
        .getFirst()
        .setProblemBenchmarksConfig(
            new ProblemBenchmarksConfig()
                .withProblemStatisticTypes(
                    ProblemStatisticType.BEST_SCORE,
                    ProblemStatisticType.STEP_SCORE,
                    ProblemStatisticType.SCORE_CALCULATION_SPEED,
                    ProblemStatisticType.MOVE_EVALUATION_SPEED,
                    ProblemStatisticType.MOVE_COUNT_PER_STEP,
                    ProblemStatisticType.MOVE_COUNT_PER_TYPE)
                .withSingleStatisticTypes(SingleStatisticType.PICKED_MOVE_TYPE_STEP_SCORE_DIFF));
    var problem = TestdataSolution.generateSolution(3, 6);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    return (DefaultPlannerBenchmark)
        PlannerBenchmarkFactory.create(config).buildPlannerBenchmark(problem);
  }

  private static SolverConfig config() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
        .withPhases(
            new IslandModelPhaseConfig()
                .withIslandCount(2)
                .withPhaseConfigList(
                    List.of(
                        new LocalSearchPhaseConfig()
                            .withTerminationConfig(
                                new TerminationConfig().withStepCountLimit(5)))));
  }

  private static SubSingleBenchmarkResult result(DefaultPlannerBenchmark benchmark) {
    return benchmark
        .getPlannerBenchmarkResult()
        .getSolverBenchmarkResultList()
        .getFirst()
        .getSingleBenchmarkResultList()
        .getFirst()
        .getSubSingleBenchmarkResultList()
        .getFirst();
  }

  @SuppressWarnings("unchecked")
  private static List<StatisticPoint> points(SubSingleStatistic<?, ?> statistic) {
    if (statistic.getPointList() == null) {
      statistic.unhibernatePointList();
    }
    return (List<StatisticPoint>) statistic.getPointList();
  }

  public static class FailingScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      throw new IllegalStateException("Deliberate benchmark failure.");
    }
  }
}
