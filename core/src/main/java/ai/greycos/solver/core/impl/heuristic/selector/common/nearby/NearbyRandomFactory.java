package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;

import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Factory for creating NearbyRandom distribution instances from configuration.
 * Supports block, linear, parabolic, and beta distributions.
 */
public class NearbyRandomFactory {

  private final @NonNull NearbySelectionConfig nearbySelectionConfig;

  private NearbyRandomFactory(@NonNull NearbySelectionConfig nearbySelectionConfig) {
    this.nearbySelectionConfig = nearbySelectionConfig;
  }

  public static @NonNull NearbyRandomFactory create(
      @NonNull NearbySelectionConfig nearbySelectionConfig) {
    return new NearbyRandomFactory(nearbySelectionConfig);
  }

  public @Nullable NearbyRandom buildNearbyRandom(boolean randomSelection) {
    boolean blockDistributionEnabled =
        nearbySelectionConfig.getNearbySelectionDistributionType()
                == NearbySelectionDistributionType.BLOCK_DISTRIBUTION
            || nearbySelectionConfig.getBlockDistributionSizeMinimum() != null
            || nearbySelectionConfig.getBlockDistributionSizeMaximum() != null
            || nearbySelectionConfig.getBlockDistributionSizeRatio() != null
            || nearbySelectionConfig.getBlockDistributionUniformDistributionProbability() != null;
    boolean linearDistributionEnabled =
        nearbySelectionConfig.getNearbySelectionDistributionType()
                == NearbySelectionDistributionType.LINEAR_DISTRIBUTION
            || nearbySelectionConfig.getLinearDistributionSizeMaximum() != null;
    boolean parabolicDistributionEnabled =
        nearbySelectionConfig.getNearbySelectionDistributionType()
                == NearbySelectionDistributionType.PARABOLIC_DISTRIBUTION
            || nearbySelectionConfig.getParabolicDistributionSizeMaximum() != null;
    boolean betaDistributionEnabled =
        nearbySelectionConfig.getNearbySelectionDistributionType()
                == NearbySelectionDistributionType.BETA_DISTRIBUTION
            || nearbySelectionConfig.getBetaDistributionAlpha() != null
            || nearbySelectionConfig.getBetaDistributionBeta() != null;

    if (!randomSelection) {
      if (blockDistributionEnabled
          || linearDistributionEnabled
          || parabolicDistributionEnabled
          || betaDistributionEnabled) {
        throw new IllegalArgumentException(
            "The nearbySelectorConfig ("
                + nearbySelectionConfig
                + ") with randomSelection ("
                + randomSelection
                + ") has distribution parameters.");
      }
      return null;
    }

    // Validate only one distribution is enabled
    int enabledCount = 0;
    if (blockDistributionEnabled) {
      enabledCount++;
    }
    if (linearDistributionEnabled) {
      enabledCount++;
    }
    if (parabolicDistributionEnabled) {
      enabledCount++;
    }
    if (betaDistributionEnabled) {
      enabledCount++;
    }
    if (enabledCount > 1) {
      throw new IllegalArgumentException(
          "The nearbySelectorConfig ("
              + nearbySelectionConfig
              + ") has multiple distribution types enabled. Only one is allowed.");
    }

    if (blockDistributionEnabled) {
      int sizeMinimum =
          Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionSizeMinimum(), 1);
      int sizeMaximum =
          Objects.requireNonNullElse(
              nearbySelectionConfig.getBlockDistributionSizeMaximum(), Integer.MAX_VALUE);
      double sizeRatio =
          Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionSizeRatio(), 1.0);
      double uniformDistributionProbability =
          Objects.requireNonNullElse(
              nearbySelectionConfig.getBlockDistributionUniformDistributionProbability(), 0.0);
      return new BlockDistributionNearbyRandom(
          sizeMinimum, sizeMaximum, sizeRatio, uniformDistributionProbability);
    } else if (linearDistributionEnabled) {
      int sizeMaximum =
          Objects.requireNonNullElse(
              nearbySelectionConfig.getLinearDistributionSizeMaximum(), Integer.MAX_VALUE);
      return new LinearDistributionNearbyRandom(sizeMaximum);
    } else if (parabolicDistributionEnabled) {
      int sizeMaximum =
          Objects.requireNonNullElse(
              nearbySelectionConfig.getParabolicDistributionSizeMaximum(), Integer.MAX_VALUE);
      return new ParabolicDistributionNearbyRandom(sizeMaximum);
    } else if (betaDistributionEnabled) {
      double alpha =
          Objects.requireNonNullElse(nearbySelectionConfig.getBetaDistributionAlpha(), 1.0);
      double beta =
          Objects.requireNonNullElse(nearbySelectionConfig.getBetaDistributionBeta(), 5.0);
      return new BetaDistributionNearbyRandom(alpha, beta);
    } else {
      // Default to linear distribution with no size limit
      return new LinearDistributionNearbyRandom(Integer.MAX_VALUE);
    }
  }
}
