package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.SolverEventListener;

import org.junit.jupiter.api.Test;

class ThrottlingSolverEventListenerTest {

  private static final Duration THROTTLE_DURATION = Duration.ofMillis(100);
  private static final Duration LONG_THROTTLE_DURATION = Duration.ofDays(1);
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

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

    assertReleased(delivered);

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void multipleRapidEvents_onlyLastDelivered() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event1 = createEvent("solution1");
    var event2 = createEvent("solution2");
    var event3 = createEvent("solution3");
    var deliveredEvent = new AtomicReference<BestSolutionChangedEvent<String>>();

    doAnswer(
            invocation -> {
              deliveredEvent.set(invocation.getArgument(0));
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);

    listener.bestSolutionChanged(event1);
    listener.bestSolutionChanged(event2);
    listener.bestSolutionChanged(event3);
    listener.terminateAndDeliverPending();

    verify(delegate).bestSolutionChanged(event3);
    verify(delegate, never()).bestSolutionChanged(event1);
    verify(delegate, never()).bestSolutionChanged(event2);
    assertThat(deliveredEvent.get()).isSameAs(event3);
    listener.close();
  }

  @Test
  void continuousRapidEvents_deliverPeriodically() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var deliveryCount = new AtomicInteger(0);
    var lastDeliveredEvent = new AtomicReference<BestSolutionChangedEvent<String>>();
    var firstEventDelivered = new CountDownLatch(1);
    var lastEventDelivered = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              var event = invocation.<BestSolutionChangedEvent<String>>getArgument(0);
              deliveryCount.incrementAndGet();
              lastDeliveredEvent.set(event);
              if (event.getNewBestSolution().equals("solution0")) {
                firstEventDelivered.countDown();
              } else if (event.getNewBestSolution().equals("solution9")) {
                lastEventDelivered.countDown();
              }
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    listener.bestSolutionChanged(createEvent("solution0"));
    assertReleased(firstEventDelivered);

    for (int i = 1; i < 10; i++) {
      listener.bestSolutionChanged(createEvent("solution" + i));
    }
    assertReleased(lastEventDelivered);

    assertThat(deliveryCount.get()).isGreaterThanOrEqualTo(2);
    assertThat(lastDeliveredEvent.get().getNewBestSolution()).isEqualTo("solution9");
    listener.close();
  }

  @Test
  void eventNotDeliveredImmediately() {
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

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    assertThat(delivered.get()).isFalse();

    listener.close();
  }

  @Test
  void terminateAndDeliverPending_deliversImmediately() {
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

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    assertThat(delivered.getCount()).isEqualTo(1L);

    listener.terminateAndDeliverPending();

    assertThat(delivered.getCount()).isZero();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void terminateAndDeliverPending_isIdempotent() {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var event = createEvent("solution1");

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);
    listener.bestSolutionChanged(event);

    listener.terminateAndDeliverPending();
    listener.terminateAndDeliverPending();
    listener.terminateAndDeliverPending();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void eventAfterTermination_deliveredImmediately() {
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

    assertThat(delivered.getCount()).isZero();

    verify(delegate).bestSolutionChanged(event);
    listener.close();
  }

  @Test
  void close_terminatesAndDeliversPending() {
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

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);
    listener.bestSolutionChanged(event);
    listener.close();

    assertThat(delivered.getCount()).isZero();

    verify(delegate).bestSolutionChanged(event);
  }

  @Test
  void delegateException_doesNotBreakThrottler() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
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
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);

    listener.bestSolutionChanged(event1);
    assertReleased(firstDeliveryAttempted);

    listener.bestSolutionChanged(event2);
    assertReleased(secondEventDelivered);

    assertThat(deliveryCount.get()).isEqualTo(2);
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
    var allThreadsReady = new CountDownLatch(10);
    var startThreads = new CountDownLatch(1);
    var allThreadsFinished = new CountDownLatch(10);

    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);

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
                listener.bestSolutionChanged(event);
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
      listener.terminateAndDeliverPending();

      assertThat(deliveryCount.get()).isEqualTo(1);
    } finally {
      startThreads.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
      listener.close();
    }
  }

  @Test
  void terminateDuringConcurrency_isSafe() throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var deliveryCount = new AtomicInteger(0);
    var deliveredEvent = new AtomicReference<BestSolutionChangedEvent<String>>();
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
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, LONG_THROTTLE_DURATION);

    ExecutorService executor = Executors.newFixedThreadPool(10);
    var terminationThread =
        new Thread(
            () -> {
              terminationReady.countDown();
              try {
                startThreads.await();
                listener.terminateAndDeliverPending();
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
                listener.bestSolutionChanged(event);
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
      listener.close();
    }
  }

  @Test
  void terminateWhileDeliveryInProgress_flushesPendingEventWithoutLoss()
      throws InterruptedException {
    SolverEventListener<String> delegate = mock(SolverEventListener.class);
    var firstDeliveryStarted = new CountDownLatch(1);
    var allowFirstDeliveryToFinish = new CountDownLatch(1);
    List<String> deliveredSolutions = Collections.synchronizedList(new ArrayList<>());
    var deliveryCount = new AtomicInteger(0);

    doAnswer(
            invocation -> {
              var event = invocation.<BestSolutionChangedEvent<String>>getArgument(0);
              if (deliveryCount.getAndIncrement() == 0) {
                firstDeliveryStarted.countDown();
                allowFirstDeliveryToFinish.await();
              }
              deliveredSolutions.add(event.getNewBestSolution());
              return null;
            })
        .when(delegate)
        .bestSolutionChanged(any());

    var listener = ThrottlingSolverEventListener.of(delegate, THROTTLE_DURATION);
    listener.bestSolutionChanged(createEvent("solution1"));

    assertReleased(firstDeliveryStarted);

    listener.bestSolutionChanged(createEvent("solution2"));
    var terminationThread = new Thread(listener::terminateAndDeliverPending);
    terminationThread.start();
    allowFirstDeliveryToFinish.countDown();
    terminationThread.join(TEST_TIMEOUT.toMillis());

    assertThat(terminationThread.isAlive()).isFalse();
    assertThat(deliveredSolutions).containsExactly("solution1", "solution2");
    listener.close();
  }

  @Test
  void subMillisecondDuration_isAccepted() throws InterruptedException {
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

    var listener = ThrottlingSolverEventListener.of(delegate, Duration.ofNanos(1));
    listener.bestSolutionChanged(event);

    assertReleased(delivered);
    listener.close();
  }

  private static void assertReleased(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
  }

  private BestSolutionChangedEvent<String> createEvent(String solution) {
    return new BestSolutionChangedEvent<>() {
      @Override
      public long getTimeMillisSpent() {
        return 100L;
      }

      @Override
      public EventProducerId getProducerId() {
        return EventProducerId.solvingStarted();
      }

      @Override
      public String getNewBestSolution() {
        return solution;
      }

      @Override
      public Score getNewBestScore() {
        return SimpleScore.ZERO;
      }

      @Override
      public boolean isNewBestSolutionInitialized() {
        return true;
      }

      @Override
      public boolean isEveryProblemChangeProcessed() {
        return true;
      }
    };
  }
}
