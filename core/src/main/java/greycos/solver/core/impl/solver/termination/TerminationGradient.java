package greycos.solver.core.impl.solver.termination;

/** Combines supported gradients without disguising unavailable or invalid cooling schedules. */
final class TerminationGradient {

  private TerminationGradient() {}

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
