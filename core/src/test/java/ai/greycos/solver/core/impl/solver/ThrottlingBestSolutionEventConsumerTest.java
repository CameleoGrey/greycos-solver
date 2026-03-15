package ai.greycos.solver.core.impl.solver;

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

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;

import org.junit.jupiter.api.Test;

class ThrottlingBestSolutionEventConsumerTest {

  private static final Duration THROTTLE_DURATION = Duration.ofMillis(100);
  private static final Duration WAIT_TOLERANCE = Duration.ofMillis(50);

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

    boolean deliveredBeforeTimeout =
        delivered.await(
            THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(deliveredBeforeTimeout).isTrue();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void multipleRapidEvents_onlyLastDelivered() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var event3 = createEvent("solution3");
    var deliveredEvent = new AtomicReference<NewBestSolutionEvent<String>>();
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              deliveredEvent.set(invocation.getArgument(0));
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    throttler.accept(event1);
    Thread.sleep(20); // Less than throttle duration
    throttler.accept(event2);
    Thread.sleep(20); // Less than throttle duration
    throttler.accept(event3);

    delivered.await(
        THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);

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

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              lastDeliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    for (int i = 0; i < 10; i++) {
      throttler.accept(createEvent("solution" + i));
      Thread.sleep(40);
    }
    Thread.sleep(THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis());

    assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
    assertThat(lastDeliveredEvent.get().solution()).isEqualTo("solution9");
    throttler.close();
  }

  @Test
  void eventNotDeliveredImmediately() throws InterruptedException {
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

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);
    throttler.accept(event);

    Thread.sleep(THROTTLE_DURATION.toMillis() / 2);
    assertThat(delivered.get()).isFalse();

    throttler.close();
  }

  @Test
  void terminateAndDeliverPending_deliversImmediately() throws InterruptedException {
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

    boolean deliveredImmediately = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredImmediately).isFalse();

    throttler.terminateAndDeliverPending();

    boolean deliveredAfterTerminate = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredAfterTerminate).isTrue();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void terminateAndDeliverPending_isIdempotent() {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event = createEvent("solution1");

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);
    throttler.accept(event);

    throttler.terminateAndDeliverPending();
    throttler.terminateAndDeliverPending();
    throttler.terminateAndDeliverPending();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void eventAfterTermination_deliveredImmediately() throws InterruptedException {
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

    boolean deliveredImmediately = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredImmediately).isTrue();

    verify(delegate).accept(event);
    throttler.close();
  }

  @Test
  void close_terminatesAndDeliversPending() throws InterruptedException {
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
    throttler.close();

    boolean deliveredAfterClose = delivered.await(50, TimeUnit.MILLISECONDS);
    assertThat(deliveredAfterClose).isTrue();

    verify(delegate).accept(event);
  }

  @Test
  void delegateException_doesNotBreakThrottler() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var deliveryCount = new AtomicInteger(0);
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              if (deliveryCount.get() == 1) {
                throw new RuntimeException("Test exception");
              }
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    throttler.accept(event1);
    Thread.sleep(THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis());

    throttler.accept(event2);
    delivered.await(
        THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);

    assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
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
    var allThreadsStarted = new CountDownLatch(10);
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              allThreadsStarted.countDown();
              try {
                allThreadsStarted.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              var event = createEvent("solution" + index);
              throttler.accept(event);
              allThreadsFinished.countDown();
            });
      }

      allThreadsFinished.await();
      Thread.sleep(THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis());

      assertThat(deliveryCount.get()).isGreaterThan(0);
      assertThat(deliveryCount.get()).isLessThanOrEqualTo(10);
    } finally {
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
      throttler.close();
    }
  }

  @Test
  void terminateDuringConcurrency_isSafe() throws InterruptedException {
    Consumer<NewBestSolutionEvent<String>> delegate = mock(Consumer.class);
    var deliveryCount = new AtomicInteger(0);
    var deliveredEvent = new AtomicReference<NewBestSolutionEvent<String>>();
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              deliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .accept(any());

    var throttler = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              var event = createEvent("solution" + index);
              throttler.accept(event);
              allThreadsFinished.countDown();
            });
      }

      allThreadsFinished.await(50, TimeUnit.MILLISECONDS);
      throttler.terminateAndDeliverPending();
      Thread.sleep(50);

      assertThat(deliveryCount.get()).isGreaterThan(0);
      assertThat(deliveredEvent.get()).isNotNull();
    } finally {
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
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

    assertThat(
            firstDeliveryStarted.await(
                THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS))
        .isTrue();

    throttler.accept(createEvent("solution2"));
    var terminationThread = new Thread(throttler::terminateAndDeliverPending);
    terminationThread.start();
    allowFirstDeliveryToFinish.countDown();
    terminationThread.join(TimeUnit.SECONDS.toMillis(1));

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

    assertThat(delivered.await(1, TimeUnit.SECONDS)).isTrue();
    throttler.close();
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
