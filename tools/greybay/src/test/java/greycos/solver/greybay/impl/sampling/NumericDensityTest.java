package greycos.solver.greybay.impl.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.NumericPrior;
import greycos.solver.greybay.config.RealDimension;

import org.junit.jupiter.api.Test;

class NumericDensityTest {
  @Test
  void halfCellArithmeticRemainsExactNearBinary64IntegerLimit() {
    long lower = (1L << 53) - 9;
    var large =
        new NumericDensity(
            new IntegerDimension("large", lower, lower + 9, 3),
            List.of(lower, lower + 6, lower + 9));
    var translated =
        new NumericDensity(new IntegerDimension("small", 0, 9, 3), List.of(0L, 6L, 9L));
    double total = 0;
    for (long index = 0; index < 4; index++) {
      double mass = StrictMath.exp(large.logProbability(lower + 3 * index));
      assertThat(mass).isEqualTo(StrictMath.exp(translated.logProbability(3 * index)));
      total += mass;
    }
    assertThat(total).isCloseTo(1, within(3e-14));
    var leftRandom = new SplitMix64(71);
    var rightRandom = new SplitMix64(71);
    for (int index = 0; index < 1_000; index++)
      assertThat((Long) large.sample(leftRandom) - lower)
          .isEqualTo((Long) translated.sample(rightRandom));
  }

  @Test
  void narrowLogIntegerSupportUsesOneShiftedCoordinateSystem() {
    long lower = 1_000_000_000_000_000L;
    var dimension = new IntegerDimension("narrow", lower, lower + 80, 8, NumericPrior.LOGARITHMIC);
    var prior = new NumericDensity(dimension, List.of());
    var mixture = new NumericDensity(dimension, List.of(lower, lower + 40, lower + 80));
    double total = 0;
    for (long index = 0; index < 11; index++)
      total += StrictMath.exp(mixture.logProbability(lower + index * 8));
    assertThat(total).isCloseTo(1, within(3e-14));
    var random = new SplitMix64(11);
    int[] counts = new int[11];
    for (int index = 0; index < 55_000; index++)
      counts[(int) (((Long) prior.sample(random) - lower) / 8)]++;
    for (int index = 0; index < counts.length; index++) {
      // Exact log(cellUpper/cellLower)/log(supportUpper/supportLower), using log1p;
      // at this magnitude it differs from 1/11 by less than 1e-14.
      assertThat(counts[index] / 55_000.0).isCloseTo(1.0 / 11, within(0.006));
      assertThat(StrictMath.exp(prior.logProbability(lower + index * 8)))
          .isCloseTo(1.0 / 11, within(1e-14));
    }
  }

  @Test
  void logIntegerMixtureMatchesIndependentArbitraryPrecisionIntegral() {
    var density =
        new NumericDensity(
            new IntegerDimension("x", 1, 5, 1, NumericPrior.LOGARITHMIC), List.of(1L, 3L, 3L, 5L));
    // Independent mpmath 1.3.0, 80 decimal digits, integrated normal CDF differences per cell.
    double[] expected = {
      0.33654868354960719279,
      0.21879132678676193865,
      0.17831035537489395648,
      0.14811955644020106842,
      0.11823007784853584366
    };
    double sum = 0;
    for (int index = 0; index < expected.length; index++) {
      double actual = StrictMath.exp(density.logProbability((long) index + 1));
      assertThat(actual).isCloseTo(expected[index], within(2e-14));
      sum += actual;
    }
    assertThat(sum).isCloseTo(1, within(3e-14));
    var random = new SplitMix64(413);
    int[] counts = new int[5];
    for (int sample = 0; sample < 40_000; sample++)
      counts[((Long) density.sample(random)).intValue() - 1]++;
    for (int index = 0; index < counts.length; index++)
      assertThat(counts[index] / 40_000.0).isCloseTo(expected[index], within(0.009));
  }

  @Test
  void linearCellsAndPriorNormalizeWithoutPointDensityApproximation() {
    var density =
        new NumericDensity(new IntegerDimension("x", -6, 6, 3), List.of(-6L, -6L, 0L, 6L));
    var prior = new NumericDensity(new IntegerDimension("x", -6, 6, 3), List.of());
    double sum = 0;
    for (long value = -6; value <= 6; value += 3) {
      sum += StrictMath.exp(density.logProbability(value));
      assertThat(StrictMath.exp(prior.logProbability(value))).isCloseTo(0.2, within(1e-15));
    }
    assertThat(sum).isCloseTo(1, within(3e-14));
    assertThat(density.bandwidths()[0]).isEqualTo(density.bandwidths()[1]);
  }

  @Test
  void realMixtureIndependentSimpsonQuadratureNormalizes() {
    var density =
        new NumericDensity(new RealDimension("x", -2, 3), List.of(-2.0, -1.1, -1.1, 2.9, 3.0));
    int intervals = 10_000;
    double width = 5.0 / intervals;
    double sum = 0;
    for (int index = 0; index <= intervals; index++) {
      double x = -2 + index * width;
      double weight = index == 0 || index == intervals ? 1 : index % 2 == 0 ? 2 : 4;
      sum += weight * StrictMath.exp(density.logProbability(x));
    }
    assertThat(sum * width / 3).isCloseTo(1, within(2e-12));
  }

  @Test
  void endpointKernelsRemainFinite() {
    var density =
        new NumericDensity(new RealDimension("x", 0, 1), java.util.Collections.nCopies(300, 0.0));
    var random = new SplitMix64(0);
    for (int index = 0; index < 2_000; index++) {
      double value = (Double) density.sample(random);
      assertThat(value).isBetween(0.0, 1.0);
      assertThat(density.logProbability(value)).isFinite();
    }
  }
}
