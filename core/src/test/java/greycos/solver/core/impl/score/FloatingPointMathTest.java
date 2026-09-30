package greycos.solver.core.impl.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FloatingPointMathTest {

  @Test
  void representedValuesRoundTripThroughIntegerUnits() {
    var random = new Random(763912L);
    for (int i = 0; i < 5000; i++) {
      float floatValue = Float.intBitsToFloat(random.nextInt());
      if (Float.isFinite(floatValue)) {
        assertThat(
                Float.floatToRawIntBits(
                    FloatingPointMath.fromFloatUnits(FloatingPointMath.units(floatValue))))
            .isEqualTo(Float.floatToRawIntBits(floatValue == 0.0f ? 0.0f : floatValue));
      }
      double doubleValue = Double.longBitsToDouble(random.nextLong());
      if (Double.isFinite(doubleValue)) {
        assertThat(
                Double.doubleToRawLongBits(
                    FloatingPointMath.fromDoubleUnits(FloatingPointMath.units(doubleValue))))
            .isEqualTo(Double.doubleToRawLongBits(doubleValue == 0.0 ? 0.0 : doubleValue));
      }
    }
  }

  @Test
  void roundingHandlesHalfwayStickyBitsAndSubnormals() {
    var floatHalfway = BigInteger.ONE.shiftLeft(24).add(BigInteger.ONE);
    assertThat(FloatingPointMath.roundFloat(floatHalfway, -24)).isEqualTo(1.0f);
    assertThat(FloatingPointMath.roundFloat(floatHalfway.shiftLeft(200).add(BigInteger.ONE), -224))
        .isEqualTo(Math.nextUp(1.0f));
    assertThat(
            FloatingPointMath.roundFloat(
                floatHalfway.shiftLeft(200).subtract(BigInteger.ONE), -224))
        .isEqualTo(1.0f);
    assertThat(FloatingPointMath.roundFloat(BigInteger.ONE, -150)).isZero();
    assertThat(FloatingPointMath.roundFloat(BigInteger.valueOf(3), -150))
        .isEqualTo(2.0f * Float.MIN_VALUE);
    assertThat(Float.floatToRawIntBits(FloatingPointMath.roundFloat(BigInteger.ONE.negate(), -150)))
        .isZero();
    var doubleHalfway = BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE);
    assertThat(FloatingPointMath.roundDouble(doubleHalfway, -53)).isEqualTo(1.0);
    assertThat(
            FloatingPointMath.roundDouble(doubleHalfway.shiftLeft(200).add(BigInteger.ONE), -253))
        .isEqualTo(Math.nextUp(1.0));
    assertThat(FloatingPointMath.roundDouble(BigInteger.ONE, -1075)).isZero();
    assertThat(FloatingPointMath.roundDouble(BigInteger.valueOf(3), -1075))
        .isEqualTo(2.0 * Double.MIN_VALUE);
    assertThat(
            Double.doubleToRawLongBits(
                FloatingPointMath.roundDouble(BigInteger.ONE.negate(), -1075)))
        .isZero();
  }

  @Test
  void productsMatchIndependentExactDecimalOracle() {
    var random = new Random(74211L);
    for (int i = 0; i < 1500; i++) {
      float left = Float.intBitsToFloat(random.nextInt());
      double right = Double.longBitsToDouble(random.nextLong());
      if (!Float.isFinite(left) || !Double.isFinite(right)) {
        continue;
      }
      var exactProduct = new BigDecimal((double) left).multiply(new BigDecimal(right));
      assertFloatProduct(left, right, exactProduct.floatValue());
      double doubleLeft = Double.longBitsToDouble(random.nextLong());
      if (Double.isFinite(doubleLeft)) {
        double expected = new BigDecimal(doubleLeft).multiply(new BigDecimal(right)).doubleValue();
        if (Double.isFinite(expected)) {
          assertThat(FloatingPointMath.multiply(doubleLeft, right))
              .isEqualTo(expected == 0.0 ? 0.0 : expected);
        } else {
          assertThatThrownBy(() -> FloatingPointMath.multiply(doubleLeft, right))
              .isInstanceOf(ArithmeticException.class);
        }
        double mixedExpected =
            new BigDecimal(doubleLeft).multiply(new BigDecimal((double) left)).doubleValue();
        if (Double.isFinite(mixedExpected)) {
          assertThat(FloatingPointMath.multiply(doubleLeft, left))
              .isEqualTo(mixedExpected == 0.0 ? 0.0 : mixedExpected);
        } else {
          assertThatThrownBy(() -> FloatingPointMath.multiply(doubleLeft, left))
              .isInstanceOf(ArithmeticException.class);
        }
      }
      float floatRight = Float.intBitsToFloat(random.nextInt());
      if (Float.isFinite(floatRight)) {
        float floatExpected =
            new BigDecimal((double) left)
                .multiply(new BigDecimal((double) floatRight))
                .floatValue();
        if (Float.isFinite(floatExpected)) {
          assertThat(FloatingPointMath.multiply(left, floatRight))
              .isEqualTo(floatExpected == 0.0f ? 0.0f : floatExpected);
        } else {
          assertThatThrownBy(() -> FloatingPointMath.multiply(left, floatRight))
              .isInstanceOf(ArithmeticException.class);
        }
      }
    }
  }

  @Test
  void integerWeightsRetainAllBitsBeforeProductRounding() {
    long factor = (1L << 53) + 1L;
    double value = Math.nextUp(1.0);
    double expected = new BigDecimal(value).multiply(BigDecimal.valueOf(factor)).doubleValue();
    assertThat(FloatingPointMath.multiply(value, factor)).isEqualTo(expected);
    assertThat(FloatingPointMath.multiply(value, factor)).isNotEqualTo(value * factor);
    long floatFactor = (1L << 24) + 1L;
    float floatValue = Math.nextUp(1.0f);
    float floatExpected =
        new BigDecimal((double) floatValue).multiply(BigDecimal.valueOf(floatFactor)).floatValue();
    assertThat(FloatingPointMath.multiply(floatValue, floatFactor)).isEqualTo(floatExpected);
    assertThat(FloatingPointMath.multiply(floatValue, floatFactor))
        .isNotEqualTo(floatValue * floatFactor);
  }

  @Test
  void divisionMatchesDecimalOracleWithoutDoubleRounding() {
    var random = new Random(517L);
    var context = new MathContext(200, RoundingMode.HALF_EVEN);
    for (int i = 0; i < 300; i++) {
      float left = Float.intBitsToFloat(random.nextInt());
      double right = Double.longBitsToDouble(random.nextLong());
      if (!Float.isFinite(left) || !Double.isFinite(right) || right == 0.0) {
        continue;
      }
      float expected =
          new BigDecimal((double) left).divide(new BigDecimal(right), context).floatValue();
      if (Float.isFinite(expected)) {
        assertThat(FloatingPointMath.divide(left, right))
            .isEqualTo(expected == 0.0f ? 0.0f : expected);
      } else {
        assertThatThrownBy(() -> FloatingPointMath.divide(left, right))
            .isInstanceOf(ArithmeticException.class);
      }
    }
  }

  @Test
  void averagesCanRemainFiniteWhenTheirSumsOverflow() {
    assertThat(
            FloatingPointMath.averageFloatUnits(
                FloatingPointMath.units(Float.MAX_VALUE).shiftLeft(1), 2L))
        .isEqualTo(Float.MAX_VALUE);
    assertThat(
            FloatingPointMath.averageDoubleUnits(
                FloatingPointMath.units(Double.MAX_VALUE).shiftLeft(1), 2L))
        .isEqualTo(Double.MAX_VALUE);
    assertThat(FloatingPointMath.averageFloatUnits(BigInteger.ONE, 2L)).isZero();
    assertThat(FloatingPointMath.averageDoubleUnits(BigInteger.valueOf(3), 2L))
        .isEqualTo(2.0 * Double.MIN_VALUE);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FloatingPointMath.averageDoubleUnits(BigInteger.ONE, 0L));
  }

  @Test
  void invalidInputsAndNonFiniteResultsFailExplicitly() {
    assertThatIllegalArgumentException().isThrownBy(() -> FloatingPointMath.units(Float.NaN));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FloatingPointMath.units(Double.POSITIVE_INFINITY));
    assertThatThrownBy(() -> FloatingPointMath.divide(1.0f, 0.0))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> FloatingPointMath.multiply(Double.MAX_VALUE, 2L))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> FloatingPointMath.multiply(Float.MAX_VALUE, 2L))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> FloatingPointMath.power(-1.0, 0.5))
        .isInstanceOf(ArithmeticException.class);
  }

  private static void assertFloatProduct(float left, double right, float expected) {
    if (Float.isFinite(expected)) {
      assertThat(FloatingPointMath.multiply(left, right))
          .isEqualTo(expected == 0.0f ? 0.0f : expected);
    } else {
      assertThatThrownBy(() -> FloatingPointMath.multiply(left, right))
          .isInstanceOf(ArithmeticException.class);
    }
  }
}
