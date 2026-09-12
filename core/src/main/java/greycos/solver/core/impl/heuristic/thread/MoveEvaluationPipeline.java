package greycos.solver.core.impl.heuristic.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.Nullable;

/**
 * A single coordinator publishes candidates; persistent workers claim them individually and publish
 * results directly into bounded slots. Only the coordinator consumes results, in selection order.
 *
 * <p>Two generations of slots allow a worker to evaluate the next step while another worker is
 * still finishing the previous step. Before publishing another step, every worker must acknowledge
 * the current one. That acknowledgement also proves that the older slots have no remaining writers.
 * Each worker has an addressed, single-entry control mailbox; cancelling candidates never cancels a
 * committed step. Volatile publication of candidates, results and acknowledgements establishes the
 * ownership transfers of otherwise thread-confined state.
 */
public final class MoveEvaluationPipeline<Solution_> implements AutoCloseable {

  private static final int WAKE_BATCH_SIZE = 8;

  private final ExecutorService executor;
  private final int workerCount;
  private final Epoch<Solution_>[] epochs;
  private final List<MoveThreadRunner<Solution_, ?>> workers;
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private final Thread coordinator = Thread.currentThread();
  final int phaseIndex;
  final boolean evaluateDoable;
  final boolean assertMoveScoreFromScratch;
  final boolean assertExpectedUndoMoveScore;
  final boolean assertStepScoreFromScratch;
  final boolean assertExpectedStepScore;
  final boolean assertShadowVariablesAreNotStaleAfterStep;
  final boolean diagnosticsEnabled = Boolean.getBoolean("greycos.solver.moveThreadDiagnostics");
  volatile boolean stopping;
  volatile boolean aborting;
  private boolean started;
  private boolean joined;
  private Epoch<Solution_> current;
  private long generated;
  private long consumed;
  private long consumedDoable;
  private long orderedWaitNanos;
  private long replayWaitNanos;
  private long stepCount;
  private volatile boolean waitingForReplay;
  private BooleanSupplier terminationCheck = () -> false;

  public void setTerminationCheck(BooleanSupplier terminationCheck) {
    this.terminationCheck = Objects.requireNonNull(terminationCheck);
  }

  @SuppressWarnings("unchecked")
  public MoveEvaluationPipeline(
      ExecutorService executor,
      int workerCount,
      int capacity,
      int phaseIndex,
      boolean evaluateDoable,
      boolean assertMoveScoreFromScratch,
      boolean assertExpectedUndoMoveScore,
      boolean assertStepScoreFromScratch,
      boolean assertExpectedStepScore,
      boolean assertShadowVariablesAreNotStaleAfterStep) {
    if (workerCount < 1 || capacity < 1) {
      throw new IllegalArgumentException("Worker count and candidate capacity must be positive.");
    }
    this.executor = Objects.requireNonNull(executor);
    this.workerCount = workerCount;
    this.phaseIndex = phaseIndex;
    this.evaluateDoable = evaluateDoable;
    this.assertMoveScoreFromScratch = assertMoveScoreFromScratch;
    this.assertExpectedUndoMoveScore = assertExpectedUndoMoveScore;
    this.assertStepScoreFromScratch = assertStepScoreFromScratch;
    this.assertExpectedStepScore = assertExpectedStepScore;
    this.assertShadowVariablesAreNotStaleAfterStep = assertShadowVariablesAreNotStaleAfterStep;
    epochs = (Epoch<Solution_>[]) new Epoch<?>[] {new Epoch<>(capacity), new Epoch<>(capacity)};
    workers = new ArrayList<>(workerCount);
    current = epochs[0];
    current.reset(0, null, null);
  }

  public void start(InnerScoreDirector<Solution_, ?> parent) {
    if (started) {
      throw new IllegalStateException("Move evaluation pipeline already started.");
    }
    started = true;
    try {
      // Populate the list before any worker can fail and wake/interrupt its peers.
      for (int i = 0; i < workerCount; i++) {
        workers.add(new MoveThreadRunner<>(this, i, parent, current));
      }
      for (var worker : workers) {
        executor.execute(worker);
      }
      awaitApplied(0); // Parent mutation is unsafe until all initial clones exist.
    } catch (RuntimeException | Error e) {
      try {
        abort();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != e) e.addSuppressed(cleanupFailure);
      }
      throw e;
    }
  }

  public void startNextStep(int stepIndex) {
    checkFailure();
    if (!started || stopping || current.stepIndex != stepIndex || current.closed) {
      throw new IllegalStateException("Unexpected move evaluation step (" + stepIndex + ").");
    }
  }

  public void submit(int moveIndex, Move<Solution_> move) {
    checkFailure();
    var epoch = current;
    if (epoch.closed
        || moveIndex != epoch.published
        || epoch.published - epoch.consumed >= epoch.slots.length) {
      throw new IllegalStateException(
          "Invalid or full candidate window at move (" + moveIndex + ").");
    }
    var slot = epoch.slots[moveIndex % epoch.slots.length];
    slot.move = Objects.requireNonNull(move);
    slot.score = null;
    slot.completedIndex = -1;
    epoch.published = moveIndex + 1;
    generated++;
    // Preserve publication order, but amortize native wakeups. Active workers can already claim
    // every published candidate. take() flushes any partial batch before it needs an unfinished
    // result.
    if ((moveIndex + 1) % WAKE_BATCH_SIZE == 0
        && epoch.published - epoch.claimed.get() >= WAKE_BATCH_SIZE) {
      wakeOneWorker();
    }
  }

  /** Notify idle workers of a partial batch, without generating any additional candidates. */
  public void flush() {
    var epoch = current;
    int remaining = epoch.closed ? 0 : epoch.published - epoch.claimed.get();
    for (var worker : workers) {
      if (remaining <= 0) {
        break;
      }
      if (worker.waiting) {
        worker.waiting = false;
        LockSupport.unpark(worker.thread);
        remaining--;
      }
    }
  }

  /** Returns the next ordered result, or null when termination is requested while waiting. */
  public Result<Solution_> take() throws InterruptedException {
    var epoch = current;
    int moveIndex = epoch.consumed;
    if (moveIndex >= epoch.published) {
      throw new IllegalStateException("No submitted result at move (" + moveIndex + ").");
    }
    var slot = epoch.slots[moveIndex % epoch.slots.length];
    long waitStart = diagnosticsEnabled ? System.nanoTime() : 0;
    try {
      while (true) {
        checkFailure();
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("Interrupted while waiting for a move result.");
        }
        if (slot.completedIndex == moveIndex) {
          break;
        }
        flush();
        // Short CPU-bound evaluations often finish before a native park/unpark round trip.
        for (int spin = 0; spin < 128 && slot.completedIndex != moveIndex; spin++) {
          Thread.onSpinWait();
        }
        epoch.waitingMoveIndex = moveIndex;
        // Register, recheck, then park. A publication between the check and park leaves a permit.
        if (slot.completedIndex != moveIndex && failure.get() == null) {
          LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(50));
          // The decider checks termination after consuming a result. Only poll here when a
          // wait returns without one, so healthy evaluations do not incur a second clock read.
          if (slot.completedIndex != moveIndex
              && failure.get() == null
              && !Thread.currentThread().isInterrupted()
              && terminationCheck.getAsBoolean()) {
            return null;
          }
        }
      }
    } finally {
      epoch.waitingMoveIndex = -1;
      if (diagnosticsEnabled) {
        orderedWaitNanos += System.nanoTime() - waitStart;
      }
    }
    var result = new Result<>(epoch.stepIndex, moveIndex, slot.move, slot.score);
    slot.move = null;
    slot.score = null;
    epoch.consumed++;
    consumed++;
    if (result.isMoveDoable()) {
      consumedDoable++;
    }
    return result;
  }

  public void cancelStep() {
    current.closed = true;
  }

  public void applyStep(int nextStepIndex, Move<Solution_> move, InnerScore<?> score) {
    applyState(nextStepIndex, move, Objects.requireNonNull(score));
  }

  /**
   * Replays a balanced state delta before evaluating the next epoch. ALNS may not have scored its
   * partial state on the coordinator; in that case each worker calculates its own replay baseline.
   * This does not add score calculations to the coordinator's logical termination budget.
   */
  public void applyState(int nextStepIndex, Move<Solution_> move, @Nullable InnerScore<?> score) {
    if (nextStepIndex != current.stepIndex + 1) {
      throw new IllegalStateException("Step updates must be consecutive.");
    }
    cancelStep();
    // All workers must have processed the previous mailbox before it can be overwritten.
    awaitApplied(current.stepIndex);
    var next = epochs[nextStepIndex & 1];
    next.reset(nextStepIndex, Objects.requireNonNull(move), score);
    current = next;
    stepCount++;
    for (var worker : workers) {
      worker.mailbox = next;
      if (worker.waiting) {
        worker.waiting = false;
        LockSupport.unpark(worker.thread);
      }
    }
  }

  private void awaitApplied(int stepIndex) {
    long waitStart = diagnosticsEnabled ? System.nanoTime() : 0;
    try {
      for (var worker : workers) {
        while (worker.appliedStepIndex < stepIndex) {
          checkFailure();
          if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException(
                "Interrupted while waiting for move worker setup/replay.",
                new InterruptedException("Move worker setup/replay interrupted."));
          }
          waitingForReplay = true;
          if (worker.appliedStepIndex < stepIndex && failure.get() == null) {
            LockSupport.park(this);
          }
          waitingForReplay = false;
        }
      }
      checkFailure();
    } finally {
      waitingForReplay = false;
      if (diagnosticsEnabled) {
        replayWaitNanos += System.nanoTime() - waitStart;
      }
    }
  }

  private void wakeOneWorker() {
    // Prefer an already-warm subset when the producer cannot feed all workers. As the backlog
    // grows, busy workers are skipped and more workers are notified.
    for (var worker : workers) {
      if (worker.waiting) {
        // Reserve this wakeup. Until the worker actually runs, further publications should
        // wake other idle workers, rather than repeatedly unparking the same thread.
        worker.waiting = false;
        LockSupport.unpark(worker.thread);
        return;
      }
    }
  }

  void acknowledge() {
    if (waitingForReplay) {
      LockSupport.unpark(coordinator);
    }
  }

  void resultPublished(Epoch<Solution_> epoch, int moveIndex) {
    if (epoch.waitingMoveIndex == moveIndex) {
      LockSupport.unpark(coordinator);
    }
  }

  void fail(int workerIndex, Throwable cause) {
    failure.compareAndSet(
        null,
        new IllegalStateException("Move thread (" + workerIndex + ") threw exception.", cause));
    aborting = true;
    stopping = true;
    LockSupport.unpark(coordinator);
    for (var worker : workers) {
      var thread = worker.thread;
      if (thread != null && thread != Thread.currentThread()) {
        thread.interrupt();
        LockSupport.unpark(thread);
      }
    }
  }

  private void checkFailure() {
    var cause = failure.get();
    if (cause != null) {
      throw (IllegalStateException) cause;
    }
  }

  @Override
  public void close() {
    if (joined) {
      checkFailure();
      return;
    }
    cancelStep();
    stopping = true;
    for (var worker : workers) {
      LockSupport.unpark(worker.thread);
    }
    join(false);
    checkFailure();
  }

  /** Cancel after an error without replacing the original solver exception. */
  public void abort() {
    aborting = true;
    stopping = true;
    cancelStep();
    for (var worker : workers) {
      var thread = worker.thread;
      if (thread != null) {
        thread.interrupt();
        LockSupport.unpark(thread);
      }
    }
    join(true);
  }

  private void join(boolean force) {
    if (joined) {
      return;
    }
    boolean interrupted = Thread.interrupted();
    if (force || interrupted || failure.get() != null) {
      aborting = true;
      executor.shutdownNow();
    } else {
      executor.shutdown();
    }
    long timeout = TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
    long deadline = System.nanoTime() + timeout;
    boolean terminated = false;
    try {
      while (!terminated) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          break;
        }
        try {
          // Notice failures during graceful shutdown without waiting for the entire timeout.
          terminated =
              executor.awaitTermination(
                  Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
          if (failure.get() != null) {
            executor.shutdownNow();
          }
        } catch (InterruptedException e) {
          interrupted = true;
          aborting = true;
          executor.shutdownNow();
        }
      }
      if (!terminated) {
        aborting = true;
        executor.shutdownNow();
        try {
          terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
        }
        failure.compareAndSet(
            null, new IllegalStateException("Move workers did not terminate after cancellation."));
      }
    } finally {
      joined = terminated;
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public long getCalculationCount() {
    return workers.stream().mapToLong(worker -> worker.calculationCount).sum();
  }

  /**
   * Final counters are exact after close; timings are enabled by the diagnostic system property.
   */
  public Diagnostics getDiagnostics() {
    long evaluated = 0,
        scored = 0,
        rebaseNanos = 0,
        evaluationNanos = 0,
        replayNanos = 0,
        idleNanos = 0;
    long samples = 0;
    for (var worker : workers) {
      evaluated += worker.evaluated;
      scored += worker.scored;
      samples += worker.samples;
      rebaseNanos += worker.rebaseNanos;
      evaluationNanos += worker.evaluationNanos;
      replayNanos += worker.replayNanos;
      idleNanos += worker.idleNanos;
    }
    return new Diagnostics(
        generated,
        evaluated,
        scored,
        consumed,
        consumedDoable,
        evaluated - consumed,
        stepCount,
        samples,
        rebaseNanos,
        evaluationNanos,
        replayNanos,
        idleNanos,
        orderedWaitNanos,
        replayWaitNanos);
  }

  public record Diagnostics(
      long generated,
      long evaluated,
      long scored,
      long consumed,
      long consumedDoable,
      long discarded,
      long steps,
      long samples,
      long sampledRebaseNanos,
      long sampledEvaluationNanos,
      long replayNanos,
      long idleNanos,
      long orderedWaitNanos,
      long replayWaitNanos) {}

  public record Result<Solution_>(
      int stepIndex, int moveIndex, Move<Solution_> move, InnerScore<?> score) {
    public boolean isMoveDoable() {
      return score != null;
    }
  }

  static final class Slot<Solution_> {
    Move<Solution_> move;
    InnerScore<?> score;
    volatile int completedIndex = -1;
  }

  static final class Epoch<Solution_> {
    final Slot<Solution_>[] slots;
    final AtomicInteger claimed = new AtomicInteger();
    int stepIndex;
    Move<Solution_> step;
    InnerScore<?> stepScore;
    int consumed;
    volatile int published;
    volatile boolean closed;
    volatile int waitingMoveIndex = -1;

    @SuppressWarnings("unchecked")
    Epoch(int capacity) {
      slots = (Slot<Solution_>[]) new Slot<?>[capacity];
      for (int i = 0; i < capacity; i++) {
        slots[i] = new Slot<>();
      }
    }

    void reset(int index, Move<Solution_> step, InnerScore<?> score) {
      for (var slot : slots) {
        slot.move = null;
        slot.score = null;
        slot.completedIndex = -1;
      }
      stepIndex = index;
      this.step = step;
      stepScore = score;
      consumed = 0;
      published = 0;
      claimed.set(0);
      waitingMoveIndex = -1;
      closed = false;
    }

    int claim() {
      while (!closed) {
        int index = claimed.get();
        if (index >= published) {
          return -1;
        }
        if (claimed.compareAndSet(index, index + 1)) {
          return index;
        }
      }
      return -1;
    }

    boolean hasWork() {
      return !closed && claimed.get() < published;
    }
  }
}
