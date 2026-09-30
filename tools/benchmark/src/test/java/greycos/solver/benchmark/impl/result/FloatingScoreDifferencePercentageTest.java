package greycos.solver.benchmark.impl.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;

import org.junit.jupiter.api.Test;

class FloatingScoreDifferencePercentageTest {

  @Test
  void rangeSpanningDifferenceHasAFiniteRelativePercentage() {
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.of(-Double.MAX_VALUE), SimpleDoubleScore.of(Double.MAX_VALUE))
                .percentageLevels())
        .containsExactly(2.0);
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.of(Double.MAX_VALUE), SimpleDoubleScore.of(-Double.MAX_VALUE))
                .percentageLevels())
        .containsExactly(-2.0);
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleFloatScore.of(-Float.MAX_VALUE), SimpleFloatScore.of(Float.MAX_VALUE))
                .percentageLevels())
        .containsExactly(2.0);
  }

  @Test
  void zeroAndUnrepresentableRatiosRetainInfinityConventions() {
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.ZERO, SimpleDoubleScore.ZERO)
                .percentageLevels())
        .containsExactly(0.0);
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.ZERO, SimpleDoubleScore.of(1))
                .percentageLevels())
        .containsExactly(Double.POSITIVE_INFINITY);
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.ZERO, SimpleDoubleScore.of(-1))
                .percentageLevels())
        .containsExactly(Double.NEGATIVE_INFINITY);
    assertThat(
            ScoreDifferencePercentage.calculateScoreDifferencePercentage(
                    SimpleDoubleScore.of(Double.MIN_VALUE), SimpleDoubleScore.of(Double.MAX_VALUE))
                .percentageLevels())
        .containsExactly(Double.POSITIVE_INFINITY);
  }

  @Test
  void averagingFiniteRatiosDoesNotOverflowOrLoseCancellation() {
    var maximum = percentage(Double.MAX_VALUE);
    assertThat(
            ScoreDifferencePercentage.averageFloating(List.of(maximum, maximum)).percentageLevels())
        .containsExactly(Double.MAX_VALUE);
    assertThat(
            ScoreDifferencePercentage.averageFloating(
                    List.of(maximum, percentage(1), percentage(1), percentage(-Double.MAX_VALUE)))
                .percentageLevels())
        .containsExactly(0.5);
    assertThat(
            ScoreDifferencePercentage.averageFloating(
                    List.of(percentage(Double.POSITIVE_INFINITY), maximum))
                .percentageLevels())
        .containsExactly(Double.POSITIVE_INFINITY);
    assertThat(
            ScoreDifferencePercentage.averageFloating(
                    List.of(
                        percentage(Double.POSITIVE_INFINITY), percentage(Double.NEGATIVE_INFINITY)))
                .percentageLevels()[0])
        .isNaN();
  }

  private static ScoreDifferencePercentage percentage(double value) {
    return new ScoreDifferencePercentage(new double[] {value});
  }
}
