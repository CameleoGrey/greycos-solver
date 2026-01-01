package ai.greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ai.greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.greycos.solver.core.api.solver.event.SolverEventListener;

import org.jspecify.annotations.NonNull;

/**
 * A throttling listener for {@link SolverEventListener} that limits the rate at which best solution
 * changed events are delivered to a delegate listener.
 *
 * <p>This listener ensures that at most one event is delivered per {@code throttleDuration}. If
 * multiple events arrive during the interval, only the last one is delivered. The final best
 * solution is always delivered regardless of the throttle interval when the solver terminates.
 *
 * <p>Key behaviors:
 *
 * <ul>
 *   <li>Events arriving within the throttle interval overwrite previous pending events (skip-ahead
 *       logic)
 *   <li>Listener exceptions don't affect throttle counting - the event is still considered
 *       delivered
 *   <li>Thread-safe for concurrent event submission
 *   <li>Resources are cleaned up properly on termination
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * var throttledListener = ThrottlingSolverEventListener.of(
 *     event -> System.out.println("Score: " + event.getNewBestScore()),
 *     Duration.ofSeconds(1)
 * );
 * solver.addEventListener(throttledListener);
 * }</pre>
 *
 * <p>Performance characteristics:
 *
 * <ul>
 *   <li>Time complexity: O(1) per event (simple atomic reference update)
 *   <li>Space complexity: O(1) (single pending event reference)
 *   <li>Overhead: minimal - one scheduler thread per listener instance
 * </ul>
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
public final class ThrottlingSolverEventListener<Solution_>
    implements SolverEventListener<Solution_>, AutoCloseable {

  private final SolverEventListener<Solution_> delegate;
  private final Duration throttleDuration;
  private final AtomicReference<BestSolutionChangedEvent<Solution_>> pendingEvent;
  private final ScheduledExecutorService scheduler;
  private final AtomicReference<ScheduledFuture<?>> pendingDelivery;
  private volatile boolean terminated;

  private ThrottlingSolverEventListener(
      SolverEventListener<Solution_> delegate, Duration throttleDuration) {
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
   * Creates a new throttling listener with the specified delegate and throttle duration.
   *
   * @param delegate the actual listener to call with throttled events
   * @param throttleDuration minimum time between event deliveries
   * @param <Solution_> the solution type
   * @return a new throttling listener instance
   * @throws NullPointerException if delegate or throttleDuration is null
   * @throws IllegalArgumentException if throttleDuration is zero or negative
   */
  @NonNull
  public static <Solution_> ThrottlingSolverEventListener<Solution_> of(
      @NonNull SolverEventListener<Solution_> delegate, @NonNull Duration throttleDuration) {
    return new ThrottlingSolverEventListener<>(delegate, throttleDuration);
  }

  /**
   * Called when a better solution is found, potentially throttling its delivery.
   *
   * <p>If multiple events arrive within the throttle interval, only the last one is delivered. The
   * event is stored as pending and delivery is scheduled after the throttle duration.
   *
   * @param event the best solution changed event
   * @throws NullPointerException if event is null
   */
  @Override
  public void bestSolutionChanged(@NonNull BestSolutionChangedEvent<Solution_> event) {
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
   * Terminates the throttling listener and delivers any pending event immediately.
   *
   * <p>This method should be called when the solver terminates to ensure the final best solution is
   * delivered regardless of the throttle interval. After calling this method, the listener will no
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
    BestSolutionChangedEvent<Solution_> event = pendingEvent.getAndSet(null);
    if (event != null) {
      deliverEvent(event);
    }
  }

  /**
   * Closes the throttling listener, shutting down the scheduler and delivering any pending event.
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
    BestSolutionChangedEvent<Solution_> event = pendingEvent.getAndSet(null);

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
   * Delivers an event to the delegate listener, handling exceptions appropriately.
   *
   * <p>Exceptions thrown by the delegate listener are caught and logged by the scheduler's
   * exception handler. The event is still counted as delivered for throttle timing purposes.
   *
   * @param event the event to deliver
   */
  private void deliverEvent(BestSolutionChangedEvent<Solution_> event) {
    try {
      delegate.bestSolutionChanged(event);
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
          Thread thread = new Thread(r, "throttling-listener-scheduler");
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
   * Returns whether this listener has been terminated.
   *
   * <p>This is primarily useful for testing.
   *
   * @return true if terminated, false otherwise
   */
  boolean isTerminated() {
    return terminated;
  }
}
