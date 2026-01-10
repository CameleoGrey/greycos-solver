package ai.greycos.solver.core.impl.solver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * Manages asynchronous consumption of solver events including best solutions, first initialized solutions,
 * and solver job started events. Coordinates with throttling consumers and ensures proper event ordering.
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
    try {
      firstSolutionConsumption.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted when waiting for first initialized solution consumption.");
    }
    this.firstInitializedSolution = firstInitializedSolution;
    scheduleFirstInitializedSolutionConsumption(
        solution ->
            firstInitializedSolutionConsumer.accept(
                new FirstInitializedSolutionEventImpl<>(solution, producerId, isTerminatedEarly)));
  }

  void consumeStartSolverJob(Solution_ initialSolution) {
    try {
      startSolverJobConsumption.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted when waiting for start solver job consumption.");
    }
    this.initialSolution = initialSolution;
    scheduleStartJobConsumption();
  }

  void consumeFinalBestSolution(Solution_ finalBestSolution) {
    try {
      acquireAll();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted when waiting for final best solution consumption.");
    }

    if (bestSolutionConsumer != null) {
      scheduleIntermediateBestSolutionConsumption();
    }

    if (bestSolutionConsumer instanceof ThrottlingBestSolutionEventConsumer) {
      ((ThrottlingBestSolutionEventConsumer<Solution_>) bestSolutionConsumer)
          .terminateAndDeliverPending();
    }
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
            bestSolutionHolder.cancelPendingChanges();
            releaseAll();
            disposeConsumerThread();
          }
        });
  }

  private void tryConsumeWaitingIntermediateBestSolution() {
    if (bestSolutionHolder.isEmpty()) {
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
            } finally {
              activeConsumption.release();
            }
          }
        },
        consumerExecutor);
  }

  private void scheduleFirstInitializedSolutionConsumption(
      Consumer<? super Solution_> solutionConsumer) {
    scheduleConsumption(firstSolutionConsumption, solutionConsumer, firstInitializedSolution);
  }

  private void scheduleStartJobConsumption() {
    scheduleConsumption(
        startSolverJobConsumption,
        solverJobStartedConsumer == null
            ? null
            : solution ->
                solverJobStartedConsumer.accept(new SolverJobStartedEventImpl<>(solution)),
        initialSolution);
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
    try {
      acquireAll();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted when waiting for closing consumer.");
    } finally {
      if (bestSolutionConsumer instanceof AutoCloseable) {
        try {
          ((AutoCloseable) bestSolutionConsumer).close();
        } catch (Exception e) {
        }
      }
      disposeConsumerThread();
      bestSolutionHolder.cancelPendingChanges();
      releaseAll();
    }
  }

  private void disposeConsumerThread() {
    consumerExecutor.shutdownNow();
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
