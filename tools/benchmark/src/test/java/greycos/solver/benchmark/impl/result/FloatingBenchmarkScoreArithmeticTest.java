package greycos.solver.benchmark.impl.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;

import org.junit.jupiter.api.Test;

class FloatingBenchmarkScoreArithmeticTest {

  @Test
  void everyNativeLayoutWidensItsTotalAndRetainsItsNativeAverage() {
    for (var prototype :
        List.<Score<?>>of(
            SimpleFloatScore.of(Float.MAX_VALUE),
            SimpleDoubleScore.of(Double.MAX_VALUE),
            HardSoftFloatScore.of(Float.MAX_VALUE, Float.MIN_VALUE),
            HardSoftDoubleScore.of(Double.MAX_VALUE, Double.MIN_VALUE),
            HardMediumSoftFloatScore.of(Float.MAX_VALUE, 0.1f, -Float.MAX_VALUE),
            HardMediumSoftDoubleScore.of(Double.MAX_VALUE, 0.1, -Double.MAX_VALUE),
            BendableFloatScore.of(
                new float[] {Float.MAX_VALUE, 0.1f}, new float[] {Float.MIN_VALUE}),
            BendableDoubleScore.of(
                new double[] {Double.MAX_VALUE, 0.1}, new double[] {Double.MIN_VALUE}))) {
      var total = FloatingBenchmarkScoreArithmetic.add(prototype, prototype);
      assertThat(total.getClass().getSimpleName())
          .isEqualTo(
              prototype
                  .getClass()
                  .getSimpleName()
                  .replace("Float", "BigDecimal")
                  .replace("Double", "BigDecimal"));
      assertThat(FloatingBenchmarkScoreArithmetic.average(total, 2, prototype))
          .isExactlyInstanceOf(prototype.getClass())
          .isEqualTo(prototype);
    }
  }

  @Test
  void cancellationIsIndependentOfContributionOrder() {
    var doubles =
        new ArrayList<Score<?>>(
            List.of(
                SimpleDoubleScore.of(Double.MAX_VALUE),
                SimpleDoubleScore.of(1),
                SimpleDoubleScore.of(1),
                SimpleDoubleScore.of(-Double.MAX_VALUE)));
    var floats =
        new ArrayList<Score<?>>(
            List.of(
                SimpleFloatScore.of(Float.MAX_VALUE),
                SimpleFloatScore.of(1),
                SimpleFloatScore.of(1),
                SimpleFloatScore.of(-Float.MAX_VALUE)));
    for (var values : List.of(floats, doubles)) {
      for (int rotation = 0; rotation < 4; rotation++) {
        Collections.rotate(values, 1);
        assertCancellation(values);
        Collections.reverse(values);
        assertCancellation(values);
        Collections.reverse(values);
      }
    }
  }

  private static void assertCancellation(List<Score<?>> values) {
    Score total = null;
    for (var score : values) {
      total = FloatingBenchmarkScoreArithmetic.add(total, score);
    }
    assertThat(((SimpleBigDecimalScore) total).score()).isEqualByComparingTo("2");
    var average = FloatingBenchmarkScoreArithmetic.average(total, values.size(), values.getFirst());
    assertThat(average.toLevelNumbers()[0].doubleValue()).isEqualTo(0.5);
    assertThat(average).isExactlyInstanceOf(values.getFirst().getClass());
  }

  @Test
  void averagesRoundSubnormalTiesToEven() {
    var floatMin = SimpleFloatScore.of(Float.MIN_VALUE);
    var doubleMin = SimpleDoubleScore.of(Double.MIN_VALUE);
    assertThat(
            FloatingBenchmarkScoreArithmetic.average(
                FloatingBenchmarkScoreArithmetic.add(floatMin, SimpleFloatScore.ZERO), 2, floatMin))
        .isEqualTo(SimpleFloatScore.ZERO);
    assertThat(
            FloatingBenchmarkScoreArithmetic.average(
                FloatingBenchmarkScoreArithmetic.add(doubleMin, SimpleDoubleScore.ZERO),
                2,
                doubleMin))
        .isEqualTo(SimpleDoubleScore.ZERO);
    assertThat(
            FloatingBenchmarkScoreArithmetic.average(
                FloatingBenchmarkScoreArithmetic.add(
                    floatMin, SimpleFloatScore.of(2 * Float.MIN_VALUE)),
                2,
                floatMin))
        .isEqualTo(SimpleFloatScore.of(2 * Float.MIN_VALUE));
    assertThat(
            FloatingBenchmarkScoreArithmetic.average(
                FloatingBenchmarkScoreArithmetic.add(
                    doubleMin, SimpleDoubleScore.of(2 * Double.MIN_VALUE)),
                2,
                doubleMin))
        .isEqualTo(SimpleDoubleScore.of(2 * Double.MIN_VALUE));
  }

  @Test
  void rangeSpanningDifferenceRemainsExactAndUnbounded() {
    var maximum = SimpleDoubleScore.of(Double.MAX_VALUE);
    var minimum = SimpleDoubleScore.of(-Double.MAX_VALUE);
    var difference =
        (SimpleBigDecimalScore) FloatingBenchmarkScoreArithmetic.difference(maximum, minimum);
    assertThat(difference.score())
        .isEqualByComparingTo(new BigDecimal(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2)));
    assertThat(FloatingBenchmarkScoreArithmetic.averageDifference(difference, 1, maximum))
        .isExactlyInstanceOf(SimpleBigDecimalScore.class);
  }

  @Test
  void preservesExistingStructuralAndNonFloatingArithmeticConventions() {
    var structural = new SimpleFloatScore(-7L, 3.0f);
    var total = FloatingBenchmarkScoreArithmetic.add(null, structural);
    assertThat(total.structuralScore()).isEqualTo(-7L);
    assertThat(FloatingBenchmarkScoreArithmetic.average(total, 1, structural).structuralScore())
        .isZero();
    assertThat(FloatingBenchmarkScoreArithmetic.add(total, structural).structuralScore()).isZero();
    assertThat(FloatingBenchmarkScoreArithmetic.average(SimpleScore.of(3), 2, SimpleScore.ZERO))
        .isEqualTo(SimpleScore.of(1));
    var decimal = SimpleBigDecimalScore.of(new BigDecimal("0.123"));
    assertThat(FloatingBenchmarkScoreArithmetic.average(decimal, 2, decimal))
        .isEqualTo(decimal.divide(2));
  }
}
