package greycos.solver.core.impl.heuristic.selector.common.nearby;

import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;

import org.jspecify.annotations.NonNull;

public final class NearbySelectionTuning {

  private NearbySelectionTuning() {
    // Utility class.
  }

  public static int calculateMaxNearbySortSize(@NonNull NearbySelectionConfig config) {
    Integer userSpecified = config.getMaxNearbySortSize();
    if (userSpecified != null) {
      if (userSpecified < 1) {
        throw new IllegalArgumentException(
            "The maxNearbySortSize (%d) must be at least 1.".formatted(userSpecified));
      }
      return userSpecified;
    }
    // Selectors may still bound storage by the distribution's support. An additional implicit
    // cap would change unlimited distributions and remove BLOCK's uniform exploration tail.
    return Integer.MAX_VALUE;
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
}
