package greycos.solver.benchmark.impl.statistic.stepscore;

import java.util.List;

import greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.ProblemBasedSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.StatisticPoint;
import greycos.solver.benchmark.impl.statistic.StatisticRegistry;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.definition.ScoreDefinition;

import io.micrometer.core.instrument.Tags;

public class StepScoreSubSingleStatistic<Solution_>
    extends ProblemBasedSubSingleStatistic<Solution_, StepScoreStatisticPoint> {

  private StepScoreSubSingleStatistic() {
    // For JAXB.
  }

  public StepScoreSubSingleStatistic(SubSingleBenchmarkResult subSingleBenchmarkResult) {
    super(subSingleBenchmarkResult, ProblemStatisticType.STEP_SCORE);
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  @Override
  public void open(StatisticRegistry<Solution_> registry, Tags runTag) {
    registry.addListener(
        SolverMetric.STEP_SCORE,
        timeMillisSpent ->
            registry.extractScoreFromMeters(
                SolverMetric.STEP_SCORE,
                runTag,
                score ->
                    pointList.add(
                        new StepScoreStatisticPoint(
                            timeMillisSpent, score.raw(), score.isFullyAssigned()))));
  }

  // ************************************************************************
  // CSV methods
  // ************************************************************************

  @Override
  protected String getCsvHeader() {
    return StatisticPoint.buildCsvLine("timeMillisSpent", "score", "initialized");
  }

  @Override
  protected StepScoreStatisticPoint createPointFromCsvLine(
      ScoreDefinition<?> scoreDefinition, List<String> csvLine) {
    return new StepScoreStatisticPoint(
        Long.parseLong(csvLine.get(0)),
        scoreDefinition.parseScore(csvLine.get(1)),
        Boolean.parseBoolean(csvLine.get(2)));
  }
}
