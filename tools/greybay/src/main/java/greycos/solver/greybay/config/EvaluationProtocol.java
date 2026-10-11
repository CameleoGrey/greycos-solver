package greycos.solver.greybay.config;

import java.time.Duration;
import java.util.Objects;

/** Immutable wall-clock protocol. Warmup observations never enter the objective or sampler. */
public record EvaluationProtocol(
    Duration solveDuration,
    Duration warmupDuration,
    long preflightSeed,
    long warmupSeed,
    ResourcePolicy resourcePolicy) {
  public EvaluationProtocol {
    validateDuration(solveDuration, "solveDuration", false);
    validateDuration(warmupDuration, "warmupDuration", true);
    Objects.requireNonNull(resourcePolicy, "resourcePolicy");
    if (preflightSeed == warmupSeed) {
      throw new IllegalArgumentException("preflightSeed and warmupSeed must be distinct.");
    }
  }

  private static void validateDuration(Duration duration, String name, boolean zeroAllowed) {
    Objects.requireNonNull(duration, name);
    try {
      long millis = duration.toMillis();
      long nanos = duration.toNanos();
      if (duration.isNegative() || (!zeroAllowed && millis == 0) || nanos % 1_000_000 != 0) {
        throw new IllegalArgumentException(
            name
                + " must be a "
                + (zeroAllowed ? "nonnegative" : "positive")
                + " whole-millisecond duration.");
      }
      long graceMillis = Math.max(1_000L, millis / 20 + (millis % 20 == 0 ? 0 : 1));
      Math.addExact(nanos, Math.multiplyExact(graceMillis, 1_000_000L));
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          name + " including cleanup grace must fit nanosecond elapsed arithmetic.", e);
    }
  }

  public Duration graceDuration() {
    long millis = solveDuration.toMillis();
    return Duration.ofMillis(Math.max(1_000L, millis / 20 + (millis % 20 == 0 ? 0 : 1)));
  }
}
