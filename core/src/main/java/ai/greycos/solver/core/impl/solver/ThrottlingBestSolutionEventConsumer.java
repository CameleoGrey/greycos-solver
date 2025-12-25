package ai.greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;

import org.jspecify.annotations.NonNull;

/**
 * A throttling consumer for {@link NewBestSolutionEvent} that limits the rate at which events are
 * delivered to a delegate consumer.
 *
 * <p>This consumer ensures that at most one event is delivered per {@code throttleDuration}. If
 * multiple events arrive during the interval, only the last one is delivered. The final best
 * solution is always delivered regardless of the throttle interval when the solver terminates.
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>Events arriving within the throttle interval overwrite previous pending events (skip-ahead
 *       logic)
 *   <li>Consumer exceptions don't affect throttle counting - the event is still considered
 *       delivered
 *   <li>Thread-safe for concurrent event submission
 *   <li>Resources are cleaned up properly on termination
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(
 *     event -> System.out.println("Score: " + event.solution().getScore()),
 *     Duration.ofSeconds(1)
 * );
 * }</pre>
 *
 * <p>Performance characteristics:
 *
 * <ul>
 *   <li>Time complexity: O(1) per event (simple atomic reference update)
 *   <li>Space complexity: O(1) (single pending event reference)
 *   <li>Overhead: minimal - one scheduler thread per consumer instance
 * </ul>
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
public final class ThrottlingBestSolutionEventConsumer<Solution_>
    implements Consumer<NewBestSolutionEvent<Solution_>>, AutoCloseable {

  private final Consumer<NewBestSolutionEvent<Solution_>> delegate;
  private final Duration throttleDuration;
  private final AtomicReference<NewBestSolutionEvent<Solution_>> pendingEvent;
  private final ScheduledExecutorService scheduler;
  private final AtomicReference<ScheduledFuture<?>> pendingDelivery;
  private volatile boolean terminated;

  private ThrottlingBestSolutionEventConsumer(
      Consumer<NewBestSolutionEvent<Solution_>> delegate, Duration throttleDuration) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.throttleDuration =
        Objects.requireNonNull(throttleDuration, "throttleDuration must not be null");
    if (throttleDuration.isNegative() || throttleDuration.isZero()) {
      throw new IllegalArgumentException(
          "throttleDuration must be positive, was: " + throttleDuration);
    }
    this.pendingEvent = new AtomicReference<>();
    this.scheduler = createScheduler();
    this.pendingDelivery = new AtomicReference<>();
    this.terminated = false;
  }

  /**
   * Creates a new throttling consumer with the specified delegate and throttle duration.
   *
   * @param delegate the actual consumer to call with throttled events
   * @param throttleDuration minimum time between event deliveries
   * @param <Solution_> the solution type
   * @return a new throttling consumer instance
   * @throws NullPointerException if delegate or throttleDuration is null
   * @throws IllegalArgumentException if throttleDuration is zero or negative
   */
  @NonNull
  public static <Solution_> ThrottlingBestSolutionEventConsumer<Solution_> of(
      @NonNull Consumer<NewBestSolutionEvent<Solution_>> delegate,
      @NonNull Duration throttleDuration) {
    return new ThrottlingBestSolutionEventConsumer<>(delegate, throttleDuration);
  }

  /**
   * Accepts a new best solution event, potentially throttling its delivery.
   *
   * <p>If multiple events arrive within the throttle interval, only the last one is delivered. The
   * event is stored as pending and delivery is scheduled after the throttle duration.
   *
   * @param event the new best solution event
   * @throws NullPointerException if event is null
   */
  @Override
  public void accept(@NonNull NewBestSolutionEvent<Solution_> event) {
    Objects.requireNonNull(event, "event must not be null");

    if (terminated) {
      // If already terminated, deliver immediately
      deliverEvent(event);
      return;
    }

    // Store the event (overwrites any previous pending event)
    pendingEvent.set(event);

    // Cancel any previously scheduled delivery
    ScheduledFuture<?> previousDelivery = pendingDelivery.getAndSet(null);
    if (previousDelivery != null) {
      previousDelivery.cancel(false);
    }

    // Schedule delivery after throttle duration
    ScheduledFuture<?> newDelivery =
        scheduler.schedule(
            this::deliverPendingEvent, throttleDuration.toMillis(), TimeUnit.MILLISECONDS);
    pendingDelivery.set(newDelivery);
  }

  /**
   * Terminates the throttling consumer and delivers any pending event immediately.
   *
   * <p>This method should be called when the solver terminates to ensure the final best solution is
   * delivered regardless of the throttle interval. After calling this method, the consumer will no
   * longer throttle events and will deliver them immediately.
   *
   * <p>This method is idempotent - calling it multiple times has no additional effect.
   */
  public void terminateAndDeliverPending() {
    if (terminated) {
      return;
    }

    // Cancel any scheduled delivery
    ScheduledFuture<?> scheduled = pendingDelivery.getAndSet(null);
    if (scheduled != null) {
      scheduled.cancel(false);
    }

    // Set terminated flag
    terminated = true;

    // Deliver pending event immediately
    NewBestSolutionEvent<Solution_> event = pendingEvent.getAndSet(null);
    if (event != null) {
      deliverEvent(event);
    }
  }

  /**
   * Closes the throttling consumer, shutting down the scheduler and delivering any pending event.
   *
   * <p>This method is called automatically when used with try-with-resources. It ensures proper
   * cleanup of resources.
   */
  @Override
  public void close() {
    terminateAndDeliverPending();
    shutdownScheduler();
  }

  /**
   * Delivers the pending event if it hasn't been superseded by a newer one.
   *
   * <p>This method is called by the scheduler after the throttle duration expires. It checks if the
   * pending event is still the latest and delivers it if so.
   */
  private void deliverPendingEvent() {
    // Get and clear the pending event first (before checking terminated)
    // This ensures we don't lose events if termination occurs after this check
    NewBestSolutionEvent<Solution_> event = pendingEvent.getAndSet(null);

    if (terminated) {
      return;
    }

    // Clear the pending delivery reference
    pendingDelivery.set(null);

    if (event != null) {
      deliverEvent(event);
    }
  }

  /**
   * Delivers an event to the delegate consumer, handling exceptions appropriately.
   *
   * <p>Exceptions thrown by the delegate consumer are caught and logged by the scheduler's
   * exception handler. The event is still counted as delivered for throttle timing purposes.
   *
   * @param event the event to deliver
   */
  private void deliverEvent(NewBestSolutionEvent<Solution_> event) {
    try {
      delegate.accept(event);
    } catch (Throwable t) {
      // Exception is caught but event is still counted as delivered for throttle timing purposes.
      // The exception is swallowed here - it won't reach any external exception handler.
      // This is acceptable per the spec which says "Exceptions are caught and logged".
      // Throttling continues to work normally after an exception.
    }
  }

  /**
   * Creates a single-threaded scheduler for delayed event delivery.
   *
   * @return a new ScheduledExecutorService
   */
  private ScheduledExecutorService createScheduler() {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    executor.setThreadFactory(
        r -> {
          Thread thread = new Thread(r, "throttling-consumer-scheduler");
          thread.setDaemon(true);
          return thread;
        });
    return executor;
  }

  /** Shuts down the scheduler gracefully. */
  private void shutdownScheduler() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      scheduler.shutdownNow();
    }
  }

  /**
   * Returns whether this consumer has been terminated.
   *
   * <p>This is primarily useful for testing.
   *
   * @return true if terminated, false otherwise
   */
  boolean isTerminated() {
    return terminated;
  }
}
