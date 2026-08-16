package greycos.solver.benchmark.impl.statistic.scorecalculationspeed;

import greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import greycos.solver.benchmark.impl.result.ProblemBenchmarkResult;
import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.SubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.common.AbstractTimeLineChartProblemStatistic;

public class ScoreCalculationSpeedProblemStatistic extends AbstractTimeLineChartProblemStatistic {

  protected ScoreCalculationSpeedProblemStatistic() {
    super(ProblemStatisticType.SCORE_CALCULATION_SPEED);
  }

  public ScoreCalculationSpeedProblemStatistic(ProblemBenchmarkResult problemBenchmarkResult) {
    super(
        ProblemStatisticType.SCORE_CALCULATION_SPEED,
        problemBenchmarkResult,
        "scoreCalculationSpeedProblemStatisticChart",
        problemBenchmarkResult.getName() + " score calculation speed statistic",
        "Score calculation speed per second");
  }

  @Override
  public SubSingleStatistic createSubSingleStatistic(
      SubSingleBenchmarkResult subSingleBenchmarkResult) {
    return new ScoreCalculationSpeedSubSingleStatistic(subSingleBenchmarkResult);
  }

  @Override
  public void setProblemBenchmarkResult(ProblemBenchmarkResult problemBenchmarkResult) {
    super.setProblemBenchmarkResult(problemBenchmarkResult);
    this.setyLabel("Score calculation speed per second");
    this.setReportTitle(problemBenchmarkResult.getName() + " score calculation speed statistic");
    this.setReportFileName("scoreCalculationSpeedProblemStatisticChart");
  }
}
