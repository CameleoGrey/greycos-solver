package greycos.solver.greybay.api.objective;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ExactRationalTest {
  @Test
  void canonicalFractionsAndSignedDenominators() {
    assertThat(ExactRational.of(6, -8)).isEqualTo(ExactRational.of(-3, 4));
    assertThat(ExactRational.of(0, -42)).isEqualTo(ExactRational.ZERO);
    assertThat(ExactRational.of(-6, -8)).hasToString("3/4");
    assertThatIllegalArgumentException().isThrownBy(() -> ExactRational.of(1, 0));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExactRational.ONE.divide(ExactRational.ZERO));
    for (var invalid : new String[] {"1", "2/4", "+1/2", "0/2", "1/-2", "01/2", "1/2/3"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> ExactRational.parse(invalid));
    }
    assertThat(ExactRational.parse("-3/4")).isEqualTo(ExactRational.of(-3, 4));
  }

  @Test
  void exactIntegralAndDecimalConversions() {
    for (Number value : new Number[] {(byte) 3, (short) 3, 3, 3L, BigInteger.valueOf(3)}) {
      assertThat(ExactRational.fromNumber(value)).isEqualTo(ExactRational.of(3));
    }
    assertThat(ExactRational.fromNumber(Long.MIN_VALUE)).hasToString("-9223372036854775808/1");
    assertThat(ExactRational.fromNumber(BigInteger.ONE.shiftLeft(200)))
        .isEqualTo(new ExactRational(BigInteger.ONE.shiftLeft(200), BigInteger.ONE));
    assertThat(ExactRational.fromNumber(new BigDecimal("1.23000")))
        .isEqualTo(ExactRational.of(123, 100));
    assertThat(ExactRational.fromNumber(new BigDecimal("1.23E+8")))
        .isEqualTo(ExactRational.of(123000000));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ExactRational.fromNumber(new AtomicInteger(3)))
        .withMessageContaining("ExactScoreAdapter");
  }

  @Test
  void decimalZeroDoesNotExpandEvenExtremeScales() {
    for (int scale : new int[] {Integer.MIN_VALUE, -100_000, 0, 100_000, Integer.MAX_VALUE}) {
      assertThat(ExactRational.fromNumber(BigDecimal.valueOf(0, scale)))
          .isSameAs(ExactRational.ZERO);
    }
  }

  @Test
  void representedBinaryValuesIncludingSubnormals() {
    assertThat(ExactRational.fromNumber(0.1d)).hasToString("3602879701896397/36028797018963968");
    assertThat(ExactRational.fromNumber(0.1f)).hasToString("13421773/134217728");
    assertThat(ExactRational.fromNumber(-0.0d)).isEqualTo(ExactRational.ZERO);
    assertThat(ExactRational.fromNumber(-0.0f)).isEqualTo(ExactRational.ZERO);
    assertThat(ExactRational.fromNumber(Double.MIN_VALUE))
        .isEqualTo(new ExactRational(BigInteger.ONE, BigInteger.ONE.shiftLeft(1074)));
    assertThat(ExactRational.fromNumber(Float.MIN_VALUE))
        .isEqualTo(new ExactRational(BigInteger.ONE, BigInteger.ONE.shiftLeft(149)));
    for (double value :
        new double[] {Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE, Math.nextUp(1.0)}) {
      assertExactDecimalOracle(ExactRational.fromNumber(value), new BigDecimal(value));
    }
    for (Number value :
        new Number[] {
          Double.NaN,
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY,
          Float.NaN,
          Float.POSITIVE_INFINITY,
          Float.NEGATIVE_INFINITY
        }) {
      assertThatIllegalArgumentException().isThrownBy(() -> ExactRational.fromNumber(value));
    }
  }

  @Test
  void randomBinaryValuesAgreeWithIndependentBigDecimalConversion() {
    var random = new Random(345761L);
    for (int i = 0; i < 1000; i++) {
      double value = Double.longBitsToDouble(random.nextLong());
      if (Double.isFinite(value)) {
        assertExactDecimalOracle(ExactRational.fromNumber(value), new BigDecimal(value));
      }
      float floatValue = Float.intBitsToFloat(random.nextInt());
      if (Float.isFinite(floatValue)) {
        assertExactDecimalOracle(
            ExactRational.fromNumber(floatValue), new BigDecimal((double) floatValue));
      }
    }
  }

  @Test
  void arithmeticHasNoPrimitiveOverflowAndAlwaysReduces() {
    var maximum = ExactRational.of(Long.MAX_VALUE);
    assertThat(maximum.add(maximum).divide(2)).isEqualTo(maximum);
    assertThat(ExactRational.of(Long.MIN_VALUE).abs())
        .isEqualTo(new ExactRational(BigInteger.ONE.shiftLeft(63), BigInteger.ONE));
    var random = new Random(7713L);
    for (int i = 0; i < 500; i++) {
      var a = new BigInteger(200, random).subtract(BigInteger.ONE.shiftLeft(199));
      var b = new BigInteger(160, random).add(BigInteger.ONE);
      var c = new BigInteger(200, random).subtract(BigInteger.ONE.shiftLeft(199));
      var d = new BigInteger(160, random).add(BigInteger.ONE);
      var left = new ExactRational(a, b);
      var right = new ExactRational(c, d);
      assertFractionOracle(left.add(right), a.multiply(d).add(c.multiply(b)), b.multiply(d));
      assertFractionOracle(
          left.subtract(right), a.multiply(d).subtract(c.multiply(b)), b.multiply(d));
      assertFractionOracle(left.multiply(right), a.multiply(c), b.multiply(d));
      if (c.signum() != 0) {
        assertFractionOracle(left.divide(right), a.multiply(d), b.multiply(c));
      }
      assertThat(Integer.signum(left.compareTo(right)))
          .isEqualTo(Integer.signum(a.multiply(d).compareTo(c.multiply(b))));
    }
  }

  private static void assertExactDecimalOracle(ExactRational value, BigDecimal expected) {
    var actual = new BigDecimal(value.numerator()).divide(new BigDecimal(value.denominator()));
    assertThat(actual).isEqualByComparingTo(expected);
  }

  private static void assertFractionOracle(
      ExactRational actual, BigInteger numerator, BigInteger denominator) {
    assertThat(actual.numerator().multiply(denominator))
        .isEqualTo(numerator.multiply(actual.denominator()));
    assertThat(actual.denominator().signum()).isPositive();
    assertThat(actual.numerator().gcd(actual.denominator())).isEqualTo(BigInteger.ONE);
  }
}
