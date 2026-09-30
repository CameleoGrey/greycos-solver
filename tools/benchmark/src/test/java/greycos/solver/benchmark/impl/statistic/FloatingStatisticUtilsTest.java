package greycos.solver.benchmark.impl.statistic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import greycos.solver.benchmark.impl.result.BenchmarkResult;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;

import org.junit.jupiter.api.Test;

class FloatingStatisticUtilsTest {

  @Test
  void finiteDeviationDoesNotOverflowDuringSubtractionOrSquaring() {
    var extremes =
        List.of(
            success(SimpleDoubleScore.of(-Double.MAX_VALUE)),
            success(SimpleDoubleScore.of(Double.MAX_VALUE)));
    assertThat(
            StatisticUtils.determineStandardDeviationDoubles(extremes, SimpleDoubleScore.ZERO, 2))
        .containsExactly(Double.MAX_VALUE);
    var floatExtremes =
        List.of(
            success(SimpleFloatScore.of(-Float.MAX_VALUE)),
            success(SimpleFloatScore.of(Float.MAX_VALUE)));
    assertThat(
            StatisticUtils.determineStandardDeviationDoubles(
                floatExtremes, SimpleFloatScore.ZERO, 2))
        .containsExactly((double) Float.MAX_VALUE);
  }

  @Test
  void repeatedMaximumScoresHaveZeroDeviation() {
    var maximum = SimpleDoubleScore.of(Double.MAX_VALUE);
    assertThat(
            StatisticUtils.determineStandardDeviationDoubles(
                List.of(success(maximum), success(maximum)), maximum, 2))
        .containsExactly(0.0);
  }

  @Test
  void squaringSubnormalsDoesNotUnderflow() {
    assertThat(
            StatisticUtils.determineStandardDeviationDoubles(
                List.of(
                    success(SimpleDoubleScore.of(-Double.MIN_VALUE)),
                    success(SimpleDoubleScore.of(Double.MIN_VALUE))),
                SimpleDoubleScore.ZERO,
                2))
        .containsExactly(Double.MIN_VALUE);
    assertThat(
            StatisticUtils.determineStandardDeviationDoubles(
                List.of(
                    success(SimpleFloatScore.of(-Float.MIN_VALUE)),
                    success(SimpleFloatScore.of(Float.MIN_VALUE))),
                SimpleFloatScore.ZERO,
                2))
        .containsExactly((double) Float.MIN_VALUE);
  }

  private static BenchmarkResult success(Score<?> score) {
    var result = mock(BenchmarkResult.class);
    when(result.hasAllSuccess()).thenReturn(true);
    when(result.getAverageScore()).thenReturn(score);
    return result;
  }
}
