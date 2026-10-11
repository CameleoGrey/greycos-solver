package greycos.solver.core.impl.solver.thread;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Tracks workers shared by a solver and its child scopes until they have actually stopped. */
public final class SolverWorkerRegistry {

  private final Map<ExecutorService, String> executors = new IdentityHashMap<>();
  private final Map<Thread, String> threads = new IdentityHashMap<>();

  public synchronized void registerExecutor(ExecutorService executor, String label) {
    pruneTerminated();
    executors.put(Objects.requireNonNull(executor), Objects.requireNonNull(label));
  }

  /** Register before starting the thread; a NEW thread is still an outstanding worker. */
  public synchronized void registerThread(Thread thread, String label) {
    pruneTerminated();
    threads.put(Objects.requireNonNull(thread), Objects.requireNonNull(label));
  }

  /** Removes a thread whose start failed. A live thread must remain registered until it exits. */
  public synchronized void unregisterThread(Thread thread) {
    Objects.requireNonNull(thread);
    if (thread.getState() != Thread.State.NEW && thread.getState() != Thread.State.TERMINATED) {
      throw new IllegalStateException(
          "Cannot unregister worker thread (%s) while its state (%s) is active."
              .formatted(thread.getName(), thread.getState()));
    }
    threads.remove(thread);
    notifyAll();
  }

  /** Returns an immutable, sorted snapshot of groups whose workers have not yet terminated. */
  public synchronized Set<String> activeWorkerLabels() {
    pruneTerminated();
    var groups = new TreeSet<String>();
    groups.addAll(executors.values());
    groups.addAll(threads.values());
    return Collections.unmodifiableSet(groups);
  }

  /**
   * Waits up to the timeout for all registered workers to terminate, without requesting their
   * termination or releasing their ownership. A registered thread that has not started still counts
   * as outstanding. Call only after the owner has stopped launching workers.
   *
   * @return true if no registered workers remain; false if the timeout expired
   * @throws InterruptedException if interrupted while waiting; ownership remains unchanged
   */
  public synchronized boolean awaitQuiescence(Duration timeout) throws InterruptedException {
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("The timeout (%s) must be >= 0.".formatted(timeout));
    }
    final long timeoutNanos;
    try {
      timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException(
          "The timeout (%s) must fit in nanoseconds.".formatted(timeout), overflow);
    }
    var startNanos = System.nanoTime();
    while (true) {
      pruneTerminated();
      if (executors.isEmpty() && threads.isEmpty()) {
        return true;
      }
      var remainingNanos = timeoutNanos - (System.nanoTime() - startNanos);
      if (remainingNanos <= 0) {
        return false;
      }
      // External executors and threads do not notify this registry when they terminate.
      TimeUnit.NANOSECONDS.timedWait(this, Math.min(remainingNanos, 10_000_000L));
    }
  }

  /** Called before a new solve can reset state that surviving workers may still reference. */
  public synchronized void assertNoActiveWorkers() {
    var groups = activeWorkerLabels();
    if (groups.isEmpty()) {
      return;
    }
    throw new IllegalStateException(
        "Cannot start solving while workers from the previous solve have not stopped "
            + "(worker groups: "
            + groups
            + "). Wait for those workers to exit before reusing this solver.");
  }

  private void pruneTerminated() {
    executors.keySet().removeIf(ExecutorService::isTerminated);
    threads.keySet().removeIf(thread -> thread.getState() == Thread.State.TERMINATED);
  }
}
