package greycos.solver.core.impl.localsearch.decider;

/** Bounds consecutive consumed candidates without acceptance in a never-ending repository. */
final class LocalSearchCandidateLimit {

  private static final int MAXIMUM_LIMIT = 327_670;
  private static final int SIZE_MULTIPLIER = 10;

  private final int limit;
  private int consecutiveRejectedCount;
  private boolean exhausted;

  LocalSearchCandidateLimit(long estimatedSize) {
    limit =
        estimatedSize < 0
            ? MAXIMUM_LIMIT
            : (int) Math.min(MAXIMUM_LIMIT / SIZE_MULTIPLIER, Math.max(1L, estimatedSize))
                * SIZE_MULTIPLIER;
  }

  boolean record(boolean accepted) {
    if (accepted) {
      consecutiveRejectedCount = 0;
    } else if (consecutiveRejectedCount < limit) {
      consecutiveRejectedCount++;
    }
    exhausted = consecutiveRejectedCount >= limit;
    return exhausted;
  }

  int getLimit() {
    return limit;
  }

  boolean isExhausted() {
    return exhausted;
  }
}
