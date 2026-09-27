package greycos.solver.core.impl.solver.monitoring;

/**
 * Serializes metric publication with the end of a run. A worker from a failed run may finish later,
 * but it must not update or register meters after its run was sealed.
 */
public final class SolverMetricRun {
  private boolean active = true;

  public synchronized boolean publish(Runnable publication) {
    if (!active) {
      return false;
    }
    publication.run();
    return true;
  }

  public synchronized void seal() {
    active = false;
  }

  public synchronized boolean isActive() {
    return active;
  }

  /** Cleanup is permitted after sealing, and cannot race an earlier publication. */
  public synchronized void cleanup(Runnable cleanup) {
    cleanup.run();
  }
}
