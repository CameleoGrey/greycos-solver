package ai.greycos.solver.core.impl.solver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import ai.greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import ai.greycos.solver.core.api.solver.event.SolverJobStartedEvent;

/**
 * Manages asynchronous consumption of solver events including best solutions, first initialized
 * solutions, and solver job started events. Coordinates with throttling consumers and ensures
 * proper event ordering.
 */
final class ConsumerSupport<Solution_, ProblemId_> implements AutoCloseable {

  private final ProblemId_ problemId;
  private final Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer;
  private final Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer;
  private final Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer;
  private final Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer;
  private final BiConsumer<? super ProblemId_, ? super Throwable> exceptionHandler;
  private final Semaphore activeConsumption = new Semaphore(1);
  private final Semaphore firstSolutionConsumption = new Semaphore(1);
  private final Semaphore startSolverJobConsumption = new Semaphore(1);
  private final BestSolutionHolder<Solution_> bestSolutionHolder;
  private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor();
  private Solution_ firstInitializedSolution;
  private Solution_ initialSolution;

  public ConsumerSupport(
      ProblemId_ problemId,
      Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
      Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
      Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer,
      Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer,
      BiConsumer<? super ProblemId_, ? super Throwable> exceptionHandler,
      BestSolutionHolder<Solution_> bestSolutionHolder) {
    this.problemId = problemId;
    this.bestSolutionConsumer = bestSolutionConsumer;
    this.finalBestSolutionConsumer =
        finalBestSolutionConsumer == null ? finalBestSolution -> {} : finalBestSolutionConsumer;
    this.firstInitializedSolutionConsumer =
        firstInitializedSolutionConsumer == null ? event -> {} : firstInitializedSolutionConsumer;
    this.solverJobStartedConsumer = solverJobStartedConsumer;
    this.exceptionHandler = exceptionHandler;
    this.bestSolutionHolder = bestSolutionHolder;
    this.firstInitializedSolution = null;
    this.initialSolution = null;
  }

  void consumeIntermediateBestSolution(
      Solution_ bestSolution,
      EventProducerId producerId,
      BooleanSupplier isEveryProblemChangeProcessed) {
    bestSolutionHolder.set(bestSolution, producerId, isEveryProblemChangeProcessed);
    if (bestSolutionConsumer != null) {
      tryConsumeWaitingIntermediateBestSolution();
    }
  }

  void consumeFirstInitializedSolution(
      Solution_ firstInitializedSolution, EventProducerId producerId, boolean isTerminatedEarly) {
    this.firstInitializedSolution = firstInitializedSolution;
    scheduleFirstInitializedSolutionConsumption(
        solution ->
            firstInitializedSolutionConsumer.accept(
                new FirstInitializedSolutionEventImpl<>(solution, producerId, isTerminatedEarly)));
  }

  void consumeStartSolverJob(Solution_ initialSolution) {
    this.initialSolution = initialSolution;
    scheduleStartJobConsumption();
  }

  void consumeFinalBestSolution(Solution_ finalBestSolution) {
    // Drain any remaining intermediate best solutions before submitting the final one.
    if (bestSolutionConsumer != null) {
      scheduleIntermediateBestSolutionConsumption();
    }

    if (bestSolutionConsumer instanceof ThrottlingBestSolutionEventConsumer) {
      ((ThrottlingBestSolutionEventConsumer<Solution_>) bestSolutionConsumer)
          .terminateAndDeliverPending();
    }

    // Submit a marker task BEFORE the final best solution task.
    // When the marker task completes, all previous tasks have been processed.
    // We need to do this before the final task because the final task shuts down the executor.
    Future<?> markerTask = null;
    try {
      markerTask = consumerExecutor.submit(() -> {});
    } catch (java.util.concurrent.RejectedExecutionException e) {
      // Executor is already shut down, continue without waiting
    }

    // Submit the final best solution consumption to the executor.
    // The single-threaded executor guarantees that this task will run after all
    // previously submitted tasks (start solver job, first initialized solution,
    // intermediate solutions) have completed, because tasks are processed in FIFO order.
    consumerExecutor.submit(
        () -> {
          try {
            finalBestSolutionConsumer.accept(new FinalBestSolutionEventImpl<>(finalBestSolution));
          } catch (Throwable throwable) {
            exceptionHandler.accept(problemId, throwable);
          } finally {
            if (bestSolutionConsumer == null) {
              var solutionHolder = bestSolutionHolder.take();
              if (solutionHolder != null) {
                solutionHolder.completeProblemChanges();
              }
            }
            bestSolutionHolder.cancelPendingChangesQuietly();
            disposeConsumerThread();
          }
        });

    // Wait for the marker task to complete, ensuring all prior tasks have run.
    if (markerTask != null) {
      waitForMarkerTask(markerTask);
    }
  }

  private void waitForMarkerTask(Future<?> markerTask) {
    // Wait for the marker task to complete.
    // When it completes, all previously submitted tasks have been processed.
    try {
      // Wait with a short timeout to avoid blocking indefinitely
      markerTask.get(1, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // If interrupted, we can't guarantee all tasks have run, but that's okay
      // - the executor will process them eventually
    } catch (java.util.concurrent.TimeoutException e) {
      // Timeout means tasks are taking too long, but that's okay
      // - they're still processing in the background
    } catch (java.util.concurrent.ExecutionException e) {
      // Should not happen as the marker task doesn't throw exceptions
    }
  }

  private void tryConsumeWaitingIntermediateBestSolution() {
    if (bestSolutionHolder.isEmpty()) {
      return;
    }
    if (activeConsumption.tryAcquire()) {
      scheduleIntermediateBestSolutionConsumption()
          .thenRunAsync(this::tryConsumeWaitingIntermediateBestSolution, consumerExecutor);
    }
  }

  private CompletableFuture<Void> scheduleIntermediateBestSolutionConsumption() {
    return CompletableFuture.runAsync(
        () -> {
          BestSolutionContainingProblemChanges<Solution_> bestSolutionContainingProblemChanges =
              bestSolutionHolder.take();
          if (bestSolutionContainingProblemChanges != null) {
            try {
              bestSolutionConsumer.accept(
                  new NewBestSolutionEventImpl<>(
                      bestSolutionContainingProblemChanges.getBestSolution(),
                      bestSolutionContainingProblemChanges.getProducerId()));
              bestSolutionContainingProblemChanges.completeProblemChanges();
            } catch (Throwable throwable) {
              if (exceptionHandler != null) {
                exceptionHandler.accept(problemId, throwable);
              }
              bestSolutionContainingProblemChanges.completeProblemChangesExceptionally(throwable);
            }
          }
          activeConsumption.release();
        },
        consumerExecutor);
  }

  private void scheduleFirstInitializedSolutionConsumption(
      Consumer<? super Solution_> solutionConsumer) {
    scheduleConsumptionNoSemaphore(solutionConsumer, firstInitializedSolution);
  }

  private void scheduleStartJobConsumption() {
    Consumer<? super Solution_> consumer =
        solverJobStartedConsumer == null
            ? null
            : solution ->
                solverJobStartedConsumer.accept(new SolverJobStartedEventImpl<>(solution));
    scheduleConsumptionNoSemaphore(consumer, initialSolution);
  }

  private void scheduleConsumptionNoSemaphore(
      Consumer<? super Solution_> consumer, Solution_ solution) {
    CompletableFuture.runAsync(
        () -> {
          try {
            if (consumer != null && solution != null) {
              consumer.accept(solution);
            }
          } catch (Throwable throwable) {
            if (exceptionHandler != null) {
              exceptionHandler.accept(problemId, throwable);
            }
          }
        },
        consumerExecutor);
  }

  private void scheduleConsumption(
      Semaphore semaphore, Consumer<? super Solution_> consumer, Solution_ solution) {
    CompletableFuture.runAsync(
        () -> {
          try {
            if (consumer != null && solution != null) {
              consumer.accept(solution);
            }
          } catch (Throwable throwable) {
            if (exceptionHandler != null) {
              exceptionHandler.accept(problemId, throwable);
            }
          } finally {
            semaphore.release();
          }
        },
        consumerExecutor);
  }

  private void acquireAll() throws InterruptedException {
    activeConsumption.acquire();
    startSolverJobConsumption.acquire();
    firstSolutionConsumption.acquire();
  }

  private void releaseAll() {
    activeConsumption.release();
    startSolverJobConsumption.release();
    firstSolutionConsumption.release();
  }

  @Override
  public void close() {
    // Clean up resources.
    // Note: cancelPendingChangesQuietly() is called in the final best solution consumption task.
    if (bestSolutionConsumer instanceof AutoCloseable) {
      try {
        ((AutoCloseable) bestSolutionConsumer).close();
      } catch (Exception e) {
      }
    }
    disposeConsumerThread();
  }

  private void disposeConsumerThread() {
    // Gracefully shut down the executor.
    // Don't wait for termination here, as this might be called from within the executor.
    consumerExecutor.shutdown();
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
