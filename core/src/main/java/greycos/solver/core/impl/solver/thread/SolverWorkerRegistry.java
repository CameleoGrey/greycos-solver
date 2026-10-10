package greycos.solver.core.impl.solver.thread;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

/** Tracks workers shared by a solver and its child scopes until they have actually stopped. */
public final class SolverWorkerRegistry {

  private final Map<ExecutorService, String> executors = new IdentityHashMap<>();
  private final Map<Thread, String> threads = new IdentityHashMap<>();
  private final Map<Object, DeferredCleanup> cleanupOwners = new IdentityHashMap<>();
  private final ArrayDeque<DeferredCleanup> deferredCleanups = new ArrayDeque<>();
  private boolean cleanupRunning;

  /**
   * Retains an idempotent owner cleanup after worker shutdown failed. The action must retain its
   * old scopes and run before a later solve replaces their director or resets accounting. Repeated
   * registration by the same owner does not duplicate lifecycle or calculation credits.
   */
  public synchronized void deferCleanup(Object owner, Runnable action) {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(action);
    if (!cleanupOwners.containsKey(owner)) {
      var cleanup = new DeferredCleanup(owner, action);
      cleanupOwners.put(owner, cleanup);
      deferredCleanups.addLast(cleanup);
    }
  }

  /**
   * Runs retained cleanup only after all shared workers have exited, before a new solve mutates
   * state.
   */
  public void runDeferredCleanup() {
    synchronized (this) {
      // Shared child registries may legitimately contain sibling workers during normal startup.
      // No deferred owner means there is no cleanup boundary to establish.
      if (cleanupOwners.isEmpty()) return;
      if (cleanupRunning) {
        throw new IllegalStateException(
            "Deferred worker cleanup is already running; retained state cannot be reused yet.");
      }
      cleanupRunning = true;
    }
    try {
      drainDeferredCleanup();
    } finally {
      synchronized (this) {
        cleanupRunning = false;
      }
    }
  }

  private void drainDeferredCleanup() {
    Throwable failure = null;
    while (true) {
      ArrayList<DeferredCleanup> batch;
      synchronized (this) {
        if (deferredCleanups.isEmpty()) break;
        assertNoActiveWorkers();
        batch = new ArrayList<>(deferredCleanups);
        deferredCleanups.clear();
      }
      for (var cleanup : batch) {
        try {
          cleanup.action().run();
          synchronized (this) {
            cleanupOwners.remove(cleanup.owner());
          }
        } catch (RuntimeException | Error exception) {
          synchronized (this) {
            deferredCleanups.addLast(cleanup);
          }
          if (failure == null) failure = exception;
          else if (failure != exception) failure.addSuppressed(exception);
        }
      }
      if (failure != null) break; // Failed actions remain owned for a later explicit retry.
    }
    if (failure instanceof Error error) throw error;
    if (failure != null) throw (RuntimeException) failure;
  }

  private record DeferredCleanup(Object owner, Runnable action) {}

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
