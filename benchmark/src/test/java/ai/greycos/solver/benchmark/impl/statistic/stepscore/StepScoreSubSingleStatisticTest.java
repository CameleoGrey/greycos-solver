package ai.greycos.solver.benchmark.impl.statistic.stepscore;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import ai.greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import ai.greycos.solver.benchmark.impl.statistic.AbstractSubSingleStatisticTest;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testdomain.TestdataSolution;

import org.assertj.core.api.SoftAssertions;

public final class StepScoreSubSingleStatisticTest
    extends AbstractSubSingleStatisticTest<
        StepScoreStatisticPoint, StepScoreSubSingleStatistic<TestdataSolution>> {

  @Override
  protected Function<SubSingleBenchmarkResult, StepScoreSubSingleStatistic<TestdataSolution>>
      getSubSingleStatisticConstructor() {
    return StepScoreSubSingleStatistic::new;
  }

  @Override
  protected List<StepScoreStatisticPoint> getInputPoints() {
    return Collections.singletonList(
        new StepScoreStatisticPoint(Long.MAX_VALUE, SimpleScore.of(Integer.MAX_VALUE), true));
  }

  @Override
  protected void runTest(SoftAssertions assertions, List<StepScoreStatisticPoint> outputPoints) {
    assertions
        .assertThat(outputPoints)
        .hasSize(1)
        .first()
        .matches(
            s -> s.getScore().equals(SimpleScore.of(Integer.MAX_VALUE)), "Scores do not match.")
        .matches(s -> s.getTimeMillisSpent() == Long.MAX_VALUE, "Millis do not match.");
  }
}
