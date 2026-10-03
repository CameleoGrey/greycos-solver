package greycos.solver.core.impl.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.alns.AlnsScoreMath;

import org.junit.jupiter.api.Test;

class ScoreArithmeticTest {

  @Test
  void differencesRemainExactBeyondDoubleRange() {
    var high = SimpleDoubleScore.of(Double.MAX_VALUE);
    var low = high.negate();
    var difference = ScoreArithmetic.difference(high, low)[0];
    assertThat(difference)
        .isEqualTo(new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2)));
    assertThat(ScoreArithmetic.ratio(difference, Double.MAX_VALUE)).isEqualTo(2.0);
    assertThat(ScoreArithmetic.differenceAtLeast(high, low, high)).isTrue();
    assertThat(ScoreArithmetic.differenceAtLeast(low, high, low)).isFalse();
    assertThat(ScoreArithmetic.differenceString(high, low)).contains("359538626972");
  }

  @Test
  void feasibilityDifferenceDoesNotConstructAnOverflowingSoftLevel() {
    var high = HardSoftDoubleScore.of(0.5, Double.MAX_VALUE);
    var low = HardSoftDoubleScore.of(0.25, -Double.MAX_VALUE);
    assertThat(ScoreArithmetic.isFeasibleDifference(high, low, 1)).isTrue();
    assertThat(ScoreArithmetic.isFeasibleDifference(low, high, 1)).isFalse();
  }

  @Test
  void integralFeasibilityUsesExactComponentwiseComparisons() {
    var low = HardSoftScore.of(Long.MIN_VALUE, Long.MAX_VALUE);
    var high = HardSoftScore.of(1, Long.MIN_VALUE);
    assertThat(ScoreArithmetic.isFeasibleDifference(low, high, 1)).isFalse();
    assertThat(ScoreArithmetic.isFeasibleDifference(high, low, 1)).isTrue();
    assertThat(ScoreArithmetic.isFeasibleDifference(HardSoftScore.of(1, Long.MAX_VALUE), high, 1))
        .isTrue();
    var baseline = BendableScore.of(new long[] {0, 0}, new long[] {Long.MIN_VALUE});
    assertThat(
            ScoreArithmetic.isFeasibleDifference(
                BendableScore.of(new long[] {1, -1}, new long[] {Long.MAX_VALUE}), baseline, 2))
        .isFalse();
    assertThat(
            ScoreArithmetic.isFeasibleDifference(
                BendableScore.of(new long[] {1, 0}, new long[] {Long.MAX_VALUE}), baseline, 2))
        .isTrue();
    assertThat(
            ScoreArithmetic.isFeasibleDifference(
                new HardSoftScore(Long.MIN_VALUE, 0, 0), new HardSoftScore(0, 0, 0), 1))
        .isFalse();
  }

  @Test
  void decimalHardDifferencesRetainExactOrderingAndIgnoreSoftLevels() {
    var low =
        HardSoftBigDecimalScore.of(
            new BigDecimal("9007199254740992.0000000000001"), BigDecimal.ONE);
    var high =
        HardSoftBigDecimalScore.of(
            new BigDecimal("9007199254740992.0000000000002"), BigDecimal.TEN.negate());
    assertThat(ScoreArithmetic.isFeasibleDifference(high, low, 1)).isTrue();
    assertThat(ScoreArithmetic.isFeasibleDifference(low, high, 1)).isFalse();
  }

  @Test
  void floatDifferenceDoesNotRoundToFloat() {
    var difference =
        ScoreArithmetic.difference(
            SimpleFloatScore.of(Float.MAX_VALUE), SimpleFloatScore.of(-Float.MAX_VALUE))[0];
    assertThat(ScoreArithmetic.ratio(difference, Float.MAX_VALUE)).isEqualTo(2.0);
  }

  @Test
  void alnsUsesExactBinaryValues() {
    assertThat(AlnsScoreMath.decimal(0.1f)).isEqualTo(new BigDecimal((double) 0.1f));
    assertThat(AlnsScoreMath.decimal(0.1)).isEqualTo(new BigDecimal(0.1));
    assertThat(
            AlnsScoreMath.difference(
                SimpleDoubleScore.of(Double.MAX_VALUE), SimpleDoubleScore.of(-Double.MAX_VALUE))[0])
        .isEqualTo(new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2)));
  }
}
