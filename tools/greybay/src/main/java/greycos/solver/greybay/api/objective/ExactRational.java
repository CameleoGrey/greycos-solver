package greycos.solver.greybay.api.objective;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/** An immutable fraction in lowest terms, with a positive denominator and canonical zero. */
public record ExactRational(BigInteger numerator, BigInteger denominator)
    implements Comparable<ExactRational> {

  public static final ExactRational ZERO = of(0);
  public static final ExactRational ONE = of(1);

  public ExactRational {
    Objects.requireNonNull(numerator, "numerator");
    Objects.requireNonNull(denominator, "denominator");
    if (denominator.signum() == 0) {
      throw new IllegalArgumentException("The denominator must not be zero.");
    }
    if (denominator.signum() < 0) {
      numerator = numerator.negate();
      denominator = denominator.negate();
    }
    var divisor = numerator.gcd(denominator);
    numerator = numerator.divide(divisor);
    denominator = denominator.divide(divisor);
  }

  public static ExactRational of(long value) {
    return new ExactRational(BigInteger.valueOf(value), BigInteger.ONE);
  }

  public static ExactRational of(long numerator, long denominator) {
    return new ExactRational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
  }

  /**
   * Converts standard immutable numbers exactly. Binary floating point uses its represented bits;
   * neither decimal display rounding nor {@code Number.doubleValue()} is an exact adapter. Custom
   * number types require an application {@link ExactScoreAdapter}.
   */
  public static ExactRational fromNumber(Number value) {
    Objects.requireNonNull(value, "value");
    return switch (value) {
      case Byte number -> of(number.longValue());
      case Short number -> of(number.longValue());
      case Integer number -> of(number.longValue());
      case Long number -> of(number);
      case BigInteger number when number.getClass() == BigInteger.class ->
          new ExactRational(number, BigInteger.ONE);
      case BigDecimal number when number.getClass() == BigDecimal.class -> {
        if (number.signum() == 0) yield ZERO;
        var scale = number.scale();
        if (scale >= 0) {
          yield new ExactRational(number.unscaledValue(), BigInteger.TEN.pow(scale));
        }
        yield new ExactRational(
            number.unscaledValue().multiply(BigInteger.TEN.pow(Math.negateExact(scale))),
            BigInteger.ONE);
      }
      case Float number -> fromFloat(number);
      case Double number -> fromDouble(number);
      default ->
          throw new IllegalArgumentException(
              "Unsupported exact number type ("
                  + value.getClass().getName()
                  + "); register an ExactScoreAdapter.");
    };
  }

  private static ExactRational fromFloat(float value) {
    if (!Float.isFinite(value)) {
      throw new IllegalArgumentException("The float score level (" + value + ") must be finite.");
    }
    int bits = Float.floatToRawIntBits(value);
    int exponent = (bits >>> 23) & 0xff;
    int significand = (bits & 0x7fffff) | (exponent == 0 ? 0 : 0x800000);
    return fromBinary(significand, exponent == 0 ? -149 : exponent - 150, bits < 0);
  }

  private static ExactRational fromDouble(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("The double score level (" + value + ") must be finite.");
    }
    long bits = Double.doubleToRawLongBits(value);
    int exponent = (int) ((bits >>> 52) & 0x7ff);
    long significand = (bits & 0xfffffffffffffL) | (exponent == 0 ? 0 : 0x10000000000000L);
    return fromBinary(significand, exponent == 0 ? -1074 : exponent - 1075, bits < 0);
  }

  private static ExactRational fromBinary(long significand, int exponent, boolean negative) {
    var numerator = BigInteger.valueOf(negative ? -significand : significand);
    return exponent >= 0
        ? new ExactRational(numerator.shiftLeft(exponent), BigInteger.ONE)
        : new ExactRational(numerator, BigInteger.ONE.shiftLeft(-exponent));
  }

  /**
   * Reads the canonical encoding returned by {@link #toString()}, rejecting alternate encodings.
   */
  public static ExactRational parse(String encoded) {
    Objects.requireNonNull(encoded, "encoded");
    var parts = encoded.split("/", -1);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid rational encoding (" + encoded + ").");
    }
    var value = new ExactRational(new BigInteger(parts[0]), new BigInteger(parts[1]));
    if (!value.toString().equals(encoded)) {
      throw new IllegalArgumentException("Noncanonical rational encoding (" + encoded + ").");
    }
    return value;
  }

  public ExactRational add(ExactRational other) {
    var common = denominator.gcd(other.denominator);
    var leftMultiplier = other.denominator.divide(common);
    var rightMultiplier = denominator.divide(common);
    return new ExactRational(
        numerator.multiply(leftMultiplier).add(other.numerator.multiply(rightMultiplier)),
        denominator.multiply(leftMultiplier));
  }

  public ExactRational subtract(ExactRational other) {
    return add(other.negate());
  }

  public ExactRational multiply(ExactRational other) {
    var leftDivisor = numerator.gcd(other.denominator);
    var rightDivisor = other.numerator.gcd(denominator);
    return new ExactRational(
        numerator.divide(leftDivisor).multiply(other.numerator.divide(rightDivisor)),
        denominator.divide(rightDivisor).multiply(other.denominator.divide(leftDivisor)));
  }

  public ExactRational divide(ExactRational other) {
    if (other.numerator.signum() == 0) {
      throw new IllegalArgumentException("The divisor must not be zero.");
    }
    return multiply(new ExactRational(other.denominator, other.numerator));
  }

  public ExactRational divide(long divisor) {
    return divide(of(divisor));
  }

  public ExactRational negate() {
    return new ExactRational(numerator.negate(), denominator);
  }

  public ExactRational abs() {
    return numerator.signum() < 0 ? negate() : this;
  }

  public int signum() {
    return numerator.signum();
  }

  /** Rounded presentation only; comparisons and calibration never use this conversion. */
  public BigDecimal toBigDecimal(MathContext context) {
    return new BigDecimal(numerator).divide(new BigDecimal(denominator), context);
  }

  @Override
  public int compareTo(ExactRational other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }

  @Override
  public String toString() {
    return numerator + "/" + denominator;
  }
}
