package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** Exact GLS arithmetic with checked primitive operations before widening. */
public final class GuidedLocalSearchNumber implements Comparable<GuidedLocalSearchNumber> {

  public static final GuidedLocalSearchNumber ZERO = new GuidedLocalSearchNumber(0L, null, null);
  public static final GuidedLocalSearchNumber ONE = new GuidedLocalSearchNumber(1L, null, null);

  private final long integral;
  private final @Nullable BigInteger wideIntegral;
  private final @Nullable BigDecimal decimal;

  private GuidedLocalSearchNumber(
      long integral, @Nullable BigInteger wideIntegral, @Nullable BigDecimal decimal) {
    this.integral = integral;
    this.wideIntegral = wideIntegral;
    this.decimal = decimal;
  }

  public static GuidedLocalSearchNumber of(long value) {
    return value == 0L ? ZERO : value == 1L ? ONE : new GuidedLocalSearchNumber(value, null, null);
  }

  public static GuidedLocalSearchNumber of(BigInteger value) {
    Objects.requireNonNull(value);
    return value.bitLength() < 64
        ? of(value.longValue())
        : new GuidedLocalSearchNumber(0L, value, null);
  }

  public static GuidedLocalSearchNumber of(BigDecimal value) {
    var normalized = Objects.requireNonNull(value).stripTrailingZeros();
    return normalized.scale() <= 0
        ? of(normalized.toBigIntegerExact())
        : new GuidedLocalSearchNumber(0L, null, normalized);
  }

  public static GuidedLocalSearchNumber of(Number value) {
    return switch (Objects.requireNonNull(value)) {
      case BigDecimal number -> of(number);
      case BigInteger number -> of(number);
      case Long number -> of(number.longValue());
      case Integer number -> of(number.longValue());
      case Short number -> of(number.longValue());
      case Byte number -> of(number.longValue());
      case Float number -> ofFloating(number.doubleValue());
      case Double number -> ofFloating(number.doubleValue());
      default ->
          throw new IllegalArgumentException(
              "GLS requires integral, finite floating-point or BigDecimal numbers, but received (%s) of type (%s)."
                  .formatted(value, value.getClass().getName()));
    };
  }

  private static GuidedLocalSearchNumber ofFloating(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(
          "GLS requires a finite score or cost, but received (" + value + ").");
    }
    // This constructor preserves the exact IEEE value; valueOf would instead use its decimal
    // spelling.
    return of(new BigDecimal(value));
  }

  public GuidedLocalSearchNumber add(GuidedLocalSearchNumber other) {
    if (other.signum() == 0) {
      return this;
    }
    if (signum() == 0) {
      return other;
    }
    if (decimal != null || other.decimal != null) {
      return of(toBigDecimal().add(other.toBigDecimal()));
    }
    if (wideIntegral == null && other.wideIntegral == null) {
      try {
        return of(Math.addExact(integral, other.integral));
      } catch (ArithmeticException overflow) {
        // Widen instead of wrapping or saturating.
      }
    }
    return of(toBigInteger().add(other.toBigInteger()));
  }

  public GuidedLocalSearchNumber subtract(GuidedLocalSearchNumber other) {
    if (other.signum() == 0) {
      return this;
    }
    if (decimal != null || other.decimal != null) {
      return of(toBigDecimal().subtract(other.toBigDecimal()));
    }
    if (wideIntegral == null && other.wideIntegral == null) {
      try {
        return of(Math.subtractExact(integral, other.integral));
      } catch (ArithmeticException overflow) {
        // Widen instead of wrapping or saturating.
      }
    }
    return of(toBigInteger().subtract(other.toBigInteger()));
  }

  public GuidedLocalSearchNumber multiply(long factor) {
    if (factor == 0L || signum() == 0) {
      return ZERO;
    }
    if (factor == 1L) {
      return this;
    }
    if (isPrimitive()) {
      try {
        return of(Math.multiplyExact(integral, factor));
      } catch (ArithmeticException overflow) {
        // Widen below.
      }
    }
    return decimal != null
        ? of(decimal.multiply(BigDecimal.valueOf(factor)))
        : of(toBigInteger().multiply(BigInteger.valueOf(factor)));
  }

  public GuidedLocalSearchNumber multiply(GuidedLocalSearchNumber other) {
    if (signum() == 0 || other.signum() == 0) {
      return ZERO;
    }
    if (other.equals(ONE)) {
      return this;
    }
    if (decimal != null || other.decimal != null) {
      return of(toBigDecimal().multiply(other.toBigDecimal()));
    }
    if (wideIntegral == null && other.wideIntegral == null) {
      try {
        return of(Math.multiplyExact(integral, other.integral));
      } catch (ArithmeticException overflow) {
        // Widen instead of wrapping or saturating.
      }
    }
    return of(toBigInteger().multiply(other.toBigInteger()));
  }

  public int signum() {
    return decimal != null
        ? decimal.signum()
        : wideIntegral != null ? wideIntegral.signum() : Long.compare(integral, 0L);
  }

  public boolean isPrimitive() {
    return decimal == null && wideIntegral == null;
  }

  public BigDecimal toBigDecimal() {
    return decimal != null
        ? decimal
        : wideIntegral != null ? new BigDecimal(wideIntegral) : BigDecimal.valueOf(integral);
  }

  /** Allocation-free common comparison; widens only when an intermediate operation overflows. */
  static int compareAdjusted(
      long left,
      long right,
      GuidedLocalSearchNumber leftPenalty,
      GuidedLocalSearchNumber rightPenalty,
      GuidedLocalSearchNumber numerator,
      GuidedLocalSearchNumber denominator) {
    if (leftPenalty.isPrimitive()
        && rightPenalty.isPrimitive()
        && numerator.isPrimitive()
        && denominator.isPrimitive()) {
      try {
        var scoreDifference = Math.subtractExact(left, right);
        var penaltyDifference = Math.subtractExact(leftPenalty.integral, rightPenalty.integral);
        return Long.compare(
            Math.subtractExact(
                Math.multiplyExact(scoreDifference, denominator.integral),
                Math.multiplyExact(penaltyDifference, numerator.integral)),
            0L);
      } catch (ArithmeticException overflow) {
        // Exact wide or decimal comparison below.
      }
    }
    return of(left)
        .subtract(of(right))
        .multiply(denominator)
        .subtract(leftPenalty.subtract(rightPenalty).multiply(numerator))
        .signum();
  }

  private BigInteger toBigInteger() {
    return wideIntegral != null ? wideIntegral : BigInteger.valueOf(integral);
  }

  @Override
  public int compareTo(GuidedLocalSearchNumber other) {
    if (decimal != null || other.decimal != null) {
      return toBigDecimal().compareTo(other.toBigDecimal());
    }
    if (wideIntegral == null && other.wideIntegral == null) {
      return Long.compare(integral, other.integral);
    }
    return toBigInteger().compareTo(other.toBigInteger());
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof GuidedLocalSearchNumber number && compareTo(number) == 0;
  }

  @Override
  public int hashCode() {
    return toBigDecimal().stripTrailingZeros().hashCode();
  }

  @Override
  public String toString() {
    return decimal != null
        ? decimal.toPlainString()
        : wideIntegral != null ? wideIntegral.toString() : Long.toString(integral);
  }
}
