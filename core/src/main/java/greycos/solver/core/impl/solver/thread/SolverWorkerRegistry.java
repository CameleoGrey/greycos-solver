package greycos.solver.core.impl.solver.thread;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

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
  }

  /** Called before a new solve can reset state that surviving workers may still reference. */
  public synchronized void assertNoActiveWorkers() {
    pruneTerminated();
    if (executors.isEmpty() && threads.isEmpty()) {
      return;
    }
    var groups = new TreeSet<String>();
    groups.addAll(executors.values());
    groups.addAll(threads.values());
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
