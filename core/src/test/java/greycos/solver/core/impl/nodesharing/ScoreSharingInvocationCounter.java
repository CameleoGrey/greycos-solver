package greycos.solver.core.impl.nodesharing;

/** Counts predicate evaluations without putting mutable state on a transformed provider. */
public final class ScoreSharingInvocationCounter {

  private static int calls;

  private ScoreSharingInvocationCounter() {}

  public static boolean count(String code) {
    calls++;
    return !code.isEmpty();
  }

  public static void reset() {
    calls = 0;
  }

  public static int calls() {
    return calls;
  }
}
