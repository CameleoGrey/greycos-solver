package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ConsumerSupportTest {

  private ConsumerSupport<TestdataSolution, Long> consumerSupport;

  @AfterEach
  void close() throws Exception {
    if (consumerSupport != null) {
      closeWithinTimeout(consumerSupport);
    }
  }

  @Test
  @Timeout(60)
  void skipAhead() throws InterruptedException {
    CountDownLatch consumptionStarted = new CountDownLatch(1);
    CountDownLatch consumptionPaused = new CountDownLatch(1);
    CountDownLatch consumptionCompleted = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    List<TestdataSolution> consumedSolutions = Collections.synchronizedList(new ArrayList<>());
    BestSolutionHolder<TestdataSolution> bestSolutionHolder = new BestSolutionHolder<>();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            event -> {
              try {
                consumptionStarted.countDown();
                consumptionPaused.await();
                consumedSolutions.add(event.solution());
                if (event.solution().getEntityList().size() == 3) {
                  consumptionCompleted.countDown();
                }
              } catch (InterruptedException e) {
                error.set(new IllegalStateException("Interrupted waiting.", e));
              }
            },
            null,
            null,
            null,
            null,
            bestSolutionHolder);

    consumeIntermediateBestSolution(TestdataSolution.generateSolution(1, 1));
    consumptionStarted.await();
    consumeIntermediateBestSolution(TestdataSolution.generateSolution(2, 2));
    consumeIntermediateBestSolution(TestdataSolution.generateSolution(3, 3));

    consumptionPaused.countDown();
    consumptionCompleted.await();
    assertThat(consumedSolutions).hasSize(2);
    assertThat(consumedSolutions.get(0).getEntityList()).hasSize(1);
    assertThat(consumedSolutions.get(1).getEntityList()).hasSize(3);

    if (error.get() != null) {
      fail("Exception during consumption.", error.get());
    }
  }

  @Test
  @Timeout(60)
  void problemChangesComplete_afterFinalBestSolutionIsConsumed()
      throws ExecutionException, InterruptedException {
    BestSolutionHolder<TestdataSolution> bestSolutionHolder = new BestSolutionHolder<>();
    AtomicReference<TestdataSolution> finalBestSolutionRef = new AtomicReference<>();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            null,
            event -> finalBestSolutionRef.set(event.solution()),
            null,
            null,
            null,
            bestSolutionHolder);

    CompletableFuture<Void> futureProblemChange = addProblemChange(bestSolutionHolder);

    consumeIntermediateBestSolution(TestdataSolution.generateSolution());
    assertThat(futureProblemChange).isNotCompleted();
    TestdataSolution finalBestSolution = TestdataSolution.generateSolution();
    consumerSupport.consumeFinalBestSolution(finalBestSolution);
    futureProblemChange.get();
    assertThat(finalBestSolutionRef.get()).isSameAs(finalBestSolution);
    assertThat(futureProblemChange).isCompleted();
  }

  @Test
  @Timeout(60)
  void problemChangesCompleteExceptionally_afterExceptionInConsumer() {
    BestSolutionHolder<TestdataSolution> bestSolutionHolder = new BestSolutionHolder<>();
    final String errorMessage = "Test exception";
    Consumer<NewBestSolutionEvent<TestdataSolution>> erroneousConsumer =
        bestSolution -> {
          throw new RuntimeException(errorMessage);
        };
    consumerSupport =
        new ConsumerSupport<>(
            1L, erroneousConsumer, null, null, null, (id, ex) -> {}, bestSolutionHolder);

    CompletableFuture<Void> futureProblemChange = addProblemChange(bestSolutionHolder);
    consumeIntermediateBestSolution(TestdataSolution.generateSolution());

    assertThatExceptionOfType(ExecutionException.class)
        .isThrownBy(futureProblemChange::get)
        .havingRootCause()
        .isInstanceOf(RuntimeException.class)
        .withMessage(errorMessage);
    assertThat(futureProblemChange).isCompletedExceptionally();
  }

  @Test
  @Timeout(60)
  void pendingProblemChangesAreCanceled_afterFinalBestSolutionIsConsumed()
      throws ExecutionException, InterruptedException {
    BestSolutionHolder<TestdataSolution> bestSolutionHolder = new BestSolutionHolder<>();
    consumerSupport = new ConsumerSupport<>(1L, null, null, null, null, null, bestSolutionHolder);

    CompletableFuture<Void> futureProblemChange = addProblemChange(bestSolutionHolder);

    consumeIntermediateBestSolution(TestdataSolution.generateSolution());
    assertThat(futureProblemChange).isNotCompleted();

    CompletableFuture<Void> pendingProblemChange = addProblemChange(bestSolutionHolder);
    consumerSupport.consumeFinalBestSolution(TestdataSolution.generateSolution());
    futureProblemChange.get();
    assertThat(futureProblemChange).isCompleted();

    assertThatExceptionOfType(CancellationException.class).isThrownBy(pendingProblemChange::get);
  }

  @Test
  @Timeout(60)
  void throttledBestSolutionConsumer_receivesFinalBestSolutionBeforeFinalConsumer()
      throws InterruptedException {
    BestSolutionHolder<TestdataSolution> bestSolutionHolder = new BestSolutionHolder<>();
    AtomicReference<TestdataSolution> intermediateBestSolutionRef = new AtomicReference<>();
    AtomicReference<TestdataSolution> finalBestSolutionRef = new AtomicReference<>();
    CountDownLatch intermediateConsumed = new CountDownLatch(1);
    CountDownLatch finalConsumed = new CountDownLatch(1);
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  intermediateBestSolutionRef.set(event.solution());
                  intermediateConsumed.countDown();
                },
                Duration.ofDays(1)),
            event -> {
              finalBestSolutionRef.set(event.solution());
              finalConsumed.countDown();
            },
            null,
            null,
            null,
            bestSolutionHolder);

    TestdataSolution finalBestSolution = TestdataSolution.generateSolution();
    consumeIntermediateBestSolution(finalBestSolution);
    consumerSupport.consumeFinalBestSolution(finalBestSolution);

    assertThat(intermediateConsumed.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(finalConsumed.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(intermediateBestSolutionRef.get()).isSameAs(finalBestSolution);
    assertThat(finalBestSolutionRef.get()).isSameAs(finalBestSolution);
  }

  @Test
  @Timeout(10)
  void throttledProblemChangesWaitForTheLatestSolutionToBeConsumed() throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    var callbackStarted = new CountDownLatch(1);
    var releaseCallback = new CountDownLatch(1);
    var consumedSolutions = new ArrayList<TestdataSolution>();
    var finalConsumerCalled = new AtomicBoolean();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  callbackStarted.countDown();
                  await(releaseCallback);
                  consumedSolutions.add(event.solution());
                },
                Duration.ofDays(1)),
            event -> finalConsumerCalled.set(true),
            null,
            null,
            (id, error) -> fail("Unexpected callback failure.", error),
            holder);

    var firstChange = addProblemChange(holder);
    consumeIntermediateBestSolution(TestdataSolution.generateSolution(1, 1));
    var secondChange = addProblemChange(holder);
    var latestSolution = TestdataSolution.generateSolution(2, 2);
    consumeIntermediateBestSolution(latestSolution);
    assertThat(firstChange).isNotCompleted();
    assertThat(secondChange).isNotCompleted();
    assertThat(callbackStarted.getCount()).isOne();

    try {
      consumerSupport.consumeFinalBestSolution(latestSolution);
      assertThat(callbackStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(firstChange).isNotCompleted();
      assertThat(secondChange).isNotCompleted();
      assertThat(finalConsumerCalled).isFalse();
    } finally {
      releaseCallback.countDown();
    }
    consumerSupport.close();
    assertThat(consumedSolutions).containsExactly(latestSolution);
    assertThat(firstChange).isCompleted();
    assertThat(secondChange).isCompleted();
    assertThat(finalConsumerCalled).isTrue();
  }

  @Test
  @Timeout(10)
  void throttledFailureReachesProblemChangesAndTheJobExceptionHandler() throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    var failure = new IllegalStateException("Consumer failed.");
    var consumedCount = new AtomicInteger();
    var reportedFailure = new AtomicReference<Throwable>();
    var failureReported = new CountDownLatch(1);
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  if (consumedCount.incrementAndGet() == 1) {
                    throw failure;
                  }
                },
                Duration.ofNanos(1)),
            null,
            null,
            null,
            (id, error) -> {
              reportedFailure.set(error);
              failureReported.countDown();
            },
            holder);

    var firstChange = addProblemChange(holder);
    consumeIntermediateBestSolution(TestdataSolution.generateSolution());
    assertThatExceptionOfType(ExecutionException.class)
        .isThrownBy(() -> firstChange.get(5, TimeUnit.SECONDS))
        .withCause(failure);
    assertThat(failureReported.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(reportedFailure).hasValue(failure);

    var secondChange = addProblemChange(holder);
    consumeIntermediateBestSolution(TestdataSolution.generateSolution());
    secondChange.get(5, TimeUnit.SECONDS);
    consumerSupport.close();
    assertThat(consumedCount).hasValue(2);
  }

  @Test
  @Timeout(10)
  void throwingExceptionHandlerDoesNotPreventFinalization() {
    var holder = new BestSolutionHolder<TestdataSolution>();
    var failure = new IllegalStateException("Consumer failed.");
    var handlerCalls = new AtomicInteger();
    var finalConsumerCalled = new AtomicBoolean();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  throw failure;
                },
                Duration.ofDays(1)),
            event -> finalConsumerCalled.set(true),
            null,
            null,
            (id, error) -> {
              handlerCalls.incrementAndGet();
              throw new IllegalArgumentException("Exception handler failed.");
            },
            holder);
    var change = addProblemChange(holder);
    var solution = TestdataSolution.generateSolution();
    consumeIntermediateBestSolution(solution);

    consumerSupport.consumeFinalBestSolution(solution);
    consumerSupport.close();

    assertThatExceptionOfType(ExecutionException.class).isThrownBy(change::get).withCause(failure);
    assertThat(handlerCalls).hasValue(1);
    assertThat(finalConsumerCalled).isTrue();
  }

  @Test
  @Timeout(10)
  void lifecycleCallbacksWaitForThrottledBestConsumer() throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    var bestStarted = new CountDownLatch(1);
    var releaseBest = new CountDownLatch(1);
    var lifecycleConsumed = new CountDownLatch(2);
    var callbackOrder = new ArrayList<String>();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  callbackOrder.add("best started");
                  bestStarted.countDown();
                  await(releaseBest);
                  callbackOrder.add("best ended");
                },
                Duration.ofNanos(1)),
            event -> callbackOrder.add("final"),
            event -> {
              callbackOrder.add("initialized");
              lifecycleConsumed.countDown();
            },
            event -> {
              callbackOrder.add("started");
              lifecycleConsumed.countDown();
            },
            null,
            holder);
    var solution = TestdataSolution.generateSolution();
    try {
      consumeIntermediateBestSolution(solution);
      assertThat(bestStarted.await(5, TimeUnit.SECONDS)).isTrue();
      consumerSupport.consumeFirstInitializedSolution(
          solution, EventProducerId.constructionHeuristic(0), false);
      consumerSupport.consumeStartSolverJob(solution);
      consumerSupport.consumeFinalBestSolution(solution);
      assertThat(lifecycleConsumed.await(100, TimeUnit.MILLISECONDS)).isFalse();
    } finally {
      releaseBest.countDown();
    }
    consumerSupport.close();
    assertThat(callbackOrder)
        .containsExactly("best started", "best ended", "initialized", "started", "final");
  }

  @Test
  @Timeout(10)
  void interruptedCloseWaitsForActiveAndPendingConsumers() throws Exception {
    var holder = new BestSolutionHolder<TestdataSolution>();
    var firstStarted = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var closeStarted = new CountDownLatch(1);
    var closeFinished = new CountDownLatch(1);
    var interruptRestored = new AtomicBoolean();
    var consumedSolutions = new ArrayList<TestdataSolution>();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  if (consumedSolutions.isEmpty()) {
                    firstStarted.countDown();
                    await(releaseFirst);
                  }
                  consumedSolutions.add(event.solution());
                },
                Duration.ofNanos(1)),
            null,
            null,
            null,
            null,
            holder);
    var first = TestdataSolution.generateSolution(1, 1);
    var latest = TestdataSolution.generateSolution(2, 2);
    var closer =
        new Thread(
            () -> {
              Thread.currentThread().interrupt();
              closeStarted.countDown();
              consumerSupport.close();
              interruptRestored.set(Thread.currentThread().isInterrupted());
              closeFinished.countDown();
            });
    try {
      consumeIntermediateBestSolution(first);
      assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
      consumeIntermediateBestSolution(latest);
      closer.start();
      assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(closeFinished.await(100, TimeUnit.MILLISECONDS)).isFalse();
      closer.interrupt();
    } finally {
      releaseFirst.countDown();
    }
    assertThat(closeFinished.await(5, TimeUnit.SECONDS)).isTrue();
    closer.join();
    assertThat(interruptRestored).isTrue();
    assertThat(consumedSolutions).containsExactly(first, latest);
  }

  @Test
  @Timeout(10)
  void callbacksCanCloseEachOthersConsumers() throws Exception {
    var bothCallbacksStarted = new CyclicBarrier(2);
    var callbacksReturned = new CountDownLatch(2);
    var firstSupport = new AtomicReference<ConsumerSupport<TestdataSolution, Long>>();
    var secondSupport = new AtomicReference<ConsumerSupport<TestdataSolution, Long>>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    Consumer<NewBestSolutionEvent<TestdataSolution>> closeOtherConsumer =
        event -> {
          try {
            bothCallbacksStarted.await(5, TimeUnit.SECONDS);
            if (event.solution().getEntityList().size() == 1) {
              secondSupport.get().close();
            } else {
              firstSupport.get().close();
            }
            callbacksReturned.countDown();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        };
    firstSupport.set(
        new ConsumerSupport<>(
            1L,
            closeOtherConsumer,
            null,
            null,
            null,
            (id, error) -> errors.add(error),
            new BestSolutionHolder<>()));
    secondSupport.set(
        new ConsumerSupport<>(
            2L,
            closeOtherConsumer,
            null,
            null,
            null,
            (id, error) -> errors.add(error),
            new BestSolutionHolder<>()));
    try {
      firstSupport
          .get()
          .consumeIntermediateBestSolution(
              TestdataSolution.generateSolution(1, 1),
              EventProducerId.constructionHeuristic(0),
              () -> true);
      secondSupport
          .get()
          .consumeIntermediateBestSolution(
              TestdataSolution.generateSolution(2, 2),
              EventProducerId.constructionHeuristic(0),
              () -> true);
      assertThat(callbacksReturned.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      closeWithinTimeout(firstSupport.get(), secondSupport.get());
    }
    assertThat(errors).isEmpty();
  }

  @Test
  @Timeout(10)
  void managedAndStandaloneCallbacksCanCloseEachOther() throws Exception {
    var bothCallbacksStarted = new CyclicBarrier(2);
    var callbacksReturned = new CountDownLatch(2);
    var standaloneRef =
        new AtomicReference<ThrottlingBestSolutionEventConsumer<TestdataSolution>>();
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    Consumer<Runnable> closeOther =
        closeAction -> {
          try {
            bothCallbacksStarted.await(5, TimeUnit.SECONDS);
            closeAction.run();
            callbacksReturned.countDown();
          } catch (Exception e) {
            errors.add(e);
          }
        };
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            event -> closeOther.accept(() -> standaloneRef.get().close()),
            null,
            null,
            null,
            (id, error) -> errors.add(error),
            new BestSolutionHolder<>());
    var standalone =
        ThrottlingBestSolutionEventConsumer.<TestdataSolution>of(
            event -> closeOther.accept(consumerSupport::close), Duration.ofNanos(1));
    standaloneRef.set(standalone);
    try {
      var solution = TestdataSolution.generateSolution();
      consumeIntermediateBestSolution(solution);
      standalone.accept(
          new ConsumerSupport.NewBestSolutionEventImpl<>(
              solution, EventProducerId.solvingStarted()));
      assertThat(callbacksReturned.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      closeWithinTimeout(consumerSupport, standalone);
    }
    assertThat(errors).isEmpty();
  }

  @Test
  @Timeout(10)
  void sustainedManagedStreamDeliversBeforeTheProducerStops() throws Exception {
    var producing = new AtomicBoolean(true);
    var threeConsumed = new CountDownLatch(3);
    var latestSolution = new AtomicReference<TestdataSolution>();
    var lastConsumed = new AtomicReference<TestdataSolution>();
    var failures = Collections.synchronizedList(new ArrayList<Throwable>());
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  lastConsumed.set(event.solution());
                  threeConsumed.countDown();
                },
                Duration.ofMillis(50)),
            null,
            null,
            null,
            (id, error) -> failures.add(error),
            new BestSolutionHolder<>());
    var producer =
        new Thread(
            () -> {
              while (producing.get()) {
                var solution = TestdataSolution.generateSolution(1, 1);
                latestSolution.set(solution);
                consumeIntermediateBestSolution(solution);
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
              }
            });
    producer.start();
    try {
      assertThat(threeConsumed.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(producer.isAlive()).isTrue();
    } finally {
      producing.set(false);
      producer.join();
    }
    consumerSupport.consumeFinalBestSolution(latestSolution.get());
    consumerSupport.close();
    assertThat(lastConsumed).hasValue(latestSolution.get());
    assertThat(failures).isEmpty();
  }

  @Test
  @Timeout(10)
  void waitingForAnActiveCallbackDoesNotRestartThePendingThrottleWindow() throws Exception {
    var firstStarted = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var secondConsumed = new CountDownLatch(1);
    var consumedCount = new AtomicInteger();
    consumerSupport =
        new ConsumerSupport<>(
            1L,
            ThrottlingBestSolutionEventConsumer.of(
                event -> {
                  if (consumedCount.incrementAndGet() == 1) {
                    firstStarted.countDown();
                    await(releaseFirst);
                  } else {
                    secondConsumed.countDown();
                  }
                },
                Duration.ofMillis(500)),
            null,
            null,
            null,
            null,
            new BestSolutionHolder<>());
    try {
      consumeIntermediateBestSolution(TestdataSolution.generateSolution());
      assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
      consumeIntermediateBestSolution(TestdataSolution.generateSolution());
      // Let the second window expire while the first callback still occupies the consumer thread.
      assertThat(secondConsumed.await(800, TimeUnit.MILLISECONDS)).isFalse();
    } finally {
      releaseFirst.countDown();
    }
    assertThat(secondConsumed.await(250, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  @Timeout(10)
  void timerRacingWithFinalizationDeliversOnlyOnce() throws Exception {
    for (int i = 0; i < 30; i++) {
      var consumedCount = new AtomicInteger();
      var finalCount = new AtomicInteger();
      consumerSupport =
          new ConsumerSupport<>(
              1L,
              ThrottlingBestSolutionEventConsumer.of(
                  event -> consumedCount.incrementAndGet(), Duration.ofNanos(1)),
              event -> finalCount.incrementAndGet(),
              null,
              null,
              null,
              new BestSolutionHolder<>());
      var solution = TestdataSolution.generateSolution();
      consumeIntermediateBestSolution(solution);
      consumerSupport.consumeFinalBestSolution(solution);
      var concurrentClose = Thread.ofPlatform().daemon().start(consumerSupport::close);
      closeWithinTimeout(consumerSupport);
      concurrentClose.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(concurrentClose.isAlive()).isFalse();
      assertThat(consumedCount).hasValue(1);
      assertThat(finalCount).hasValue(1);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the test callback to be released.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted waiting for the test callback.", e);
    }
  }

  private static void closeWithinTimeout(AutoCloseable... consumers) throws Exception {
    var completed = new CompletableFuture<Void>();
    Thread.ofPlatform()
        .daemon()
        .start(
            () -> {
              try {
                for (var consumer : consumers) {
                  consumer.close();
                }
                completed.complete(null);
              } catch (Throwable throwable) {
                completed.completeExceptionally(throwable);
              }
            });
    completed.get(5, TimeUnit.SECONDS);
  }

  private CompletableFuture<Void> addProblemChange(
      BestSolutionHolder<TestdataSolution> bestSolutionHolder) {
    return bestSolutionHolder.addProblemChange(
        mock(Solver.class), List.of(mock(ProblemChange.class)));
  }

  private void consumeIntermediateBestSolution(TestdataSolution bestSolution) {
    consumerSupport.consumeIntermediateBestSolution(
        bestSolution, EventProducerId.constructionHeuristic(0), () -> true);
  }
}
