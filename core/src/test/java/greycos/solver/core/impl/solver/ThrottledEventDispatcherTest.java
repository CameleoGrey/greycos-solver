package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class ThrottledEventDispatcherTest {

  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

  private final List<ThrottledEventDispatcher<Integer>> dispatchers = new ArrayList<>();
  private final List<Throwable> callbackFailures = new CopyOnWriteArrayList<>();

  @AfterEach
  void closeDispatchers() throws InterruptedException {
    for (var dispatcher : dispatchers) {
      assertFinished(startThread(dispatcher::close));
    }
    assertThat(callbackFailures).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void reentrantTerminationReturnsBeforeCallbackFinishes(boolean duringDrain, boolean close)
      throws InterruptedException {
    var dispatcherReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var schedulerThread = new AtomicReference<Thread>();
    var terminationReturned = new CountDownLatch(1);
    var allowCallbackToFinish = new CountDownLatch(1);
    var lastDelivered = new CountDownLatch(1);
    List<Integer> delivered = new CopyOnWriteArrayList<>();
    var dispatcher =
        dispatcher(
            event -> {
              schedulerThread.set(Thread.currentThread());
              if (event == 1) {
                dispatcherReference.get().submit(2);
                dispatcherReference.get().submit(3);
                terminate(dispatcherReference.get(), close);
                terminationReturned.countDown();
                awaitInCallback(allowCallbackToFinish);
              }
              delivered.add(event);
              if (event == 4) {
                lastDelivered.countDown();
              }
            },
            duringDrain ? Duration.ofDays(1) : Duration.ofMillis(1));
    dispatcherReference.set(dispatcher);
    dispatcher.submit(1);
    var externalTerminator =
        duringDrain ? startThread(dispatcher::terminateAndDeliverPending) : null;
    try {
      assertReleased(terminationReturned);
      assertThat(dispatcher.isTerminated()).isFalse();
      assertThat(delivered).isEmpty();
      // Even a reentrant close must retain the latest event submitted before its callback returns.
      dispatcher.submit(4);
    } finally {
      allowCallbackToFinish.countDown();
    }
    assertReleased(lastDelivered);
    if (externalTerminator != null) {
      assertFinished(externalTerminator);
    }
    if (close) {
      assertFinished(schedulerThread.get());
    }
    assertThat(delivered).containsExactly(1, 4);
    assertFinished(startThread(dispatcher::terminateAndDeliverPending));
    assertThat(dispatcher.isTerminated()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void interruptedTerminationFinishesDeliveryAndRestoresInterrupt(
      boolean alreadyInterrupted, boolean close) throws InterruptedException {
    var firstStarted = new CountDownLatch(1);
    var allowFirstToFinish = new CountDownLatch(1);
    var callbackInterrupted = new AtomicBoolean();
    var callerInterrupted = new AtomicBoolean();
    List<Integer> delivered = new CopyOnWriteArrayList<>();
    var dispatcher =
        dispatcher(
            event -> {
              if (event == 1) {
                firstStarted.countDown();
                awaitInCallback(allowFirstToFinish, callbackInterrupted);
              }
              delivered.add(event);
            },
            Duration.ofMillis(1));
    dispatcher.submit(1);
    assertReleased(firstStarted);
    dispatcher.submit(2);
    var terminator =
        startThread(
            () -> {
              if (alreadyInterrupted) {
                Thread.currentThread().interrupt();
              }
              terminate(dispatcher, close);
              callerInterrupted.set(Thread.currentThread().isInterrupted());
            });
    try {
      assertWaiting(terminator);
      if (!alreadyInterrupted) {
        terminator.interrupt();
        await()
            .atMost(TEST_TIMEOUT)
            .until(() -> !terminator.isInterrupted() || !terminator.isAlive());
        assertWaiting(terminator);
      }
      assertThat(delivered).isEmpty();
    } finally {
      allowFirstToFinish.countDown();
    }
    assertFinished(terminator);
    assertThat(callerInterrupted).isTrue();
    assertThat(callbackInterrupted).isFalse();
    assertThat(delivered).containsExactly(1, 2);
    assertThat(dispatcher.isTerminated()).isTrue();
    dispatcher.submit(3);
    assertThat(delivered).containsExactly(1, 2, 3);
  }

  @Test
  void concurrentTerminatorsAndReentrantCloseShareTheDrain() throws InterruptedException {
    var dispatcherReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var firstStarted = new CountDownLatch(1);
    var allowReentrantClose = new CountDownLatch(1);
    var callerInterrupted = new AtomicBoolean();
    List<Integer> delivered = new CopyOnWriteArrayList<>();
    var dispatcher =
        dispatcher(
            event -> {
              if (event == 1) {
                firstStarted.countDown();
                awaitInCallback(allowReentrantClose);
                dispatcherReference.get().terminateAndDeliverPending();
                dispatcherReference.get().close();
              }
              delivered.add(event);
            },
            Duration.ofMillis(1));
    dispatcherReference.set(dispatcher);
    dispatcher.submit(1);
    assertReleased(firstStarted);
    dispatcher.submit(2);
    var closer = startThread(dispatcher::close);
    var terminator =
        startThread(
            () -> {
              dispatcher.terminateAndDeliverPending();
              callerInterrupted.set(Thread.currentThread().isInterrupted());
            });
    try {
      assertWaiting(closer);
      assertWaiting(terminator);
      terminator.interrupt();
      dispatcher.submit(3);
    } finally {
      allowReentrantClose.countDown();
    }
    assertFinished(closer);
    assertFinished(terminator);
    assertThat(callerInterrupted).isTrue();
    assertThat(delivered).containsExactly(1, 3);
    assertThat(dispatcher.isTerminated()).isTrue();
  }

  @Test
  void exceptionAfterReentrantCloseDoesNotDiscardPendingDelivery() throws InterruptedException {
    var dispatcherReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var schedulerThread = new AtomicReference<Thread>();
    var pendingDelivered = new CountDownLatch(1);
    var dispatcher =
        dispatcher(
            event -> {
              schedulerThread.set(Thread.currentThread());
              if (event == 1) {
                dispatcherReference.get().submit(2);
                dispatcherReference.get().close();
                throw new IllegalStateException("Expected callback failure after close");
              }
              pendingDelivered.countDown();
            },
            Duration.ofMillis(1));
    dispatcherReference.set(dispatcher);
    dispatcher.submit(1);
    assertReleased(pendingDelivered);
    assertFinished(schedulerThread.get());
    assertThat(dispatcher.isTerminated()).isTrue();
  }

  @Test
  void callbacksCanCloseEachOthersDispatchers() throws InterruptedException {
    var firstReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var secondReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var callbacksStarted = new CountDownLatch(2);
    var allowClose = new CountDownLatch(1);
    var closeReturned = new CountDownLatch(2);
    var lastDelivered = new CountDownLatch(2);
    var schedulerThreads = new CopyOnWriteArrayList<Thread>();
    List<Integer> firstDelivered = new CopyOnWriteArrayList<>();
    List<Integer> secondDelivered = new CopyOnWriteArrayList<>();
    var first =
        dispatcher(
            event -> {
              if (event == 1) {
                schedulerThreads.add(Thread.currentThread());
                firstReference.get().submit(2);
                callbacksStarted.countDown();
                awaitInCallback(allowClose);
                secondReference.get().close();
                closeReturned.countDown();
              }
              firstDelivered.add(event);
              if (event == 2) {
                lastDelivered.countDown();
              }
            },
            Duration.ofMillis(1));
    var second =
        dispatcher(
            event -> {
              if (event == 1) {
                schedulerThreads.add(Thread.currentThread());
                secondReference.get().submit(2);
                callbacksStarted.countDown();
                awaitInCallback(allowClose);
                firstReference.get().close();
                closeReturned.countDown();
              }
              secondDelivered.add(event);
              if (event == 2) {
                lastDelivered.countDown();
              }
            },
            Duration.ofMillis(1));
    firstReference.set(first);
    secondReference.set(second);
    first.submit(1);
    second.submit(1);
    try {
      assertReleased(callbacksStarted);
    } finally {
      allowClose.countDown();
    }
    assertReleased(closeReturned);
    assertReleased(lastDelivered);
    for (var schedulerThread : schedulerThreads) {
      assertFinished(schedulerThread);
    }
    assertThat(firstDelivered).containsExactly(1, 2);
    assertThat(secondDelivered).containsExactly(1, 2);
    assertThat(first.isTerminated()).isTrue();
    assertThat(second.isTerminated()).isTrue();
  }

  @Test
  void synchronousCallbackCanCloseDispatcherThatIsSubmittingToIt() throws InterruptedException {
    var secondReference = new AtomicReference<ThrottledEventDispatcher<Integer>>();
    var schedulerThread = new AtomicReference<Thread>();
    var firstStarted = new CountDownLatch(1);
    var secondStarted = new CountDownLatch(1);
    var allowClose = new CountDownLatch(1);
    var allowSubmit = new CountDownLatch(1);
    var closeReturned = new CountDownLatch(1);
    var submitReturned = new CountDownLatch(1);
    var callerContextRestored = new AtomicBoolean();
    List<Integer> firstDelivered = new CopyOnWriteArrayList<>();
    var first =
        dispatcher(
            event -> {
              if (event == 1) {
                firstStarted.countDown();
                awaitInCallback(allowClose);
                secondReference.get().close();
                closeReturned.countDown();
              }
              firstDelivered.add(event);
            },
            Duration.ofDays(1));
    var second =
        dispatcher(
            event -> {
              schedulerThread.set(Thread.currentThread());
              secondStarted.countDown();
              awaitInCallback(allowSubmit);
              first.submit(2);
              submitReturned.countDown();
            },
            Duration.ofMillis(1));
    secondReference.set(second);
    assertFinished(startThread(first::close));
    var synchronousSubmitter =
        startThread(
            () -> {
              first.submit(1);
              callerContextRestored.set(!SolverEventThreadContext.isActive());
            });
    try {
      assertReleased(firstStarted);
      second.submit(1);
      assertReleased(secondStarted);
      allowSubmit.countDown();
      // The second callback must already be waiting for the first callback's delivery lock.
      await()
          .atMost(TEST_TIMEOUT)
          .until(
              () ->
                  schedulerThread.get().getState() == Thread.State.BLOCKED
                      || submitReturned.getCount() == 0);
      assertThat(submitReturned.getCount()).isEqualTo(1L);
      allowClose.countDown();
      assertReleased(closeReturned);
      assertReleased(submitReturned);
    } finally {
      allowClose.countDown();
      allowSubmit.countDown();
    }
    assertFinished(synchronousSubmitter);
    assertFinished(schedulerThread.get());
    assertThat(firstDelivered).containsExactly(1, 2);
    assertThat(callerContextRestored).isTrue();
    assertThat(second.isTerminated()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void eventsAfterTerminationAreSynchronousAndSerialized(boolean close)
      throws InterruptedException {
    var firstStarted = new CountDownLatch(1);
    var allowFirstToFinish = new CountDownLatch(1);
    var secondStarted = new AtomicBoolean();
    var callbackThreads = new CopyOnWriteArrayList<Thread>();
    List<Integer> delivered = new CopyOnWriteArrayList<>();
    var dispatcher =
        dispatcher(
            event -> {
              callbackThreads.add(Thread.currentThread());
              if (event == 1) {
                firstStarted.countDown();
                awaitInCallback(allowFirstToFinish);
              } else {
                secondStarted.set(true);
              }
              delivered.add(event);
            },
            Duration.ofDays(1));
    assertFinished(startThread(() -> terminate(dispatcher, close)));
    var firstSubmitter = startThread(() -> dispatcher.submit(1));
    assertReleased(firstStarted);
    var secondSubmitter = startThread(() -> dispatcher.submit(2));
    try {
      await()
          .atMost(TEST_TIMEOUT)
          .until(
              () ->
                  secondSubmitter.getState() == Thread.State.BLOCKED || !secondSubmitter.isAlive());
      assertThat(secondSubmitter.isAlive()).isTrue();
      assertThat(secondStarted).isFalse();
    } finally {
      allowFirstToFinish.countDown();
    }
    assertFinished(firstSubmitter);
    assertFinished(secondSubmitter);
    assertThat(delivered).containsExactly(1, 2);
    assertThat(callbackThreads).containsExactly(firstSubmitter, secondSubmitter);
  }

  @Test
  void continuousStreamDeliversBeforeProducerStopsAndRespectsInterval()
      throws InterruptedException {
    var throttleDuration = Duration.ofMillis(50);
    var fourDeliveries = new CountDownLatch(4);
    var stopProducer = new AtomicBoolean();
    var lastSubmitted = new AtomicInteger();
    var deliveryTimes = new CopyOnWriteArrayList<Long>();
    List<Integer> delivered = new CopyOnWriteArrayList<>();
    var dispatcher =
        dispatcher(
            event -> {
              long deliveryTime = System.nanoTime();
              delivered.add(event);
              if (!stopProducer.get()) {
                deliveryTimes.add(deliveryTime);
                fourDeliveries.countDown();
              }
            },
            throttleDuration);
    var producer =
        startThread(
            () -> {
              while (!stopProducer.get()) {
                dispatcher.submit(lastSubmitted.incrementAndGet());
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
              }
            });
    try {
      // The producer remains active until four callbacks arrive; debouncing cannot pass this check.
      assertReleased(fourDeliveries);
    } finally {
      stopProducer.set(true);
      assertFinished(producer);
    }
    assertFinished(startThread(dispatcher::terminateAndDeliverPending));
    assertThat(deliveryTimes).hasSizeGreaterThanOrEqualTo(4);
    for (int i = 1; i < deliveryTimes.size(); i++) {
      // Allow for the small gap between the dispatcher starting delivery and this timestamp.
      assertThat(deliveryTimes.get(i) - deliveryTimes.get(i - 1))
          .isGreaterThanOrEqualTo(throttleDuration.minusMillis(2).toNanos());
    }
    assertThat(delivered.getLast()).isEqualTo(lastSubmitted.get());
  }

  private ThrottledEventDispatcher<Integer> dispatcher(
      Consumer<Integer> delegate, Duration duration) {
    var dispatcher =
        new ThrottledEventDispatcher<>(
            LoggerFactory.getLogger(ThrottledEventDispatcherTest.class),
            delegate,
            duration,
            "throttled-dispatcher-test");
    dispatchers.add(dispatcher);
    return dispatcher;
  }

  private static void terminate(ThrottledEventDispatcher<?> dispatcher, boolean close) {
    if (close) {
      dispatcher.close();
    } else {
      dispatcher.terminateAndDeliverPending();
    }
  }

  private void awaitInCallback(CountDownLatch latch) {
    awaitInCallback(latch, new AtomicBoolean());
  }

  private void awaitInCallback(CountDownLatch latch, AtomicBoolean interrupted) {
    try {
      while (true) {
        try {
          if (!latch.await(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            callbackFailures.add(
                new AssertionError("Timed out waiting for the test to release a callback."));
          }
          return;
        } catch (InterruptedException e) {
          interrupted.set(true);
        }
      }
    } finally {
      if (interrupted.get()) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static Thread startThread(Runnable runnable) {
    return Thread.ofPlatform().daemon().start(runnable);
  }

  private static void assertReleased(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
  }

  private static void assertWaiting(Thread thread) {
    await()
        .atMost(TEST_TIMEOUT)
        .until(() -> thread.getState() == Thread.State.WAITING || !thread.isAlive());
    assertThat(thread.isAlive()).isTrue();
  }

  private static void assertFinished(Thread thread) throws InterruptedException {
    thread.join(TEST_TIMEOUT.toMillis());
    assertThat(thread.isAlive()).isFalse();
  }
}
