package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

import org.jspecify.annotations.NonNull;

/**
 * Parabolic distribution for nearby selection.
 *
 * <p>This distribution strongly favors nearby items while still allowing selection of moderately
 * distant items. It is the recommended default for most nearby selection use cases.
 *
 * <p>Probability density function: {@code P(x) = 3(m - x)²/m³} where x is the distance rank (0, 1,
 * 2, ...) and m is the sizeMaximum.
 *
 * <p>Cumulative distribution function: {@code F(x) = 1 - (1 - x/m)³}
 *
 * <p>Inverse cumulative distribution function: {@code F⁻¹(p) = m(1 - (1 - p)^(1/3))}
 *
 * <p>This gives a strong quadratic preference for nearer items. For example, with sizeMaximum=40:
 *
 * <ul>
 *   <li>Rank 0 (nearest): ~12.3% probability
 *   <li>Rank 10: ~7.7% probability
 *   <li>Rank 20: ~4.6% probability
 *   <li>Rank 30: ~2.3% probability
 *   <li>Rank 39 (farthest): ~0.2% probability
 * </ul>
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
