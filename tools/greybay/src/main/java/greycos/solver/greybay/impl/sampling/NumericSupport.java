package greycos.solver.greybay.impl.sampling;

import java.math.BigDecimal;
import java.math.BigInteger;

import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.NumericPrior;
import greycos.solver.greybay.config.RealDimension;

/** Numerical geometry shared by schema validation, prior sampling and likelihoods. */
public final class NumericSupport {
  private NumericSupport() {}

  public static void validateWidth(String name, double lower, double upper) {
    double width = upper - lower;
    if (!Double.isFinite(lower)
        || !Double.isFinite(upper)
        || !Double.isFinite(width)
        || width <= 0
        || width / 100 == 0)
      throw new IllegalArgumentException(
          "Numerical support for ("
              + name
              + ") must have a finite, resolved width supporting a positive bandwidth.");
  }

  public static double boundary(long lower, long step, long index) {
    var twice =
        BigInteger.valueOf(lower)
            .shiftLeft(1)
            .add(
                BigInteger.valueOf(step)
                    .multiply(BigInteger.valueOf(index).shiftLeft(1).subtract(BigInteger.ONE)));
    return new BigDecimal(twice).divide(BigDecimal.valueOf(2)).doubleValue();
  }

  public static void validateInteger(
      String name, long lower, long upper, long step, NumericPrior prior) {
    long count = (upper - lower) / step + 1;
    double left = boundary(lower, step, 0);
    double right = boundary(lower, step, count);
    if (prior == NumericPrior.LOGARITHMIC && left <= 0)
      throw new IllegalArgumentException(
          "Log integer dimension (" + name + ") requires a positive lower cell boundary.");
    double start = transform(left, prior), end = transform(right, prior);
    validateWidth(name, start, end);
    // Exact cell arithmetic precedes binary64 conversion. Prove all cells separated when the
    // worst-case rounding error is smaller than the smallest exact interval; otherwise inspect.
    double endpointUlp = Math.max(Math.ulp(left), Math.ulp(right));
    double minimumConvertedGap = step - endpointUlp;
    boolean proven = minimumConvertedGap > 0;
    if (prior == NumericPrior.LOGARITHMIC) {
      double logUlp = Math.max(Math.ulp(start), Math.ulp(end));
      proven &= StrictMath.log1p(minimumConvertedGap / right) > 4 * logUlp;
    }
    if (!proven) {
      // The exact scan is intentionally bounded; very dense unsupported coordinates must use
      // categorical constants, rather than accepting a grid whose precision was not established.
      if (count > 1_000_000)
        throw new IllegalArgumentException(
            "Integer grid ("
                + name
                + ") is too fine to prove all cell boundaries numerically resolved; use a larger step or categorical constants.");
      double previous = start;
      for (long i = 1; i <= count; i++) {
        double next = transform(boundary(lower, step, i), prior);
        if (!(next > previous) || !((next - previous) / (end - start) > 0))
          throw new IllegalArgumentException(
              "Integer grid (" + name + ") has an unresolved cell at index (" + (i - 1) + ").");
        previous = next;
      }
    }
  }

  static double transform(double value, NumericPrior prior) {
    return prior == NumericPrior.LOGARITHMIC ? StrictMath.log(value) : value;
  }

  static Geometry of(IntegerDimension dimension) {
    return new Geometry(
        boundary(dimension.lower(), dimension.step(), 0),
        boundary(dimension.lower(), dimension.step(), dimension.gridCount()),
        dimension.prior(),
        (double) Math.multiplyExact(dimension.gridCount(), dimension.step()));
  }

  static Geometry of(RealDimension dimension) {
    return new Geometry(
        dimension.lower(),
        dimension.upper(),
        dimension.prior(),
        dimension.upper() - dimension.lower());
  }

  /**
   * A common shift preserves the distribution and resolves narrow intervals at large magnitudes.
   */
  record Geometry(double originalLower, double originalUpper, NumericPrior prior, double span) {
    double lower() {
      return 0;
    }

    double upper() {
      return width();
    }

    double width() {
      return transformOffset(span);
    }

    double transform(double value) {
      return transformOffset(value - originalLower);
    }

    double transformOffset(double offset) {
      if (prior == NumericPrior.LINEAR) return offset;
      double relative = offset / originalLower;
      return Double.isFinite(relative)
          ? StrictMath.log1p(relative)
          : StrictMath.log(originalLower + offset) - StrictMath.log(originalLower);
    }

    double inverse(double value) {
      if (prior == NumericPrior.LINEAR) return originalLower + value;
      // expm1 preserves narrow grids; the alternate form avoids overflowing an enormous ratio.
      if (value <= 1) return originalLower + originalLower * StrictMath.expm1(value);
      double ratio = StrictMath.exp(value);
      return Double.isFinite(ratio)
          ? originalLower * ratio
          : StrictMath.exp(StrictMath.log(originalLower) + value);
    }
  }
}
