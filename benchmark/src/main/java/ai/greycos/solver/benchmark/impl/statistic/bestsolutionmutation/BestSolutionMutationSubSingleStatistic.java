package ai.greycos.solver.benchmark.impl.statistic.bestsolutionmutation;

import java.util.List;

import ai.greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import ai.greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import ai.greycos.solver.benchmark.impl.statistic.ProblemBasedSubSingleStatistic;
import ai.greycos.solver.benchmark.impl.statistic.StatisticPoint;
import ai.greycos.solver.benchmark.impl.statistic.StatisticRegistry;
import ai.greycos.solver.core.config.solver.monitoring.SolverMetric;
import ai.greycos.solver.core.impl.score.definition.ScoreDefinition;
import ai.greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;

import io.micrometer.core.instrument.Tags;

public class BestSolutionMutationSubSingleStatistic<Solution_>
    extends ProblemBasedSubSingleStatistic<Solution_, BestSolutionMutationStatisticPoint> {

  private BestSolutionMutationSubSingleStatistic() {
    // For JAXB.
  }

  public BestSolutionMutationSubSingleStatistic(SubSingleBenchmarkResult subSingleBenchmarkResult) {
    super(subSingleBenchmarkResult, ProblemStatisticType.BEST_SOLUTION_MUTATION);
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  @Override
  public void open(StatisticRegistry<Solution_> registry, Tags runTag) {
    registry.addListener(
        SolverMetric.BEST_SOLUTION_MUTATION,
        timestamp -> {
          var mutationCount =
              SolverMetricUtil.getGaugeValue(registry, SolverMetric.BEST_SOLUTION_MUTATION, runTag);
          if (mutationCount != null) {
            pointList.add(
                new BestSolutionMutationStatisticPoint(timestamp, mutationCount.intValue()));
          }
        });
  }

  // ************************************************************************
  // CSV methods
  // ************************************************************************

  @Override
  protected String getCsvHeader() {
    return StatisticPoint.buildCsvLine("timeMillisSpent", "mutationCount");
  }

  @Override
  protected BestSolutionMutationStatisticPoint createPointFromCsvLine(
      ScoreDefinition<?> scoreDefinition, List<String> csvLine) {
    return new BestSolutionMutationStatisticPoint(
        Long.parseLong(csvLine.get(0)), Integer.parseInt(csvLine.get(1)));
  }
}
