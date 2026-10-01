package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact signed observations. Fractions are reduced before they enter the bounded learning window.
 */
record GuidedLocalSearchRational(BigInteger numerator, BigInteger denominator)
    implements Comparable<GuidedLocalSearchRational> {

  GuidedLocalSearchRational {
    if (denominator.signum() <= 0) {
      throw new IllegalArgumentException("The GLS observation denominator must be positive.");
    }
    var common = numerator.gcd(denominator);
    numerator = numerator.divide(common);
    denominator = denominator.divide(common);
  }

  static GuidedLocalSearchRational of(GuidedLocalSearchNumber number, int divisor) {
    var decimal = number.toBigDecimal();
    var numerator = decimal.unscaledValue();
    var denominator = BigInteger.valueOf(divisor);
    if (decimal.scale() > 0)
      denominator = denominator.multiply(BigInteger.TEN.pow(decimal.scale()));
    else if (decimal.scale() < 0)
      numerator = numerator.multiply(BigInteger.TEN.pow(-decimal.scale()));
    return new GuidedLocalSearchRational(numerator, denominator);
  }

  GuidedLocalSearchRational negate() {
    return new GuidedLocalSearchRational(numerator.negate(), denominator);
  }

  static GuidedLocalSearchRational median(List<GuidedLocalSearchRational> values) {
    if (values.isEmpty()) throw new IllegalArgumentException("GLS median requires observations.");
    var sorted = new ArrayList<>(values);
    sorted.sort(null);
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 != 0) return sorted.get(middle);
    var left = sorted.get(middle - 1);
    var right = sorted.get(middle);
    return new GuidedLocalSearchRational(
        left.numerator.multiply(right.denominator).add(right.numerator.multiply(left.denominator)),
        left.denominator.multiply(right.denominator).multiply(BigInteger.TWO));
  }

  int weight(GuidedLocalSearchScale scale) {
    // BigInteger.divide truncates toward zero for either sign, without an intermediate rounding.
    var change =
        numerator
            .multiply(BigInteger.valueOf(4))
            .multiply(scale.denominator().toBigDecimal().toBigIntegerExact())
            .divide(denominator.multiply(scale.numerator().toBigDecimal().toBigIntegerExact()));
    return change
        .add(BigInteger.valueOf(4))
        .max(BigInteger.ONE)
        .min(BigInteger.valueOf(16))
        .intValueExact();
  }

  @Override
  public int compareTo(GuidedLocalSearchRational other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }
}
