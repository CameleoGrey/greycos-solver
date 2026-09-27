package greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.random.RandomGenerator;

import org.jspecify.annotations.NonNull;

/**
 * Block distribution for nearby selection. Selects uniformly from the nearest block, whose size is
 * derived from the full population ratio and constrained by the configured minimum and maximum. The
 * block cannot exceed the available retained destinations.
 */
public final class BlockDistributionNearbyRandom implements NearbyRandom {

  private final int sizeMinimum;
  private final int sizeMaximum;
  private final double sizeRatio;
  private final double uniformDistributionProbability;

  public BlockDistributionNearbyRandom(
      int sizeMinimum, int sizeMaximum, double sizeRatio, double uniformDistributionProbability) {
    this.sizeMinimum = sizeMinimum;
    this.sizeMaximum = sizeMaximum;
    this.sizeRatio = sizeRatio;
    this.uniformDistributionProbability = uniformDistributionProbability;
    if (sizeMinimum < 1) {
      throw new IllegalArgumentException(
          "The sizeMinimum (" + sizeMinimum + ") must be at least 1.");
    }
    if (sizeMaximum < sizeMinimum) {
      throw new IllegalArgumentException(
          "The sizeMaximum ("
              + sizeMaximum
              + ") must be at least the sizeMinimum ("
              + sizeMinimum
              + ").");
    }
    if (!Double.isFinite(sizeRatio) || sizeRatio < 0.0 || sizeRatio > 1.0) {
      throw new IllegalArgumentException(
          "The sizeRatio (" + sizeRatio + ") must be finite and between 0.0 and 1.0.");
    }
    if (!Double.isFinite(uniformDistributionProbability)
        || uniformDistributionProbability < 0.0
        || uniformDistributionProbability > 1.0) {
      throw new IllegalArgumentException(
          "The uniformDistributionProbability ("
              + uniformDistributionProbability
              + ") must be finite and between 0.0 and 1.0.");
    }
  }

  @Override
  public int nextInt(@NonNull RandomGenerator random, int nearbySize) {
    return nextInt(random, nearbySize, nearbySize);
  }

  @Override
  public int nextInt(@NonNull RandomGenerator random, int populationSize, int nearbySize) {
    if (uniformDistributionProbability > 0.0) {
      if (random.nextDouble() < uniformDistributionProbability) {
        return random.nextInt(nearbySize);
      }
    }
    int size;
    if (sizeRatio < 1.0) {
      size = (int) (populationSize * sizeRatio);
      if (size < sizeMinimum) {
        size = sizeMinimum;
      }
    } else {
      size = populationSize;
    }
    if (size > sizeMaximum) {
      size = sizeMaximum;
    }
    size = Math.min(size, nearbySize);
    return random.nextInt(size);
  }

  @Override
  public int getOverallSizeMaximum() {
    if (uniformDistributionProbability > 0.0) {
      return Integer.MAX_VALUE;
    }
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
    BlockDistributionNearbyRandom that = (BlockDistributionNearbyRandom) other;
    return sizeMinimum == that.sizeMinimum
        && sizeMaximum == that.sizeMaximum
        && Double.compare(that.sizeRatio, sizeRatio) == 0
        && Double.compare(that.uniformDistributionProbability, uniformDistributionProbability) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sizeMinimum, sizeMaximum, sizeRatio, uniformDistributionProbability);
  }
}
