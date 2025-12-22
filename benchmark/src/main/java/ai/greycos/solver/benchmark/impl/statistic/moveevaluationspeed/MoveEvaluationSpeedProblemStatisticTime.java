package ai.greycos.solver.benchmark.impl.statistic.moveevaluationspeed;

import ai.greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import ai.greycos.solver.benchmark.impl.result.ProblemBenchmarkResult;
import ai.greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import ai.greycos.solver.benchmark.impl.statistic.SubSingleStatistic;
import ai.greycos.solver.benchmark.impl.statistic.common.AbstractTimeLineChartProblemStatistic;

public class MoveEvaluationSpeedProblemStatisticTime extends AbstractTimeLineChartProblemStatistic {

  protected MoveEvaluationSpeedProblemStatisticTime() {
    super(ProblemStatisticType.MOVE_EVALUATION_SPEED);
  }

  public MoveEvaluationSpeedProblemStatisticTime(ProblemBenchmarkResult<?> problemBenchmarkResult) {
    super(
        ProblemStatisticType.MOVE_EVALUATION_SPEED,
        problemBenchmarkResult,
        "moveEvaluationSpeedProblemStatisticChart",
        problemBenchmarkResult.getName() + " move evaluation speed statistic",
        "Move evaluation speed per second");
  }

  @Override
  public SubSingleStatistic<?, ?> createSubSingleStatistic(
      SubSingleBenchmarkResult subSingleBenchmarkResult) {
    return new MoveEvaluationSpeedSubSingleStatistic<>(subSingleBenchmarkResult);
  }

  @Override
  public void setProblemBenchmarkResult(ProblemBenchmarkResult problemBenchmarkResult) {
    super.setProblemBenchmarkResult(problemBenchmarkResult);
    this.setyLabel("Move evaluation speed per second");
    this.setReportTitle(problemBenchmarkResult.getName() + " move evaluation speed statistic");
    this.setReportFileName("moveEvaluationSpeedProblemStatisticChart");
  }
}
