package greycos.solver.greybay.impl.sampling;

import java.util.Arrays;
import java.util.List;

import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.NumericPrior;
import greycos.solver.greybay.config.RealDimension;

/** Unit-weight prior and individually normalized unit-weight truncated normal kernels. */
final class NumericDensity implements Density {
  private final Dimension dimension;
  private final NumericSupport.Geometry support;
  private final double[] centers;
  private final double[] bandwidths;
  private final double[] logNormalizers;

  NumericDensity(Dimension dimension, List<Object> observations) {
    this.dimension = dimension;
    support =
        dimension instanceof IntegerDimension integer
            ? NumericSupport.of(integer)
            : NumericSupport.of((RealDimension) dimension);
    centers = observations.stream().mapToDouble(this::coordinate).toArray();
    bandwidths = new double[centers.length];
    logNormalizers = new double[centers.length];
    double[] distinct = Arrays.stream(centers).sorted().distinct().toArray();
    double minimumBandwidth = support.width() / Math.min(100, centers.length + 1);
    for (int i = 0; i < centers.length; i++) {
      int position = Arrays.binarySearch(distinct, centers[i]);
      double left = position == 0 ? support.lower() : distinct[position - 1];
      double right = position == distinct.length - 1 ? support.upper() : distinct[position + 1];
      bandwidths[i] =
          Math.max(
              minimumBandwidth,
              Math.min(support.width(), Math.max(centers[i] - left, right - centers[i])));
      if (!Double.isFinite(bandwidths[i]) || bandwidths[i] <= 0)
        throw new IllegalArgumentException("Unresolved numerical kernel bandwidth.");
      logNormalizers[i] =
          NormalMath.logInterval(
              (support.lower() - centers[i]) / bandwidths[i], support.width() / bandwidths[i]);
    }
  }

  @Override
  public Object sample(SplitMix64 random) {
    if (dimension instanceof IntegerDimension integer && integer.gridCount() == 1)
      return integer.lower();
    int component = centers.length == 0 ? 0 : (int) (random.nextDouble() * (centers.length + 1));
    double coordinate;
    if (component == 0
        && dimension instanceof IntegerDimension integer
        && integer.prior() == NumericPrior.LINEAR) {
      long cell =
          Math.min(integer.gridCount() - 1, (long) (random.nextDouble() * integer.gridCount()));
      return integer.lower() + cell * integer.step();
    }
    if (component == 0) coordinate = support.lower() + random.nextDouble() * support.width();
    else {
      int index = component - 1;
      double lowerProbability =
          NormalMath.cdf((support.lower() - centers[index]) / bandwidths[index]);
      double probability =
          lowerProbability + random.nextDouble() * StrictMath.exp(logNormalizers[index]);
      probability = Math.max(Math.nextUp(0.0), Math.min(Math.nextDown(1.0), probability));
      coordinate = centers[index] + bandwidths[index] * NormalMath.inverseCdf(probability);
      if (!Double.isFinite(coordinate))
        throw new IllegalArgumentException("Nonfinite truncated normal draw.");
    }
    coordinate = Math.max(support.lower(), Math.min(support.upper(), coordinate));
    if (dimension instanceof IntegerDimension integer) {
      // Binary search transformed cell boundaries. Boundary ties belong to the higher cell.
      long low = 0, high = integer.gridCount();
      while (low + 1 < high) {
        long middle = low + (high - low) / 2;
        double boundary =
            support.transformOffset((double) Math.multiplyExact(middle, integer.step()));
        if (coordinate >= boundary) low = middle;
        else high = middle;
      }
      return integer.lower() + low * integer.step();
    }
    var real = (RealDimension) dimension;
    double value = support.inverse(coordinate);
    if (!Double.isFinite(value))
      throw new IllegalArgumentException("Nonfinite inverse transformed draw.");
    return Math.max(real.lower(), Math.min(real.upper(), value));
  }

  @Override
  public double logProbability(Object value) {
    if (dimension instanceof IntegerDimension integer && integer.gridCount() == 1) return 0;
    var components = new double[centers.length + 1];
    double coordinate = coordinate(value);
    if (dimension instanceof IntegerDimension integer) {
      long index = (((Long) value) - integer.lower()) / integer.step();
      double cellLower = NumericSupport.boundary(integer.lower(), integer.step(), index);
      double lower = support.transformOffset((double) Math.multiplyExact(index, integer.step()));
      double width =
          integer.prior() == NumericPrior.LINEAR
              ? integer.step()
              : StrictMath.log1p(integer.step() / cellLower);
      components[0] =
          integer.prior() == NumericPrior.LINEAR
              ? -StrictMath.log(integer.gridCount())
              : StrictMath.log(width) - StrictMath.log(support.width());
      for (int i = 0; i < centers.length; i++)
        components[i + 1] =
            NormalMath.logInterval((lower - centers[i]) / bandwidths[i], width / bandwidths[i])
                - logNormalizers[i];
    } else {
      components[0] = -StrictMath.log(support.width());
      for (int i = 0; i < centers.length; i++)
        components[i + 1] =
            NormalMath.logDensity((coordinate - centers[i]) / bandwidths[i])
                - StrictMath.log(bandwidths[i])
                - logNormalizers[i];
    }
    double result = NormalMath.logSumExp(components) - StrictMath.log(centers.length + 1.0);
    if (!Double.isFinite(result))
      throw new IllegalArgumentException("Nonfinite numerical likelihood.");
    return result;
  }

  private double coordinate(Object value) {
    if (dimension instanceof IntegerDimension integer)
      return support.transformOffset(((Long) value - integer.lower()) + integer.step() * 0.5);
    return support.transform((Double) value);
  }

  double[] bandwidths() {
    return bandwidths.clone();
  }
}
