package ai.greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.api.solver.event.SolverEventListener;

import org.junit.jupiter.api.Test;

class ThrottlingSolverEventListenerTest {

  private static final Duration THROTTLE_DURATION = Duration.ofMillis(100);
  private static final Duration WAIT_TOLERANCE = Duration.ofMillis(50);

  @Test
  void of_createsValidInstance() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    assertThat(listener).isNotNull();
    listener.close();
  }

  @Test
  void of_throwsOnNullDelegate() {
    assertThatThrownBy(() -> ThrottlingSolverEventListener.of(null, THROTTLE_DURATION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("delegate");
  }

  @Test
  void of_throwsOnNullDuration() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);

    assertThatThrownBy(() -> ThrottlingSolverEventListener.of(delegate, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("throttleDuration");
  }

  @Test
  void of_throwsOnZeroDuration() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> ThrottlingSolverEventListener.of(delegate, Duration.ZERO))
        .withMessageContaining("positive");
  }

  @Test
  void of_throwsOnNegativeDuration() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> ThrottlingSolverEventListener.of(delegate, Duration.ofMillis(-1)))
        .withMessageContaining("positive");
  }

  @Test
  void singleEvent_deliveredAfterThrottleDuration() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(event);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    boolean deliveredBeforeTimeout =
        delivered.await(
            THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(deliveredBeforeTimeout).isTrue();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void multipleRapidEvents_onlyLastDelivered() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var event3 = createEvent("solution3");
    var deliveredEvent = new AtomicReference<BestSolutionChangedEvent<String>>();
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              deliveredEvent.set(invocation.getArgument(0));
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    listener.bestSolutionChanged(event1);
    Thread.sleep(20); // Less than throttle duration
    listener.bestSolutionChanged(event2);
    Thread.sleep(20); // Less than throttle duration
    listener.bestSolutionChanged(event3);

    delivered.await(
        THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);

    verify(delegate).bestSolutionChanged(event3);
    verify(delegate, never()).bestSolutionChanged(event1);
    verify(delegate, never()).bestSolutionChanged(event2);
    assertThat(deliveredEvent.get()).isSameAs(event3);
    listener.close();
  }

  @Test
  void eventNotDeliveredImmediately() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");
    var delivered = new AtomicBoolean(false);

    doAnswer(
            invocation -> {
              delivered.set(true);
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(event);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    Thread.sleep(THROTTLE_DURATION.toMillis() / 2);
    assertThat(delivered.get()).isFalse();

    listener.close();
  }

  @Test
  void terminateAndDeliverPending_deliversImmediately() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(event);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    boolean deliveredImmediately = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredImmediately).isFalse();

    listener.terminateAndDeliverPending();

    boolean deliveredAfterTerminate = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredAfterTerminate).isTrue();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void terminateAndDeliverPending_isIdempotent() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    listener.terminateAndDeliverPending();
    listener.terminateAndDeliverPending();
    listener.terminateAndDeliverPending();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void eventAfterTermination_deliveredImmediately() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(event);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.terminateAndDeliverPending();

    listener.bestSolutionChanged(event);

    boolean deliveredImmediately = delivered.await(10, TimeUnit.MILLISECONDS);
    assertThat(deliveredImmediately).isTrue();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void close_terminatesAndDeliversPending() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");
    var delivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              delivered.countDown();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(event);

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(event);
    listener.close();

    boolean deliveredAfterClose = delivered.await(50, TimeUnit.MILLISECONDS);
    assertThat(deliveredAfterClose).isTrue();

    verify(delegate).bestSolutionChanged(event);
  }

  @Test
  void delegateException_doesNotBreakThrottler() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
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
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    listener.bestSolutionChanged(event1);
    Thread.sleep(THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis());

    listener.bestSolutionChanged(event2);
    delivered.await(
        THROTTLE_DURATION.toMillis() + WAIT_TOLERANCE.toMillis(), TimeUnit.MILLISECONDS);

    assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
    listener.close();
  }

  @Test
  void nullEvent_throwsNPE() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    assertThatThrownBy(() -> listener.bestSolutionChanged(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("event");

    listener.close();
  }

  @Test
  void concurrentBestSolutionChanged_isThreadSafe() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var deliveryCount = new AtomicInteger(0);
    var allThreadsStarted = new CountDownLatch(10);
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

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
              listener.bestSolutionChanged(event);
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
      listener.close();
    }
  }

  @Test
  void terminateDuringConcurrency_isSafe() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var deliveryCount = new AtomicInteger(0);
    var deliveredEvent = new AtomicReference<BestSolutionChangedEvent<String>>();
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              deliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        final int index = i;
        executor.submit(
            () -> {
              var event = createEvent("solution" + index);
              listener.bestSolutionChanged(event);
              allThreadsFinished.countDown();
            });
      }

      allThreadsFinished.await(50, TimeUnit.MILLISECONDS);
      listener.terminateAndDeliverPending();
      Thread.sleep(50);

      assertThat(deliveryCount.get()).isGreaterThan(0);
      assertThat(deliveredEvent.get()).isNotNull();
    } finally {
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);
      listener.close();
    }
  }

  private BestSolutionChangedEvent<String> createEvent(String solution) {
    return new BestSolutionChangedEvent<>(
        null, // solver
        EventProducerId.unknown(),
        100L, // timeMillisSpent
        solution,
        SimpleScore.ZERO,
        true // isNewBestSolutionInitialized
        );
  }
}
