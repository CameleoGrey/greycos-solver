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
 * Throttles best solution changed events to limit delivery rate.
 * Delivers at most one event per throttle duration, with the last event taking precedence.
 * Ensures final best solution is always delivered on termination.
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

  @NonNull
  public static <Solution_> ThrottlingSolverEventListener<Solution_> of(
      @NonNull SolverEventListener<Solution_> delegate, @NonNull Duration throttleDuration) {
    return new ThrottlingSolverEventListener<>(delegate, throttleDuration);
  }

  @Override
  public void bestSolutionChanged(@NonNull BestSolutionChangedEvent<Solution_> event) {
    Objects.requireNonNull(event, "event must not be null");

    if (terminated) {
      deliverEvent(event);
      return;
    }

    pendingEvent.set(event);
    ScheduledFuture<?> previousDelivery = pendingDelivery.getAndSet(null);
    if (previousDelivery != null) {
      previousDelivery.cancel(false);
    }

    ScheduledFuture<?> newDelivery =
        scheduler.schedule(
            this::deliverPendingEvent, throttleDuration.toMillis(), TimeUnit.MILLISECONDS);
    pendingDelivery.set(newDelivery);
  }

  public void terminateAndDeliverPending() {
    if (terminated) {
      return;
    }

    ScheduledFuture<?> scheduled = pendingDelivery.getAndSet(null);
    if (scheduled != null) {
      scheduled.cancel(false);
    }

    terminated = true;

    BestSolutionChangedEvent<Solution_> event = pendingEvent.getAndSet(null);
    if (event != null) {
      deliverEvent(event);
    }
  }

  @Override
  public void close() {
    terminateAndDeliverPending();
    shutdownScheduler();
  }

  private void deliverPendingEvent() {
    BestSolutionChangedEvent<Solution_> event = pendingEvent.getAndSet(null);

    if (terminated) {
      return;
    }

    pendingDelivery.set(null);

    if (event != null) {
      deliverEvent(event);
    }
  }

  private void deliverEvent(BestSolutionChangedEvent<Solution_> event) {
    try {
      delegate.bestSolutionChanged(event);
    } catch (Throwable t) {
    }
  }

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

  boolean isTerminated() {
    return terminated;
  }
}
