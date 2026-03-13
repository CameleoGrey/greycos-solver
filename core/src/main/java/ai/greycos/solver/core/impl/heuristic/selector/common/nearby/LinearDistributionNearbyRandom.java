package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.NonNull;

/**
 * Linear distribution for nearby selection. Balanced preference for nearby items with more
 * exploration than parabolic. Probability decreases linearly: P(x) = 2(m - x)/(m²)
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
  public int nextInt(@NonNull RandomGenerator random, int nearbySize) {
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
