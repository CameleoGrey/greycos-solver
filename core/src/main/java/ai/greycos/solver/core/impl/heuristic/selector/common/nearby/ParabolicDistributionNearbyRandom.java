package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

import org.jspecify.annotations.NonNull;

/**
 * Parabolic distribution for nearby selection (default recommended). Strongly favors nearby items
 * using quadratic probability curve: P(x) = 3(m - x)²/m³
 */
public final class ParabolicDistributionNearbyRandom implements NearbyRandom {

  private final int sizeMaximum;

  public ParabolicDistributionNearbyRandom(int sizeMaximum) {
    this.sizeMaximum = sizeMaximum;
    if (sizeMaximum < 1) {
      throw new IllegalArgumentException("The maximum (" + sizeMaximum + ") must be at least 1.");
    }
  }

  @Override
  public int nextInt(@NonNull Random random, int nearbySize) {
    int m = sizeMaximum <= nearbySize ? sizeMaximum : nearbySize;
    double p = random.nextDouble();
    double x = m * (1.0 - Math.cbrt(1.0 - p));
    int next = (int) x;
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
    ParabolicDistributionNearbyRandom that = (ParabolicDistributionNearbyRandom) other;
    return sizeMaximum == that.sizeMaximum;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sizeMaximum);
  }
}
