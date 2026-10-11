package greycos.solver.greybay.config;

import java.util.Map;
import java.util.Objects;

/** Fixed resource and instrumentation identity shared by every candidate in a study. */
public record ResourcePolicy(
    String moveThreadCount, boolean allowInternalWorkers, Map<String, String> environmentIdentity) {
  public ResourcePolicy {
    Objects.requireNonNull(moveThreadCount, "moveThreadCount");
    if (moveThreadCount.isBlank() || "AUTO".equals(moveThreadCount)) {
      throw new IllegalArgumentException(
          "moveThreadCount must declare a fixed resource envelope; AUTO is unsupported.");
    }
    if (!allowInternalWorkers && !"NONE".equals(moveThreadCount)) {
      throw new IllegalArgumentException(
          "moveThreadCount must be NONE unless internal workers are explicitly allowed.");
    }
    environmentIdentity = Map.copyOf(environmentIdentity);
  }

  public static ResourcePolicy singleThreaded() {
    return new ResourcePolicy("NONE", false, Map.of());
  }
}
