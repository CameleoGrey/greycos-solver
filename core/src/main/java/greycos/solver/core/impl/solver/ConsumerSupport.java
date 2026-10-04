package greycos.solver.core.impl.solver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import greycos.solver.core.api.solver.event.SolverJobStartedEvent;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes all events of a solver job on one thread. Pending best solutions stay in {@link
 * BestSolutionHolder} until their consumer can run, including while waiting for a throttle
 * interval. This preserves problem-change acknowledgments when intermediate solutions are skipped.
 *
 * <p>The admission lock orders task submission against finalization. No application callbacks run
 * under this lock. Finalization promotes a delayed best-solution task and then queues a barrier
 * behind all accepted callbacks, so it never waits for a throttle interval or its own consumer.
 *
 * @param <Solution_> the solution type
 * @param <ProblemId_> the problem id type
 */
@NullMarked
final class ConsumerSupport<Solution_, ProblemId_> implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerSupport.class);
  private final ProblemId_ problemId;
  private final @Nullable Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer;
  private final Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer;
  private final Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer;
  private final @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer;
  private final @Nullable BiConsumer<? super ProblemId_, ? super Throwable> exceptionHandler;
  private final long throttleNanos;
  private final Semaphore firstSolutionConsumption = new Semaphore(1);
  private final Semaphore startSolverJobConsumption = new Semaphore(1);
  private final BestSolutionHolder<Solution_> bestSolutionHolder;
  private final ScheduledThreadPoolExecutor consumerExecutor;
  private final Object admissionLock = new Object();
  private final CompletableFuture<Void> finished = new CompletableFuture<>();

  // Protected by admissionLock. A consumption remains present while its callback is running.
  private @Nullable BestSolutionConsumption bestSolutionConsumption;
  private long pendingBestSolutionDeadline;
  private boolean finishing;

  public ConsumerSupport(
      ProblemId_ problemId,
      @Nullable Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
      @Nullable Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
      @Nullable Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer,
      @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer,
      @Nullable BiConsumer<? super ProblemId_, ? super Throwable> exceptionHandler,
      BestSolutionHolder<Solution_> bestSolutionHolder) {
    this.problemId = problemId;
    if (bestSolutionConsumer instanceof ThrottlingBestSolutionEventConsumer<Solution_> throttler) {
      // The wrapper is reusable configuration here; its independent dispatcher is never started.
      this.bestSolutionConsumer = throttler.getDelegate();
      this.throttleNanos = throttler.getThrottleNanos();
    } else {
      this.bestSolutionConsumer = bestSolutionConsumer;
      this.throttleNanos = 0L;
    }
    this.finalBestSolutionConsumer =
        finalBestSolutionConsumer == null ? finalBestSolution -> {} : finalBestSolutionConsumer;
    this.firstInitializedSolutionConsumer =
        firstInitializedSolutionConsumer == null ? event -> {} : firstInitializedSolutionConsumer;
    this.solverJobStartedConsumer = solverJobStartedConsumer;
    this.exceptionHandler = exceptionHandler;
    this.bestSolutionHolder = bestSolutionHolder;
    var threadFactory = Executors.defaultThreadFactory();
    this.consumerExecutor =
        new ScheduledThreadPoolExecutor(
            1, runnable -> threadFactory.newThread(() -> SolverEventThreadContext.run(runnable)));
    consumerExecutor.setRemoveOnCancelPolicy(true);
    consumerExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
  }

  void consumeIntermediateBestSolution(
      Solution_ solution,
      EventProducerId producerId,
      BooleanSupplier isEveryProblemChangeProcessed) {
    synchronized (admissionLock) {
      if (finishing) {
        return;
      }
      boolean wasEmpty = bestSolutionHolder.isEmpty();
      // Even without a best consumer, retain solutions to acknowledge their problem changes later.
      bestSolutionHolder.set(solution, producerId, isEveryProblemChangeProcessed);
      if (wasEmpty && !bestSolutionHolder.isEmpty()) {
        pendingBestSolutionDeadline = System.nanoTime() + throttleNanos;
      }
      scheduleWaitingBestSolution();
    }
  }

  // Called with admissionLock held, both by the solver and by the consumer thread.
  private void scheduleWaitingBestSolution() {
    if (finishing
        || bestSolutionConsumer == null
        || bestSolutionConsumption != null
        || bestSolutionHolder.isEmpty()) {
      return;
    }
    var consumption = new BestSolutionConsumption();
    bestSolutionConsumption = consumption;
    long delay = Math.max(0L, pendingBestSolutionDeadline - System.nanoTime());
    consumption.scheduledFuture =
        consumerExecutor.schedule(consumption, delay, TimeUnit.NANOSECONDS);
  }

  private void consumeBestSolution(
      @Nullable BestSolutionContainingProblemChanges<Solution_> solutionWithChanges) {
    if (solutionWithChanges == null) {
      return;
    }
    try {
      if (bestSolutionConsumer != null) {
        bestSolutionConsumer.accept(
            new NewBestSolutionEventImpl<>(
                solutionWithChanges.getBestSolution(), solutionWithChanges.getProducerId()));
      }
      solutionWithChanges.completeProblemChanges();
    } catch (Throwable throwable) {
      solutionWithChanges.completeProblemChangesExceptionally(throwable);
      reportFailure(throwable);
    }
  }

  void consumeFirstInitializedSolution(
      Solution_ solution, EventProducerId producerId, boolean isTerminatedEarly) {
    scheduleLifecycleConsumption(
        firstSolutionConsumption,
        () ->
            firstInitializedSolutionConsumer.accept(
                new FirstInitializedSolutionEventImpl<>(solution, producerId, isTerminatedEarly)),
        "first initialized solution");
  }

  void consumeStartSolverJob(Solution_ solution) {
    scheduleLifecycleConsumption(
        startSolverJobConsumption,
        () -> {
          if (solverJobStartedConsumer != null) {
            solverJobStartedConsumer.accept(new SolverJobStartedEventImpl<>(solution));
          }
        },
        "start solver job");
  }

  private void scheduleLifecycleConsumption(
      Semaphore consumptionPermit, Runnable consumer, String eventDescription) {
    try {
      // Restarts caused by problem changes must not queue an unbounded number of lifecycle events.
      consumptionPermit.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted when waiting for the %s consumption for problemId (%s)."
              .formatted(eventDescription, problemId),
          e);
    }
    synchronized (admissionLock) {
      if (finishing) {
        consumptionPermit.release();
        return;
      }
      consumerExecutor.execute(
          () -> {
            try {
              consumer.run();
            } catch (Throwable throwable) {
              reportFailure(throwable);
            } finally {
              consumptionPermit.release();
            }
          });
    }
  }

  void consumeFinalBestSolution(Solution_ solution) {
    requestFinish(solution);
  }

  private void requestFinish(@Nullable Solution_ finalSolution) {
    synchronized (admissionLock) {
      if (finishing) {
        return;
      }
      finishing = true;
      bestSolutionHolder.closeProblemChangeAdmission();
      var consumption = bestSolutionConsumption;
      if (consumption != null && !consumption.started) {
        // Canceling the timer does not complete this consumption. The same one-shot task is queued
        // immediately, so a timer already racing to execute cannot cause duplicate delivery.
        var scheduledFuture = consumption.scheduledFuture;
        if (scheduledFuture != null) {
          scheduledFuture.cancel(false);
        }
        consumerExecutor.execute(consumption);
      }
      consumerExecutor.execute(() -> finish(finalSolution));
    }
  }

  private void finish(@Nullable Solution_ finalSolution) {
    try {
      if (bestSolutionConsumer != null) {
        // An already running callback may have left a newer solution in the holder.
        consumeBestSolution(bestSolutionHolder.take());
      }
      if (finalSolution != null) {
        var finalSolutionWithChanges =
            bestSolutionConsumer == null ? bestSolutionHolder.take() : null;
        try {
          finalBestSolutionConsumer.accept(new FinalBestSolutionEventImpl<>(finalSolution));
          if (finalSolutionWithChanges != null) {
            finalSolutionWithChanges.completeProblemChanges();
          }
        } catch (Throwable throwable) {
          if (finalSolutionWithChanges != null) {
            finalSolutionWithChanges.completeProblemChangesExceptionally(throwable);
          }
          reportFailure(throwable);
        }
      }
    } catch (Throwable throwable) {
      reportFailure(throwable);
    } finally {
      bestSolutionHolder.cancelPendingChanges();
      consumerExecutor.shutdown();
      finished.complete(null);
    }
  }

  private void reportFailure(Throwable throwable) {
    if (exceptionHandler == null) {
      LOGGER.error("Consuming a solver event failed for problemId ({}).", problemId, throwable);
      return;
    }
    try {
      exceptionHandler.accept(problemId, throwable);
    } catch (Throwable handlerFailure) {
      LOGGER.error("The exception handler failed for problemId ({}).", problemId, handlerFailure);
      LOGGER.error("The original consumer failure for problemId ({}).", problemId, throwable);
    }
  }

  void requestClose() {
    requestFinish(null);
  }

  @Override
  public void close() {
    requestClose();
    if (SolverEventThreadContext.isActive()) {
      // Waiting from any event consumer can deadlock when callbacks close each other's jobs.
      return;
    }
    boolean interrupted = false;
    try {
      while (true) {
        try {
          finished.get();
          break;
        } catch (InterruptedException e) {
          interrupted = true;
        } catch (ExecutionException e) {
          throw new IllegalStateException(
              "Closing the consumer failed for problemId (%s).".formatted(problemId), e.getCause());
        }
      }
      while (true) {
        try {
          if (consumerExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            bestSolutionHolder.awaitCancellationCompletion();
            return;
          }
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private final class BestSolutionConsumption implements Runnable {

    // Protected by admissionLock, including when this task is promoted during finalization.
    private @Nullable ScheduledFuture<?> scheduledFuture;
    private boolean started;

    @Override
    public void run() {
      BestSolutionContainingProblemChanges<Solution_> solutionWithChanges;
      synchronized (admissionLock) {
        if (started) {
          return;
        }
        started = true;
        solutionWithChanges = bestSolutionHolder.take();
      }
      try {
        consumeBestSolution(solutionWithChanges);
      } finally {
        synchronized (admissionLock) {
          bestSolutionConsumption = null;
          scheduleWaitingBestSolution();
        }
      }
    }
  }

  record NewBestSolutionEventImpl<Solution_>(Solution_ solution, EventProducerId producerId)
      implements NewBestSolutionEvent<Solution_> {}

  record FirstInitializedSolutionEventImpl<Solution_>(
      Solution_ solution, EventProducerId producerId, boolean isTerminatedEarly)
      implements FirstInitializedSolutionEvent<Solution_> {}

  record FinalBestSolutionEventImpl<Solution_>(Solution_ solution)
      implements FinalBestSolutionEvent<Solution_> {}

  record SolverJobStartedEventImpl<Solution_>(Solution_ solution)
      implements SolverJobStartedEvent<Solution_> {}
}
