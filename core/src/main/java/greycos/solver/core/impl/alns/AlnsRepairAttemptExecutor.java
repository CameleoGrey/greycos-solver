package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.Nullable;

/**
 * Independent randomized repairs of one destroyed baseline. Working state stays private to a
 * persistent worker; only the coordinator credits query prefixes and chooses a complete candidate.
 */
final class AlnsRepairAttemptExecutor<Solution_, Score_ extends Score<Score_>>
    implements AutoCloseable {

  private static final long WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
  private final InnerScoreDirector<Solution_, Score_> parent;
  private final boolean synchronous;
  private final int concurrency;
  private final int attemptCount;
  private final ThreadFactory threadFactory;
  private final EnvironmentMode environmentMode;
  private final Thread coordinator = Thread.currentThread();
  private final List<Worker> workers = new ArrayList<>();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private volatile boolean closed;
  private boolean started;
  private long baselineVersion;
  private long transferredCalculationCount;
  private long attemptsStarted;
  private long attemptsCompleted;
  private long attemptsIncomplete;
  private long attemptsDiscarded;
  private int selectedAttemptIndex = -1;
  private @Nullable Control activeControl;

  AlnsRepairAttemptExecutor(
      InnerScoreDirector<Solution_, Score_> parent,
      @Nullable Integer workerCount,
      ThreadFactory threadFactory,
      EnvironmentMode environmentMode,
      int attemptCount) {
    if (attemptCount < 2 || workerCount != null && workerCount < 1) {
      throw new IllegalArgumentException(
          "Repair attempts require at least two attempts and positive workers.");
    }
    this.parent = Objects.requireNonNull(parent);
    synchronous = workerCount == null;
    concurrency = synchronous ? 1 : Math.min(workerCount, attemptCount);
    this.attemptCount = attemptCount;
    this.threadFactory = Objects.requireNonNull(threadFactory);
    this.environmentMode = Objects.requireNonNull(environmentMode);
  }

  /** Returns null when every attempt failed to produce a complete candidate. */
  @Nullable Result<Solution_, Score_> evaluate(
      DefaultAlnsContext<Solution_, Score_> parentContext,
      List<AlnsTarget<Solution_>> pending,
      AlnsRepairOperatorConfig repairConfig,
      long[] seeds) {
    requireOpen();
    if (seeds.length != attemptCount) {
      throw new IllegalArgumentException("Supply exactly one seed per configured repair attempt.");
    }
    if (repairConfig.getCustomClass() != null
        || repairConfig.getType() != AlnsRepairOperatorType.RANDOMIZED_GREEDY
        || Objects.requireNonNullElse(repairConfig.getTopK(), 3) < 1) {
      throw new IllegalArgumentException(
          "Repair attempts support built-in RANDOMIZED_GREEDY with positive topK.");
    }
    parentContext.checkTerminationNow();
    var replay = parentContext.drainReplayJournal();
    long version = Math.incrementExact(baselineVersion);
    baselineVersion = version;
    var control = new Control();
    activeControl = control;
    selectedAttemptIndex = -1;
    var tasks = new ArrayList<Task>(concurrency);
    var copiedPending = List.copyOf(pending);
    var copiedConfig = repairConfig.copyConfig();
    Result<Solution_, Score_> best = null;
    try {
      startIfNeeded(parentContext, version);
      for (int index = 0; index < concurrency; index++) {
        var task =
            new Task(
                index,
                seeds[index],
                version,
                replay,
                copiedPending,
                copiedConfig,
                control,
                parentContext);
        tasks.add(task);
        dispatch(workers.get(index), task);
      }
      for (int index = 0; index < attemptCount; index++) {
        int slot = index % concurrency;
        var task = tasks.get(slot);
        consume(task, parentContext);
        // This boundary also applies when the final query reaches a limit and the winner has no
        // remaining mutation through which the coordinator would otherwise check termination.
        parentContext.checkTerminationNow();
        if (task.result != null) {
          attemptsCompleted++;
          if (best == null || task.result.score().compareTo(best.score()) > 0) {
            best = task.result;
          }
        } else {
          attemptsIncomplete++;
        }
        task.accounted = true;
        task.result = null;
        int next = index + concurrency;
        if (next < attemptCount) {
          var replacement =
              new Task(
                  next,
                  seeds[next],
                  version,
                  replay,
                  copiedPending,
                  copiedConfig,
                  control,
                  parentContext);
          tasks.set(slot, replacement);
          dispatch(workers.get(slot), replacement);
        }
      }
      if (best != null) selectedAttemptIndex = best.attemptIndex();
      return best;
    } catch (RuntimeException | Error original) {
      control.cancelled = true;
      try {
        if (failure.get() != null || Thread.currentThread().isInterrupted()) {
          abort();
        } else {
          awaitCancelled(tasks);
        }
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != original) original.addSuppressed(cleanupFailure);
      }
      for (var task : tasks) {
        if (!task.accounted) attemptsDiscarded++;
        task.result = null;
      }
      throw original;
    } finally {
      activeControl = null;
    }
  }

  private void startIfNeeded(DefaultAlnsContext<Solution_, Score_> parentContext, long version) {
    if (started) return;
    started = true;
    try {
      for (int index = 0; index < concurrency; index++) workers.add(new Worker(index, version));
      for (var worker : workers) {
        if (synchronous) {
          worker.initialize();
        } else {
          worker.thread =
              Objects.requireNonNull(
                  threadFactory.newThread(worker), "Repair-attempt thread factory returned null.");
          worker.thread.start();
        }
      }
      AlnsTerminationException cancelled = null;
      for (var worker : workers) {
        while (!worker.ready) {
          checkFailure();
          if (Thread.currentThread().isInterrupted()) throw new AlnsTerminationException();
          if (cancelled == null) {
            try {
              parentContext.checkTerminationNow();
            } catch (AlnsTerminationException termination) {
              if (Thread.currentThread().isInterrupted()) throw termination;
              cancelled = termination;
            }
          }
          LockSupport.parkNanos(this, WAIT_NANOS);
        }
      }
      checkFailure();
      if (cancelled != null) throw cancelled;
      parentContext.checkTerminationNow();
    } catch (RuntimeException | Error original) {
      // A cooperative budget expiry must still let all clones observe the immutable baseline.
      // Once ready, those same workers can replay the rollback and serve the following trial.
      if (original instanceof AlnsTerminationException
          && !Thread.currentThread().isInterrupted()
          && failure.get() == null
          && workers.stream().allMatch(worker -> worker.ready)) {
        throw original;
      }
      try {
        abort();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != original) original.addSuppressed(cleanupFailure);
      }
      throw original;
    }
  }

  private void dispatch(Worker worker, Task task) {
    attemptsStarted++;
    worker.mailbox = task;
    if (synchronous) {
      worker.execute(task);
    } else {
      LockSupport.unpark(worker.thread);
    }
  }

  private void consume(Task task, DefaultAlnsContext<Solution_, Score_> parentContext) {
    while (true) {
      checkFailure();
      var progress = task.progress;
      while (task.credited < progress.total()) {
        if ((task.credited & 31) == 0) checkFailure();
        parentContext.checkTermination();
        parentContext.creditQuery(progress.isProbe(task.credited));
        task.credited++;
        transferredCalculationCount++;
      }
      if (task.done) {
        // Acquire completion before reading the final progress publication.
        if (task.credited != task.progress.total()) continue;
        return;
      }
      parentContext.checkTerminationNow();
      LockSupport.parkNanos(this, WAIT_NANOS);
    }
  }

  private void awaitCancelled(List<Task> tasks) {
    if (synchronous) return;
    boolean interrupted = Thread.interrupted();
    long deadline =
        System.nanoTime() + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
    try {
      for (var worker : workers) LockSupport.unpark(worker.thread);
      for (var task : tasks) {
        while (!task.done) {
          if (failure.get() != null) {
            abort();
            return;
          }
          if (System.nanoTime() >= deadline) {
            abort();
            throw new IllegalStateException("Repair attempts did not finish cancellation.");
          }
          LockSupport.parkNanos(this, WAIT_NANOS);
          if (Thread.interrupted()) interrupted = true;
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private void checkFailure() {
    var cause = failure.get();
    if (cause != null) throw new IllegalStateException("Repair-attempt worker failed.", cause);
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("Repair-attempt executor is closed.");
    checkFailure();
  }

  long getAdditionalCalculationCount() {
    long physical = 0;
    for (var worker : workers) physical += worker.calculationCount;
    return physical - transferredCalculationCount;
  }

  Diagnostics getDiagnostics() {
    return new Diagnostics(
        attemptsStarted,
        attemptsCompleted,
        attemptsIncomplete,
        attemptsDiscarded,
        selectedAttemptIndex,
        transferredCalculationCount,
        workers.size());
  }

  void abort() {
    stop(true);
  }

  @Override
  public void close() {
    stop(false);
    checkFailure();
  }

  private void stop(boolean interruptWorkers) {
    closed = true;
    if (activeControl != null) activeControl.cancelled = true;
    for (var worker : workers) {
      if (worker.thread != null) {
        if (interruptWorkers) worker.thread.interrupt();
        LockSupport.unpark(worker.thread);
      }
    }
    boolean interrupted = Thread.interrupted();
    long deadline =
        System.nanoTime() + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
    try {
      for (var worker : workers) {
        if (synchronous) {
          worker.release();
          continue;
        }
        if (worker.thread == null) continue;
        while (worker.thread.isAlive()) {
          long remaining = deadline - System.nanoTime();
          if (remaining <= 0) {
            worker.thread.interrupt();
            throw new IllegalStateException("Repair-attempt worker did not terminate.");
          }
          try {
            TimeUnit.NANOSECONDS.timedJoin(worker.thread, Math.min(remaining, WAIT_NANOS));
          } catch (InterruptedException ignored) {
            interrupted = true;
            for (var peer : workers) if (peer.thread != null) peer.thread.interrupt();
          }
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  record Result<S, Sc extends Score<Sc>>(
      int attemptIndex, InnerScore<Sc> score, List<Move<S>> journal) {}

  record Diagnostics(
      long started,
      long completed,
      long incomplete,
      long discarded,
      int selectedAttemptIndex,
      long creditedQueries,
      int workingCopies) {}

  private static final class Control {
    volatile boolean cancelled;
  }

  private record Run(@Nullable Run previous, long start, long length, boolean probe) {}

  private record Progress(@Nullable Run previous, long start, long length, boolean probe) {
    long total() {
      return start + length;
    }

    boolean isProbe(long index) {
      if (index >= start) return probe;
      for (var run = previous; run != null; run = run.previous()) {
        if (index >= run.start()) return run.probe();
      }
      throw new IllegalStateException("Missing repair-attempt query prefix.");
    }
  }

  private final class Task {
    final int index;
    final long seed;
    final long version;
    final List<Move<Solution_>> replay;
    final List<AlnsTarget<Solution_>> pending;
    final AlnsRepairOperatorConfig repairConfig;
    final Control control;
    final DefaultAlnsContext<Solution_, Score_> parentContext;
    final long localQueryLimit;
    volatile Progress progress = new Progress(null, 0, 0, false);
    volatile boolean done;
    @Nullable Result<Solution_, Score_> result;
    long credited;
    boolean accounted;
    @Nullable Run previous;
    long runStart;
    long runLength;
    boolean runProbe;

    Task(
        int index,
        long seed,
        long version,
        List<Move<Solution_>> replay,
        List<AlnsTarget<Solution_>> pending,
        AlnsRepairOperatorConfig repairConfig,
        Control control,
        DefaultAlnsContext<Solution_, Score_> parentContext) {
      this.index = index;
      this.seed = seed;
      this.version = version;
      this.replay = replay;
      this.pending = pending;
      this.repairConfig = repairConfig;
      this.control = control;
      this.parentContext = parentContext;
      localQueryLimit = Math.max(0, parentContext.remainingRepairQueryAllowance());
    }

    void query(boolean probe) {
      if (runLength != 0 && runProbe != probe) {
        previous = new Run(previous, runStart, runLength, runProbe);
        runStart += runLength;
        runLength = 0;
      }
      runProbe = probe;
      runLength++;
      if (synchronous) {
        parentContext.checkTermination();
        parentContext.creditQuery(probe);
        credited++;
        transferredCalculationCount++;
      }
      if (!probe || ((runStart + runLength) & 31) == 0) publish();
    }

    void publish() {
      progress = new Progress(previous, runStart, runLength, runProbe);
      if (!synchronous) LockSupport.unpark(coordinator);
    }

    boolean localLimitReached() {
      return runStart + runLength >= localQueryLimit;
    }
  }

  private final class Worker implements Runnable {
    final int index;
    final Random random = new Random();
    long appliedVersion;
    volatile boolean ready;
    volatile @Nullable Task mailbox;
    @Nullable Thread thread;
    @Nullable InnerScoreDirector<Solution_, Score_> director;
    @Nullable DefaultAlnsContext<Solution_, Score_> context;
    @Nullable InnerScore<Score_> baselineScore;
    @Nullable Task activeTask;
    long calculationCount;
    boolean released;

    Worker(int index, long initialVersion) {
      this.index = index;
      appliedVersion = initialVersion;
    }

    void initialize() {
      director = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      baselineScore = director.calculateScore();
      context = new DefaultAlnsContext<>(director, random, this::terminated);
      if (synchronous) {
        context.configureTermination(
            () -> {},
            () -> {
              if (activeTask != null) activeTask.parentContext.invalidateTermination();
            },
            () -> {
              if (activeTask != null) activeTask.parentContext.checkTerminationNow();
              return terminated();
            });
      }
      context.enableReplayRecording();
      ready = true;
      LockSupport.unpark(coordinator);
    }

    boolean terminated() {
      if (closed || activeTask != null && activeTask.control.cancelled) return true;
      if (activeTask != null && activeTask.localLimitReached()) return true;
      if (synchronous && activeTask != null) activeTask.parentContext.checkTermination();
      return false;
    }

    @Override
    public void run() {
      try {
        initialize();
        Task previousTask = null;
        while (!closed) {
          if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException(
                "Repair-attempt worker interrupted outside cancellation.");
          }
          var task = mailbox;
          if (task != null && task != previousTask) {
            execute(task);
            previousTask = task;
          } else {
            LockSupport.park(AlnsRepairAttemptExecutor.this);
          }
        }
      } catch (Throwable original) {
        fail(original);
      } finally {
        release();
      }
    }

    void execute(Task task) {
      activeTask = task;
      try {
        if (appliedVersion != task.version) {
          if (appliedVersion + 1 != task.version) {
            throw new IllegalStateException("Repair worker missed a baseline replay.");
          }
          if (!task.replay.isEmpty()) {
            director.executeMove(
                AlnsPrimitiveMove.composite(task.replay).rebase(director.getMoveDirector()));
          }
          baselineScore = director.calculateScore();
          appliedVersion = task.version;
        }
        // Even a cancelled attempt acknowledges its baseline delta before the next trial can reuse
        // it.
        if (terminated()) throw new AlnsTerminationException();
        var lookup = director.getMoveDirector();
        var targets = new ArrayList<AlnsTarget<Solution_>>(task.pending.size());
        for (var target : task.pending) {
          targets.add(
              new AlnsTarget<>(
                  target.variable(),
                  target.entity() == null
                      ? null
                      : Objects.requireNonNull(lookup.lookUpWorkingObject(target.entity())),
                  target.value() == null
                      ? null
                      : Objects.requireNonNull(lookup.lookUpWorkingObject(target.value()))));
        }
        random.setSeed(task.seed);
        context.configureQueryListener(task::query);
        context.beginTrial();
        context.setPendingTargets(targets);
        var repair = BuiltinAlnsOperators.<Solution_, Score_>repair(task.repairConfig);
        if (repair.repair(context, List.copyOf(targets))) {
          context.checkTermination();
          var evaluation = context.score();
          if (evaluation.isComplete()) {
            var score = InnerScore.fullyAssigned(evaluation.score());
            var journal = context.drainReplayJournal();
            if (environmentMode.isFullyAsserted()) {
              director.assertPredictedScoreFromScratch(score, AlnsPrimitiveMove.composite(journal));
            }
            task.result = new Result<>(task.index, score, journal);
          }
        }
        context.configureQueryListener(probe -> {});
        context.rollback();
        context.drainReplayJournal();
        if (environmentMode.isIntrusivelyAsserted()) {
          director.assertExpectedWorkingScore(baselineScore, "repair attempt rollback");
        }
      } catch (AlnsTerminationException cancelled) {
        if (!task.control.cancelled && !task.localLimitReached() && !closed && !synchronous)
          fail(cancelled);
        if (synchronous) throw cancelled;
      } catch (Throwable original) {
        fail(original);
      } finally {
        try {
          context.configureQueryListener(probe -> {});
          context.rollback();
          context.drainReplayJournal();
        } catch (Throwable cleanupFailure) {
          fail(cleanupFailure);
        } finally {
          activeTask = null;
          task.publish();
          task.done = true;
          LockSupport.unpark(coordinator);
        }
      }
    }

    void fail(Throwable original) {
      if (!failure.compareAndSet(null, original)) {
        var first = failure.get();
        if (first != original) first.addSuppressed(original);
      }
      if (activeTask != null) activeTask.control.cancelled = true;
      LockSupport.unpark(coordinator);
    }

    void release() {
      if (released) return;
      released = true;
      try {
        if (context != null) context.close();
      } catch (Throwable original) {
        fail(original);
      } finally {
        if (director != null) {
          try {
            calculationCount = director.getCalculationCount();
          } catch (Throwable original) {
            fail(original);
          }
          try {
            director.close();
          } catch (Throwable original) {
            fail(original);
          }
        }
      }
    }
  }
}
