package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.random.RandomGenerator;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.jspecify.annotations.NonNull;

/**
 * Beta distribution for nearby selection using Apache Commons Math. Flexible probability curves but
 * slower than other distributions.
 */
public final class BetaDistributionNearbyRandom implements NearbyRandom {

  private final @NonNull BetaDistribution betaDistribution;

  public BetaDistributionNearbyRandom(double alpha, double beta) {
    if (alpha <= 0.0) {
      throw new IllegalArgumentException("The alpha (" + alpha + ") must be positive.");
    }
    if (beta <= 0.0) {
      throw new IllegalArgumentException("The beta (" + beta + ") must be positive.");
    }
    this.betaDistribution = new BetaDistribution(alpha, beta);
  }

  @Override
  public int nextInt(@NonNull RandomGenerator random, int nearbySize) {
    double p = betaDistribution.inverseCumulativeProbability(random.nextDouble());
    double x = nearbySize * p;
    int next = (int) x;
    if (next >= nearbySize) {
      next = nearbySize - 1;
    }
    return next;
  }

  @Override
  public int getOverallSizeMaximum() {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    BetaDistributionNearbyRandom that = (BetaDistributionNearbyRandom) other;
    return Double.compare(betaDistribution.getAlpha(), that.betaDistribution.getAlpha()) == 0
        && Double.compare(betaDistribution.getBeta(), that.betaDistribution.getBeta()) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(betaDistribution.getAlpha(), betaDistribution.getBeta());
  }
}
