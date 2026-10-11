package greycos.solver.greybay.config;

import java.time.Duration;
import java.util.Objects;

/** Operational limits may increase on resume; already consumed work is never reset. */
public record StudyRunLimits(long maximumCandidateTrials, Duration activeTimeLimit) {
  public StudyRunLimits {
    Objects.requireNonNull(activeTimeLimit, "activeTimeLimit");
    if (maximumCandidateTrials < 0 || activeTimeLimit.isNegative() || activeTimeLimit.isZero()) {
      throw new IllegalArgumentException(
          "maximumCandidateTrials must be >= 0 and activeTimeLimit must be positive.");
    }
    try {
      activeTimeLimit.toNanos();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "activeTimeLimit must be representable in nanoseconds.", e);
    }
  }

  public static StudyRunLimits of(long maximumCandidateTrials, Duration activeTimeLimit) {
    return new StudyRunLimits(maximumCandidateTrials, activeTimeLimit);
  }
}
