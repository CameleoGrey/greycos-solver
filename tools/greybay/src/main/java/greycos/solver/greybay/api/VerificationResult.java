package greycos.solver.greybay.api;

import java.util.Map;
import java.util.Objects;

/** An immutable independent-verification verdict, distinct from native score recalculation. */
public record VerificationResult(Status status, String message, Map<String, String> metrics) {
  public enum Status {
    PASSED,
    INVALID,
    NOT_CONFIGURED
  }

  public VerificationResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(message, "message");
    metrics = Map.copyOf(metrics);
  }

  public static VerificationResult passed() {
    return new VerificationResult(Status.PASSED, "", Map.of());
  }

  public static VerificationResult invalid(String message) {
    return new VerificationResult(Status.INVALID, message, Map.of());
  }

  public static VerificationResult notConfigured() {
    return new VerificationResult(Status.NOT_CONFIGURED, "", Map.of());
  }
}
