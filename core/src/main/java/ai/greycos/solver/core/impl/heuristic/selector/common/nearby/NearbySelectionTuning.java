package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;

import org.jspecify.annotations.NonNull;

public final class NearbySelectionTuning {

  private static final int DEFAULT_DISTRIBUTION_SIZE = 40;
  private static final int AUTO_SORT_SIZE_MINIMUM = 1000;
  private static final int AUTO_SORT_SIZE_MULTIPLIER = 10;

  private NearbySelectionTuning() {
    // Utility class.
  }

  public static int calculateMaxNearbySortSize(@NonNull NearbySelectionConfig config) {
    Integer userSpecified = config.getMaxNearbySortSize();
    if (userSpecified != null && userSpecified > 0) {
      return userSpecified;
    }
    int distributionSize = getDistributionSize(config);
    return Math.max(AUTO_SORT_SIZE_MINIMUM, distributionSize * AUTO_SORT_SIZE_MULTIPLIER);
  }

  public static boolean isEagerInitialization(@NonNull NearbySelectionConfig config) {
    return Boolean.TRUE.equals(config.getEagerInitialization());
  }

  public static boolean hasRandomDistributionLimit(@NonNull NearbySelectionConfig config) {
    if (config.getBlockDistributionSizeRatio() != null
        && config.getBlockDistributionSizeRatio() < 1.0) {
      return true;
    }
    if (config.getBlockDistributionSizeMaximum() != null
        || config.getLinearDistributionSizeMaximum() != null
        || config.getParabolicDistributionSizeMaximum() != null) {
      return true;
    }
    return false;
  }

  private static int getDistributionSize(@NonNull NearbySelectionConfig config) {
    NearbySelectionDistributionType distributionType = config.getNearbySelectionDistributionType();
    if (distributionType == null) {
      return DEFAULT_DISTRIBUTION_SIZE;
    }
    return switch (distributionType) {
      case PARABOLIC_DISTRIBUTION ->
          config.getParabolicDistributionSizeMaximum() != null
              ? config.getParabolicDistributionSizeMaximum()
              : DEFAULT_DISTRIBUTION_SIZE;
      case LINEAR_DISTRIBUTION ->
          config.getLinearDistributionSizeMaximum() != null
              ? config.getLinearDistributionSizeMaximum()
              : DEFAULT_DISTRIBUTION_SIZE;
      case BLOCK_DISTRIBUTION ->
          config.getBlockDistributionSizeMaximum() != null
              ? config.getBlockDistributionSizeMaximum()
              : DEFAULT_DISTRIBUTION_SIZE;
      case BETA_DISTRIBUTION -> DEFAULT_DISTRIBUTION_SIZE;
    };
  }
}
