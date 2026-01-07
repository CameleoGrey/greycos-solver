package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

import org.jspecify.annotations.NonNull;

/**
 * Linear distribution for nearby selection.
 *
 * <p>This distribution provides a balanced preference for nearby items with more exploration than
 * parabolic distribution. It linearly decreases the probability as distance increases.
 *
 * <p>Probability density function: {@code P(x) = 2(m - x)/(m²)} where x is the distance rank (0, 1,
 * 2, ...) and m is the sizeMaximum.
 *
 * <p>Cumulative distribution function: {@code F(x) = x(2m - x)/m²}
 *
 * <p>Inverse cumulative distribution function: {@code F⁻¹(p) = m(1 - √(1 - p))}
 *
 * <p>This gives a linear preference for nearer items. For example, with sizeMaximum=40:
 *
 * <ul>
 *   <li>Rank 0 (nearest): ~5.0% probability
 *   <li>Rank 10: ~3.75% probability
 *   <li>Rank 20: ~2.5% probability
 *   <li>Rank 30: ~1.25% probability
 *   <li>Rank 39 (farthest): ~0.13% probability
 * </ul>
 */
public final class LinearDistributionNearbyRandom implements NearbyRandom {

  private final int sizeMaximum;

  public LinearDistributionNearbyRandom(int sizeMaximum) {
    this.sizeMaximum = sizeMaximum;
    if (sizeMaximum < 1) {
      throw new IllegalArgumentException("The maximum (" + sizeMaximum + ") must be at least 1.");
    }
  }

  @Override
  public int nextInt(@NonNull Random random, int nearbySize) {
    int m = sizeMaximum <= nearbySize ? sizeMaximum : nearbySize;
    double p = random.nextDouble();
    double x = m * (1.0 - Math.sqrt(1.0 - p));
    int next = (int) x;
    // Due to a rounding error it might return m
    if (next >= m) {
      next = m - 1;
    }
    return next;
  }

  @Override
  public int getOverallSizeMaximum() {
    return sizeMaximum;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    LinearDistributionNearbyRandom that = (LinearDistributionNearbyRandom) other;
    return sizeMaximum == that.sizeMaximum;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sizeMaximum);
  }
}
