package greycos.solver.core.impl.solver.termination;

/** Combines supported gradients without disguising unavailable or invalid cooling schedules. */
final class TerminationGradient {

  private TerminationGradient() {}

  static double ratio(long progress, long limit) {
    // Zero is a valid, already exhausted budget, including when no work has been done yet.
    return limit == 0L ? 1.0 : Math.min(progress / (double) limit, 1.0);
  }

  static double combine(boolean and, double accumulated, double next) {
    // Only -1 is the unsupported sentinel. Preserve other invalid values for the caller to reject.
    if (accumulated != -1.0 && !(accumulated >= 0.0 && accumulated <= 1.0)) {
      return accumulated;
    }
    if (next == -1.0) {
      return accumulated;
    }
    if (!(next >= 0.0 && next <= 1.0) || accumulated == -1.0) {
      return next;
    }
    return and ? Math.min(accumulated, next) : Math.max(accumulated, next);
  }
}
