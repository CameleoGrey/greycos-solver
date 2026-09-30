package greycos.solver.core.impl.score;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Exact binary arithmetic shared by floating scores and reversible score accumulation. */
public final class FloatingPointMath {

  private FloatingPointMath() {}

  public static float finite(float value, String context) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException("The " + context + " (" + value + ") must be finite.");
    }
    return value == 0.0f ? 0.0f : value;
  }

  public static double finite(double value, String context) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("The " + context + " (" + value + ") must be finite.");
    }
    return value == 0.0 ? 0.0 : value;
  }

  private static float result(float value, String operation) {
    if (!Float.isFinite(value)) {
      throw new ArithmeticException(
          "The floating score " + operation + " produced a non-finite float (" + value + ").");
    }
    return value == 0.0f ? 0.0f : value;
  }

  private static double result(double value, String operation) {
    if (!Double.isFinite(value)) {
      throw new ArithmeticException(
          "The floating score " + operation + " produced a non-finite double (" + value + ").");
    }
    return value == 0.0 ? 0.0 : value;
  }

  public static float add(float left, float right) {
    return result(finite(left, "left operand") + finite(right, "right operand"), "addition");
  }

  public static double add(double left, double right) {
    return result(finite(left, "left operand") + finite(right, "right operand"), "addition");
  }

  public static float subtract(float left, float right) {
    return result(finite(left, "left operand") - finite(right, "right operand"), "subtraction");
  }

  public static double subtract(double left, double right) {
    return result(finite(left, "left operand") - finite(right, "right operand"), "subtraction");
  }

  /** Returns exact integer units of 2^-149. */
  public static BigInteger units(float value) {
    int bits = Float.floatToRawIntBits(finite(value, "float value"));
    int exponent = (bits >>> 23) & 0xff;
    long significand = bits & 0x7fffff;
    if (exponent != 0) {
      significand |= 1L << 23;
    }
    var units = BigInteger.valueOf(significand).shiftLeft(Math.max(0, exponent - 1));
    return bits < 0 ? units.negate() : units;
  }

  /** Returns exact integer units of 2^-1074. */
  public static BigInteger units(double value) {
    long bits = Double.doubleToRawLongBits(finite(value, "double value"));
    int exponent = (int) ((bits >>> 52) & 0x7ff);
    long significand = bits & 0xfffffffffffffL;
    if (exponent != 0) {
      significand |= 1L << 52;
    }
    var units = BigInteger.valueOf(significand).shiftLeft(Math.max(0, exponent - 1));
    return bits < 0 ? units.negate() : units;
  }

  public static float fromFloatUnits(BigInteger units) {
    return roundFloat(units, -149);
  }

  public static double fromDoubleUnits(BigInteger units) {
    return roundDouble(units, -1074);
  }

  public static float averageFloatUnits(BigInteger units, long count) {
    if (count <= 0L) {
      throw new IllegalArgumentException(
          "The floating average count (" + count + ") must be positive.");
    }
    return Float.intBitsToFloat(
        (int) roundRational(units, BigInteger.valueOf(count).shiftLeft(149), 24, -149, 127));
  }

  public static double averageDoubleUnits(BigInteger units, long count) {
    if (count <= 0L) {
      throw new IllegalArgumentException(
          "The floating average count (" + count + ") must be positive.");
    }
    return Double.longBitsToDouble(
        roundRational(units, BigInteger.valueOf(count).shiftLeft(1074), 53, -1074, 1023));
  }

  public static float roundFloat(BigInteger coefficient, int binaryExponent) {
    return Float.intBitsToFloat((int) roundDyadic(coefficient, binaryExponent, 24, -149, 127));
  }

  public static double roundDouble(BigInteger coefficient, int binaryExponent) {
    return Double.longBitsToDouble(roundDyadic(coefficient, binaryExponent, 53, -1074, 1023));
  }

  private static long roundDyadic(
      BigInteger coefficient,
      int exponent,
      int precision,
      int minimumExponent,
      int maximumExponent) {
    return exponent < 0
        ? roundRational(
            coefficient,
            BigInteger.ONE.shiftLeft(-exponent),
            precision,
            minimumExponent,
            maximumExponent)
        : roundRational(
            coefficient.shiftLeft(exponent),
            BigInteger.ONE,
            precision,
            minimumExponent,
            maximumExponent);
  }

  /**
   * Rounds the exact rational directly to IEEE bits, including gradual underflow and ties to even.
   */
  private static long roundRational(
      BigInteger numerator,
      BigInteger denominator,
      int precision,
      int minimumExponent,
      int maximumExponent) {
    if (denominator.signum() == 0) {
      throw new ArithmeticException("The floating score divisor must be nonzero.");
    }
    boolean negative = numerator.signum() * denominator.signum() < 0;
    numerator = numerator.abs();
    denominator = denominator.abs();
    if (numerator.signum() == 0) {
      return 0L;
    }
    int exponent = numerator.bitLength() - denominator.bitLength();
    int comparison =
        exponent >= 0
            ? numerator.compareTo(denominator.shiftLeft(exponent))
            : numerator.shiftLeft(-exponent).compareTo(denominator);
    if (comparison < 0) {
      exponent--;
    }
    int unitExponent = Math.max(exponent - precision + 1, minimumExponent);
    var scaledNumerator = unitExponent < 0 ? numerator.shiftLeft(-unitExponent) : numerator;
    var scaledDenominator = unitExponent > 0 ? denominator.shiftLeft(unitExponent) : denominator;
    var division = scaledNumerator.divideAndRemainder(scaledDenominator);
    var significand = division[0];
    int halfwayComparison = division[1].shiftLeft(1).compareTo(scaledDenominator);
    if (halfwayComparison > 0 || halfwayComparison == 0 && significand.testBit(0)) {
      significand = significand.add(BigInteger.ONE);
    }
    if (significand.signum() == 0) {
      return 0L;
    }
    if (significand.bitLength() > precision) {
      significand = significand.shiftRight(1);
      unitExponent++;
    }
    int roundedExponent = significand.bitLength() - 1 + unitExponent;
    if (roundedExponent > maximumExponent) {
      throw new ArithmeticException(
          "The exact floating score result exceeds the finite "
              + (precision == 24 ? "float" : "double")
              + " range.");
    }
    long bits;
    if (roundedExponent < minimumExponent + precision - 1) {
      bits = significand.longValueExact();
    } else {
      long mantissa = significand.longValueExact();
      int exponentField = roundedExponent + maximumExponent;
      bits = ((long) exponentField << (precision - 1)) | (mantissa & ((1L << (precision - 1)) - 1));
    }
    return negative ? bits | (precision == 24 ? 0x80000000L : Long.MIN_VALUE) : bits;
  }

  public static float multiply(float value, long factor) {
    value = finite(value, "multiplicand");
    if (factor == 0L) {
      return 0.0f;
    }
    if (factor == 1L) {
      return value;
    }
    if (factor == -1L) {
      return value == 0.0f ? 0.0f : -value;
    }
    return roundFloat(units(value).multiply(BigInteger.valueOf(factor)), -149);
  }

  public static float multiply(float value, float factor) {
    return result(finite(value, "multiplicand") * finite(factor, "multiplier"), "multiplication");
  }

  public static float multiply(float value, double factor) {
    value = finite(value, "multiplicand");
    factor = finite(factor, "multiplier");
    if (factor == 0.0) {
      return 0.0f;
    }
    if (factor == 1.0) {
      return value;
    }
    if (factor == -1.0) {
      return value == 0.0f ? 0.0f : -value;
    }
    return roundFloat(units(value).multiply(units(factor)), -1223);
  }

  public static double multiply(double value, long factor) {
    value = finite(value, "multiplicand");
    if (factor == 0L) {
      return 0.0;
    }
    if (factor == 1L) {
      return value;
    }
    if (factor == -1L) {
      return value == 0.0 ? 0.0 : -value;
    }
    return roundDouble(units(value).multiply(BigInteger.valueOf(factor)), -1074);
  }

  public static double multiply(double value, float factor) {
    return result(finite(value, "multiplicand") * finite(factor, "multiplier"), "multiplication");
  }

  public static double multiply(double value, double factor) {
    return result(finite(value, "multiplicand") * finite(factor, "multiplier"), "multiplication");
  }

  public static float divide(float value, double divisor) {
    return Float.intBitsToFloat(
        (int) roundRational(units(value).shiftLeft(925), units(divisor), 24, -149, 127));
  }

  public static double divide(double value, double divisor) {
    return Double.longBitsToDouble(roundRational(units(value), units(divisor), 53, -1074, 1023));
  }

  /**
   * StrictMath defines the binary64 power; Float scores subsequently round that result to binary32.
   */
  public static float power(float value, double exponent) {
    return result(
        (float) StrictMath.pow(finite(value, "base"), finite(exponent, "exponent")), "power");
  }

  public static double power(double value, double exponent) {
    return result(StrictMath.pow(finite(value, "base"), finite(exponent, "exponent")), "power");
  }

  /** Exact represented value; this never parses a floating value's abbreviated decimal string. */
  public static BigDecimal exact(Number value) {
    return switch (Objects.requireNonNull(value)) {
      case Float number -> new BigDecimal((double) finite(number.floatValue(), "number"));
      case Double number -> new BigDecimal(finite(number.doubleValue(), "number"));
      case BigDecimal number -> number;
      case BigInteger number -> new BigDecimal(number);
      case Byte number -> BigDecimal.valueOf(number.longValue());
      case Short number -> BigDecimal.valueOf(number.longValue());
      case Integer number -> BigDecimal.valueOf(number.longValue());
      case Long number -> BigDecimal.valueOf(number.longValue());
      default ->
          throw new IllegalArgumentException(
              "Unsupported floating score number type (" + value.getClass().getName() + ").");
    };
  }

  public static float toFloat(Number value) {
    return result(exact(value).floatValue(), "conversion of " + value);
  }

  public static double toDouble(Number value) {
    return result(exact(value).doubleValue(), "conversion of " + value);
  }

  public static float parseFloat(Class<?> scoreClass, String scoreString, String token) {
    try {
      return token.equals("*") ? -Float.MAX_VALUE : finite(Float.parseFloat(token), "score level");
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException(
          "The scoreString ("
              + scoreString
              + ") for "
              + scoreClass.getSimpleName()
              + " has an invalid finite float level ("
              + token
              + ").",
          failure);
    }
  }

  public static double parseDouble(Class<?> scoreClass, String scoreString, String token) {
    try {
      return token.equals("*")
          ? -Double.MAX_VALUE
          : finite(Double.parseDouble(token), "score level");
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException(
          "The scoreString ("
              + scoreString
              + ") for "
              + scoreClass.getSimpleName()
              + " has an invalid finite double level ("
              + token
              + ").",
          failure);
    }
  }
}
