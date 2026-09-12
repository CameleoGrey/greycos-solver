package greycos.solver.core.impl.heuristic.selector.move.generic;

/** Overflow-safe approximate cardinality of the order-sensitive legacy ruin move descriptors. */
public final class RuinRecreateMoveSelectorSize {

  private RuinRecreateMoveSelectorSize() {}

  public static int clampCount(int requestedCount, long eligibleCount) {
    if (requestedCount < 0 || eligibleCount < 0) {
      throw new IllegalArgumentException(
          "Ruin counts and eligible population must be nonnegative.");
    }
    return eligibleCount == 0 ? 0 : (int) Math.min(Math.max(1, requestedCount), eligibleCount);
  }

  /**
   * Counts ordered distinct selections, saturating at {@link Long#MAX_VALUE}. Repair seeds are
   * deliberately not counted, consistently with the move's equality contract.
   */
  public static long count(long eligibleCount, int minimumCount, int maximumCount) {
    if (eligibleCount < 0 || minimumCount < 0 || maximumCount < minimumCount) {
      throw new IllegalArgumentException("Invalid ruin count range or eligible population.");
    }
    if (eligibleCount == 0) {
      return 0;
    }
    int minimum = clampCount(minimumCount, eligibleCount);
    int maximum = clampCount(maximumCount, eligibleCount);
    long permutations = 1;
    long total = 0;
    for (int selected = 1; selected <= maximum; selected++) {
      long factor = eligibleCount - selected + 1;
      if (permutations > Long.MAX_VALUE / factor) {
        return Long.MAX_VALUE;
      }
      permutations *= factor;
      if (selected >= minimum) {
        if (total > Long.MAX_VALUE - permutations) {
          return Long.MAX_VALUE;
        }
        total += permutations;
      }
    }
    return total;
  }
}
