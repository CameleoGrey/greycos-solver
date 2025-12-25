package ai.greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ThrottlingBestSolutionEventConsumer}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Basic throttling behavior
 *   <li>Skip-ahead logic (only last event delivered)
 *   <li>Final delivery on termination
 *   <li>Exception handling
 *   <li>Thread safety
 *   <li>Resource cleanup
 * </ul>
 */
class ThrottlingBestSolutionEventConsumerTest {

  private static final Duration THROTTLE_DURATION = Duration.ofMillis(100);

  @Mock private Consumer<NewBestSolutionEvent<String>> mockDelegate;

  private AutoCloseable mockitoCloseable;

  @BeforeEach
  void setUp() {
    mockitoCloseable = MockitoAnnotations.openMocks(this);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockitoCloseable.close();
  }

  @Test
  void factoryMethod_createsConsumer() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);

    assertThat(consumer).isNotNull();
    assertThat(consumer.isTerminated()).isFalse();
  }

  @Test
  void factoryMethod_nullDelegate_throwsException() {
    assertThatThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(null, THROTTLE_DURATION))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("delegate");
  }

  @Test
  void factoryMethod_nullDuration_throwsException() {
    assertThatThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(mockDelegate, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("throttleDuration");
  }

  @Test
  void factoryMethod_zeroDuration_throwsException() {
    assertThatThrownBy(() -> ThrottlingBestSolutionEventConsumer.of(mockDelegate, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void factoryMethod_negativeDuration_throwsException() {
    assertThatThrownBy(
            () -> ThrottlingBestSolutionEventConsumer.of(mockDelegate, Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void basicThrottling_multipleEventsWithinInterval_onlyLastDelivered()
      throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    // Capture delivered events
    doAnswer(
            invocation -> {
              deliveredEvents.add(invocation.getArgument(0));
              return null;
            })
        .when(mockDelegate)
        .accept(any());

    // Submit multiple events rapidly
    for (int i = 0; i < 5; i++) {
      consumer.accept(createEvent("solution-" + i));
    }

    // Wait for throttle interval
    Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

    // Only the last event should be delivered
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-4");
  }

  @Test
  void intervalBoundary_eventsJustBeforeAndAfter_bothDelivered() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit first event
    consumer.accept(createEvent("solution-1"));

    // Wait for throttle interval
    Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

    // Submit second event
    consumer.accept(createEvent("solution-2"));

    // Wait for second delivery
    Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

    // Both events should be delivered
    verify(mockDelegate, times(2)).accept(any());
    assertThat(deliveredEvents).hasSize(2);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-1");
    assertThat(deliveredEvents.get(1).solution()).isEqualTo("solution-2");
  }

  @Test
  void finalDelivery_termination_deliversPendingEvent() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit event and terminate immediately
    consumer.accept(createEvent("solution-1"));
    consumer.terminateAndDeliverPending();

    // Event should be delivered immediately
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-1");
  }

  @Test
  void finalDelivery_multiplePendingEvents_onlyLastDelivered() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit multiple events
    for (int i = 0; i < 5; i++) {
      consumer.accept(createEvent("solution-" + i));
    }

    // Terminate
    consumer.terminateAndDeliverPending();

    // Only last event should be delivered
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-4");
  }

  @Test
  void finalDelivery_noEvents_noDelivery() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);

    // Terminate without any events
    consumer.terminateAndDeliverPending();

    // No events should be delivered
    verify(mockDelegate, never()).accept(any());
  }

  @Test
  void exceptionHandling_consumerThrowsEvent_stillCountedAsDelivered() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveryCount = new AtomicInteger(0);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    // Make delegate throw on first call, succeed on second
    doAnswer(
            invocation -> {
              deliveryCount.incrementAndGet();
              if (deliveryCount.get() == 1) {
                throw new RuntimeException("Test exception");
              }
              deliveredEvents.add(invocation.getArgument(0));
              return null;
            })
        .when(mockDelegate)
        .accept(any());

    // Submit first event (will throw in scheduled task)
    consumer.accept(createEvent("solution-1"));

    // Wait for throttle interval - exception is swallowed by scheduler
    Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

    // Submit second event (should succeed)
    consumer.accept(createEvent("solution-2"));
    Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

    // Both events should have been attempted
    verify(mockDelegate, times(2)).accept(any());

    // Only second event should have been delivered successfully
    assertThat(deliveryCount.get()).isEqualTo(2);
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-2");
  }

  @Test
  @Timeout(5)
  void concurrentEvents_multipleThreads_threadSafe() throws Exception {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();
    var latch = new CountDownLatch(1);

    doAnswer(
            invocation -> {
              deliveredEvents.add(invocation.getArgument(0));
              return null;
            })
        .when(mockDelegate)
        .accept(any());

    int threadCount = 10;
    int eventsPerThread = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    try {
      // Submit events from multiple threads
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        executor.submit(
            () -> {
              try {
                latch.await(); // Wait for all threads to be ready
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              for (int e = 0; e < eventsPerThread; e++) {
                consumer.accept(createEvent("thread-" + threadId + "-event-" + e));
              }
            });
      }

      // Release all threads at once
      latch.countDown();

      // Wait for all submissions
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.SECONDS);

      // Wait for final delivery
      Thread.sleep(THROTTLE_DURATION.toMillis() + 50);

      // Should have delivered at least one event
      verify(mockDelegate, times(1)).accept(any());
      assertThat(deliveredEvents).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void skipAhead_newEventDuringDelivery_onlyLatestDelivered() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(
            invocation -> {
              deliveredEvents.add(invocation.getArgument(0));
              // Simulate slow delivery
              Thread.sleep(50);
              return null;
            })
        .when(mockDelegate)
        .accept(any());

    // Submit first event
    consumer.accept(createEvent("solution-1"));

    // Wait a bit but not full interval
    Thread.sleep(THROTTLE_DURATION.toMillis() / 2);

    // Submit second event (should skip first)
    consumer.accept(createEvent("solution-2"));

    // Wait for delivery
    Thread.sleep(THROTTLE_DURATION.toMillis() + 100);

    // Only second event should be delivered
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-2");
  }

  @Test
  void resourceCleanup_close_shutsDownScheduler() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);

    // Submit event
    consumer.accept(createEvent("solution-1"));

    // Close consumer
    consumer.close();

    // Consumer should be terminated
    assertThat(consumer.isTerminated()).isTrue();

    // Wait to ensure scheduler is shut down
    Thread.sleep(200);

    // Event should still be delivered
    verify(mockDelegate, times(1)).accept(any());
  }

  @Test
  void resourceCleanup_closeWithPendingEvent_deliversImmediately() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit event
    consumer.accept(createEvent("solution-1"));

    // Close immediately
    consumer.close();

    // Event should be delivered immediately
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
  }

  @Test
  void terminate_idempotent_canBeCalledMultipleTimes() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);

    consumer.accept(createEvent("solution-1"));

    // Terminate multiple times
    consumer.terminateAndDeliverPending();
    consumer.terminateAndDeliverPending();
    consumer.terminateAndDeliverPending();

    // Should only deliver once
    verify(mockDelegate, times(1)).accept(any());
  }

  @Test
  void afterTermination_eventsDeliveredImmediately() {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, THROTTLE_DURATION);
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Terminate first
    consumer.terminateAndDeliverPending();

    // Now submit events - they should be delivered immediately
    consumer.accept(createEvent("solution-1"));
    consumer.accept(createEvent("solution-2"));
    consumer.accept(createEvent("solution-3"));

    // All three should be delivered
    verify(mockDelegate, times(3)).accept(any());
    assertThat(deliveredEvents).hasSize(3);
  }

  @Test
  void close_autoCloseable_worksWithTryWithResources() {
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();
    Consumer<NewBestSolutionEvent<String>> delegate = event -> deliveredEvents.add(event);

    try (var consumer = ThrottlingBestSolutionEventConsumer.of(delegate, THROTTLE_DURATION)) {
      consumer.accept(createEvent("solution-1"));
    }

    // Event should be delivered
    assertThat(deliveredEvents).hasSize(1);
  }

  @Test
  void throttleDuration_milliseconds_shortInterval() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, Duration.ofMillis(50));
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit events rapidly
    for (int i = 0; i < 3; i++) {
      consumer.accept(createEvent("solution-" + i));
      Thread.sleep(20); // Shorter than throttle
    }

    // Wait for final delivery
    Thread.sleep(100);

    // Only last event should be delivered
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-2");
  }

  @Test
  void throttleDuration_seconds_longInterval() throws InterruptedException {
    var consumer = ThrottlingBestSolutionEventConsumer.of(mockDelegate, Duration.ofSeconds(1));
    var deliveredEvents = new ArrayList<NewBestSolutionEvent<String>>();

    doAnswer(invocation -> deliveredEvents.add(invocation.getArgument(0)))
        .when(mockDelegate)
        .accept(any());

    // Submit first event
    consumer.accept(createEvent("solution-1"));

    // Wait less than throttle interval
    Thread.sleep(200);

    // Submit second event
    consumer.accept(createEvent("solution-2"));

    // Wait less than throttle interval
    Thread.sleep(200);

    // Terminate to deliver immediately
    consumer.terminateAndDeliverPending();

    // Only second event should be delivered
    verify(mockDelegate, times(1)).accept(any());
    assertThat(deliveredEvents).hasSize(1);
    assertThat(deliveredEvents.get(0).solution()).isEqualTo("solution-2");
  }

  // Helper methods

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
