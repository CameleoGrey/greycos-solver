package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.NewBestSolutionEvent;

import org.junit.jupiter.api.Test;

class ThrottlingBestSolutionEventConsumerTest {

  private static final Duration THROTTLE_DURATION = Duration.ofMillis(100);
  private static final Duration LONG_THROTTLE_DURATION = Duration.ofDays(1);
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

  @Test
  void of_createsValidInstance() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    assertThat(throttler).isNotNull();
    throttler.close();
  }

  @Test
  void of_throwsOnNullDelegate() {
    assertThatThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(null, THROTTLE_DURATION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("delegate");
  }

  @Test
  void of_throwsOnNullDuration() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);

    assertThatThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(delegate, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("throttleDuration");
  }

  @Test
  void of_throwsOnZeroDuration() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(delegate, Duration.ZERO))
        .withMessageContaining("positive");
  }

  @Test
  void of_throwsOnNegativeDuration() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(delegate, Duration.ofMillis(-1)))
        .withMessageContaining("positive");
  }

  @Test
  void singleEvent_deliveredAfterThrottleDuration() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);
    throttler.accept(event);

    assertReleased(delivered);

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void multipleRapidEvents_onlyLastDelivered() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var event3 = createEvent("solution3");
    var deliveredEvent = new AtomicReference<NewBestSolutionEvent<String>>();

    doAnswer(
            invocation -> {
              deliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);

    throttler.accept(event1);
    throttler.accept(event2);
    throttler.accept(event3);
    throttler.terminateAndDeliverPending();

    verify(delegate).accept(event3);
    verify(delegate, never()).accept(event1);
    verify(delegate, never()).accept(event2);
    assertThat(deliveredEvent.get()).isSameAs(event3);
    throttler.close();
  }

  @Test
  void continuousRapidEvents_deliverPeriodically() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var deliveryCount = new AtomicInteger(0);
    var lastDeliveredEvent = new AtomicReference<NewBestSolutionEvent<String>>();
    var firstEventDelivered = new CountDownLatch(1);
    var lastEventDelivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              var event = invocation.<NewBestSolutionEvent<String>>getArgument(0);
              deliveryCount.incrementAndGet();
              lastDeliveredEvent.set(event);
              if (event.solution().equals("solution0")) {
                firstEventDelivered.countDown();
              } else if (event.solution().equals("solution9")) {
                lastEventDelivered.countDown();
              }
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    throttler.accept(createEvent("solution0"));
    assertReleased(firstEventDelivered);

    for (int i = 1; i < 10; i++) {
      throttler.accept(createEvent("solution" + i));
    }
    assertReleased(lastEventDelivered);

    assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
    assertThat(lastDeliveredEvent.get().solution()).isEqualTo("solution9");
    throttler.close();
  }

  @Test
  void eventNotDeliveredImmediately() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new AtomicBoolean(false);

    doAnswer(
            invocation -> {
              delivered.set(true);
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);
    throttler.accept(event);

    assertThat(delivered.get()).isFalse();

    throttler.close();
  }

  @Test
  void terminateAndDeliverPending_deliversImmediately() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);
    throttler.accept(event);

    assertThat(delivered.getCount()).isEqualTo(1L);

    throttler.terminateAndDeliverPending();

    assertThat(delivered.getCount()).isZero();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void terminateAndDeliverPending_isIdempotent() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);
    throttler.accept(event);

    throttler.terminateAndDeliverPending();
    throttler.terminateAndDeliverPending();
    throttler.terminateAndDeliverPending();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void eventAfterTermination_deliveredImmediately() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);
    throttler.terminateAndDeliverPending();

    throttler.accept(event);

    assertThat(delivered.getCount()).isZero();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void close_terminatesAndDeliversPending() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);
    throttler.accept(event);
    throttler.close();

    assertThat(delivered.getCount()).isZero();

    verify(delegate).accept(event);
  }

  @Test
  void delegateException_doesNotBreakThrottler() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var deliveryCount = new AtomicInteger(0);
    var firstDeliveryAttempted = new CountDownLatch(1);
    var secondEventDelivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              if (deliveryCount.incrementAndGet() == 1) {
                firstDeliveryAttempted.countDown();
                throw new RuntimeException("Test exception");
              }
              secondEventDelivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    throttler.accept(event1);
    assertReleased(firstDeliveryAttempted);

    throttler.accept(event2);
    assertReleased(secondEventDelivered);

    assertThat(deliveryCount.get()).isEqualTo(2);
    throttler.close();
  }

  @Test
  void nullEvent_throwsNPE() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    assertThatThrownBy(() -> throttler.accept(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("event");

    throttler.close();
  }

  @Test
  void concurrentAccept_isThreadSafe() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var deliveryCount = new AtomicInteger(0);
    var allThreadsReady = new CountDownLatch(10);
    var startThreads = new CountDownLatch(1);
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              allThreadsReady.countDown();
              try {
                startThreads.await();
                var event = createEvent("solution" + index);
                throttler.accept(event);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                allThreadsFinished.countDown();
              }
            });
      }

      assertReleased(allThreadsReady);
      startThreads.countDown();
      assertReleased(allThreadsFinished);
      throttler.terminateAndDeliverPending();

      assertThat(deliveryCount.get()).isEqualTo(1);
    } finally {
      startThreads.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      throttler.close();
    }
  }

  @Test
  void terminateDuringConcurrency_isSafe() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var deliveryCount = new AtomicInteger(0);
    var deliveredEvent = new AtomicReference<NewBestSolutionEvent<String>>();
    var allThreadsReady = new CountDownLatch(10);
    var startThreads = new CountDownLatch(1);
    var allThreadsFinished = new CountDownLatch(10);
    var terminationReady = new CountDownLatch(1);
    var terminationFinished = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              deliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, LONG_THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    var terminationThread =
        new Thread(
            () -> {
              terminationReady.countDown();
              try {
                startThreads.await();
                throttler.terminateAndDeliverPending();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                terminationFinished.countDown();
              }
            });
    try {
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              allThreadsReady.countDown();
              try {
                startThreads.await();
                var event = createEvent("solution" + index);
                throttler.accept(event);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                allThreadsFinished.countDown();
              }
            });
      }
      terminationThread.start();

      assertReleased(allThreadsReady);
      assertReleased(terminationReady);
      startThreads.countDown();
      assertReleased(allThreadsFinished);
      assertReleased(terminationFinished);

      assertThat(deliveryCount.get()).isBetween(1, 10);
      assertThat(deliveredEvent.get()).isNotNull();
    } finally {
      startThreads.countDown();
      terminationThread.interrupt();
      terminationThread.join(TEST_TIMEOUT.toMillis());
      assertThat(terminationThread.isAlive()).isFalse();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      throttler.close();
    }
  }

  @Test
  void terminateWhileDeliveryInProgress_flushesPendingEventWithoutLoss()
      throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var firstDeliveryStarted = new CountDownLatch(1);
    var allowFirstDeliveryToFinish = new CountDownLatch(1);
    List<String> deliveredSolutions = Collections.synchronizedList(new ArrayList<>());
    var deliveryCount = new AtomicInteger(0);

    doAnswer(
            invocation -> {
              var event = invocation.<NewBestSolutionEvent<String>>getArgument(0);
              if (deliveryCount.getAndIncrement() == 0) {
                firstDeliveryStarted.countDown();
                allowFirstDeliveryToFinish.await();
              }
              deliveredSolutions.add(event.solution());
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);
    throttler.accept(createEvent("solution1"));

    assertReleased(firstDeliveryStarted);

    throttler.accept(createEvent("solution2"));
    var terminationThread = new Thread(throttler::terminateAndDeliverPending);
    terminationThread.start();
    allowFirstDeliveryToFinish.countDown();
    terminationThread.join(TEST_TIMEOUT.toMillis());

    assertThat(terminationThread.isAlive()).isFalse();
    assertThat(deliveredSolutions).containsExactly("solution1", "solution2");
    throttler.close();
  }

  @Test
  void subMillisecondDuration_isAccepted() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(event);

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, Duration.ofNanos(1));
    throttler.accept(event);

    assertReleased(delivered);
    throttler.close();
  }

  private static void assertReleased(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
  }

  private NewBestSolutionEvent<String> createEvent(String solution) {
    return new NewBestSolutionEvent<>() {
      @Override
      public String solution() {
        return solution;
      }

      @Override
      public EventProducerId producerId() {
        return EventProducerId.solvingStarted();
      }
    };
  }
}
