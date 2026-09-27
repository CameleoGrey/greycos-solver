package greycos.solver.core.impl.solver;

/** Marks event delivery threads so shutdown cannot wait on another callback in the same cycle. */
final class SolverEventThreadContext {

  private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

  private SolverEventThreadContext() {}

  static boolean isActive() {
    return Boolean.TRUE.equals(ACTIVE.get());
  }

  static void run(Runnable task) {
    boolean alreadyActive = isActive();
    ACTIVE.set(true);
    try {
      task.run();
    } finally {
      if (!alreadyActive) {
        ACTIVE.remove();
      }
    }
  }
}
