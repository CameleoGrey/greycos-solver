package greycos.solver.core.impl.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Isolates inline future continuations from termination callers and from one another. */
final class ProblemChangeCancellation {

  private final Map<Thread, CompletableFuture<Void>> tasks = new HashMap<>();

  void dispatch(List<CompletableFuture<Void>> changes) {
    var batch = new ArrayList<Thread>(changes.size());
    synchronized (tasks) {
      // Register the entire batch before any task can complete or an explicit close can drain it.
      for (var change : changes) {
        var task =
            Thread.ofVirtual()
                .name("solver-change-cancellation")
                .unstarted(
                    () -> {
                      try {
                        SolverEventThreadContext.run(() -> change.cancel(false));
                      } finally {
                        synchronized (tasks) {
                          tasks.remove(Thread.currentThread());
                          tasks.notifyAll();
                        }
                      }
                    });
        tasks.put(task, change);
        batch.add(task);
      }
    }
    batch.forEach(Thread::start);
  }

  void awaitPublication(long deadlineNanos) throws InterruptedException {
    List<CompletableFuture<Void>> changes;
    synchronized (tasks) {
      changes = List.copyOf(tasks.values());
    }
    awaitPublication(changes, deadlineNanos);
  }

  static void awaitPublication(List<CompletableFuture<Void>> changes, long deadlineNanos)
      throws InterruptedException {
    for (var change : changes) {
      while (!change.isDone()) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
          return;
        }
        // CompletableFuture publishes cancellation before executing inline continuations. Waiting
        // on the task or an additional dependent stage would also wait on application code.
        LockSupport.parkNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(1)));
      }
    }
  }

  static void awaitPublication(List<CompletableFuture<Void>> changes) {
    boolean interrupted = false;
    try {
      for (var change : changes) {
        while (!change.isDone()) {
          interrupted |= Thread.interrupted();
          LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  void awaitCompletion() {
    if (SolverEventThreadContext.isActive()) {
      // A continuation may close this job or another job whose callback depends on this one.
      return;
    }
    boolean interrupted = false;
    try {
      synchronized (tasks) {
        while (!tasks.isEmpty()) {
          try {
            tasks.wait();
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
