package greycos.solver.greybay.impl.sampling;

import org.apache.commons.math3.special.Erf;

/**
 * Pinned normal primitives, version 1: Commons Math 3.6.1 central erfc, 256-level Laplace continued
 * fraction in the tail, and bounded log-CDF inversion. Commons Math's erfInv(2*p-1) loses the lower
 * probability endpoint and is deliberately unused.
 */
final class NormalMath {
  static final String VERSION = "normal-1-commons-math-3.6.1";
  private static final double SQRT_TWO = StrictMath.sqrt(2.0);
  private static final double LOG_SQRT_TWO_PI = 0.91893853320467274178;
  private static final double LOG_TWO = StrictMath.log(2.0);
  // Positive half of the 16-point Gauss-Legendre rule, in ascending order.
  private static final double[] NODES = {
    0.095012509837637440185,
    0.28160355077925891323,
    0.45801677765722738634,
    0.61787624440264374845,
    0.75540440835500303390,
    0.86563120238783174388,
    0.94457502307323257608,
    0.98940093499164993260
  };
  private static final double[] WEIGHTS = {
    0.18945061045506849629,
    0.18260341504492358887,
    0.16915651939500253819,
    0.14959598881657673208,
    0.12462897125553387205,
    0.095158511682492784810,
    0.062253523938647892863,
    0.027152459411754094852
  };

  private NormalMath() {}

  static double logDensity(double x) {
    return -0.5 * x * x - LOG_SQRT_TWO_PI;
  }

  static double logCdf(double x) {
    if (!Double.isFinite(x))
      throw new IllegalArgumentException("Normal argument must be finite: " + x);
    if (x > 0) return StrictMath.log1p(-StrictMath.exp(logCdf(-x)));
    double z = -x;
    if (z < 8) return StrictMath.log(Erf.erfc(z / SQRT_TWO)) - LOG_TWO;
    double fraction = 0;
    for (int term = 256; term >= 1; term--) fraction = term / (z + fraction);
    return logDensity(z) - StrictMath.log(z + fraction);
  }

  static double cdf(double x) {
    return x <= 0 ? StrictMath.exp(logCdf(x)) : -StrictMath.expm1(logCdf(-x));
  }

  static double inverseCdf(double probability) {
    if (!Double.isFinite(probability) || probability <= 0 || probability >= 1)
      throw new IllegalArgumentException("Normal probability must be in (0, 1): " + probability);
    if (probability == 0.5) return 0;
    double target = StrictMath.log(probability < 0.5 ? probability : 1 - probability);
    double lower = 0, upper = 40;
    for (int iteration = 0; iteration < 80 && Math.nextUp(lower) < upper; iteration++) {
      double midpoint = lower + (upper - lower) * 0.5;
      if (logCdf(-midpoint) > target) lower = midpoint;
      else upper = midpoint;
    }
    double result = lower + (upper - lower) * 0.5;
    return probability < 0.5 ? -result : result;
  }

  /** Log probability over [lower, lower + width], retaining narrow positive widths. */
  static double logInterval(double lower, double width) {
    if (!Double.isFinite(lower) || !Double.isFinite(width) || width <= 0)
      throw new IllegalArgumentException(
          "Normal interval requires finite lower and positive width: " + lower + ", " + width);
    double upper = lower + width;
    if (!Double.isFinite(upper)) throw new IllegalArgumentException("Normal interval overflows.");
    if (width * (1 + Math.abs(lower) + width) <= 0.5) return localIntegral(lower, width);
    double value;
    if (lower >= 0) value = logDifference(logCdf(-lower), logCdf(-upper));
    else if (upper <= 0) value = logDifference(logCdf(upper), logCdf(lower));
    else value = StrictMath.log(cdf(upper) - cdf(lower));
    if (!Double.isFinite(value))
      throw new IllegalArgumentException(
          "Unresolved normal probability interval: " + lower + ", " + width);
    return value;
  }

  private static double localIntegral(double lower, double width) {
    double half = width * 0.5;
    double midpoint = lower + half;
    double shift = logDensity(midpoint);
    double sum = 0;
    for (int i = 0; i < NODES.length; i++) {
      double offset = half * NODES[i];
      // Difference of squares without subtracting two nearly equal log densities.
      double leftDifference = midpoint * offset - 0.5 * offset * offset;
      double rightDifference = -midpoint * offset - 0.5 * offset * offset;
      sum += WEIGHTS[i] * (StrictMath.exp(leftDifference) + StrictMath.exp(rightDifference));
    }
    return StrictMath.log(width) - LOG_TWO + shift + StrictMath.log(sum);
  }

  static double logDifference(double larger, double smaller) {
    if (!(larger > smaller))
      throw new IllegalArgumentException(
          "Unresolved log probability difference: " + larger + ", " + smaller);
    return larger + StrictMath.log(-StrictMath.expm1(smaller - larger));
  }

  static double logSumExp(double[] values) {
    double largest = java.util.Arrays.stream(values).max().orElseThrow();
    if (!Double.isFinite(largest))
      throw new IllegalArgumentException("Nonfinite mixture component.");
    double sum = 0;
    for (double value : values) sum += StrictMath.exp(value - largest);
    return largest + StrictMath.log(sum);
  }
}
