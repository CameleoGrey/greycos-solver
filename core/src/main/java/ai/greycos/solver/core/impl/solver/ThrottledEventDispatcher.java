package ai.greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
final class ThrottledEventDispatcher<Event_> implements AutoCloseable {

  private final Logger logger;
  private final Consumer<Event_> delegate;
  private final long throttleNanos;
  private final ScheduledThreadPoolExecutor scheduler;
  private final Object stateLock = new Object();

  private @Nullable Event_ pendingEvent = null;
  private @Nullable ScheduledFuture<?> scheduledDelivery = null;
  private State state = State.ACTIVE;

  ThrottledEventDispatcher(
      Logger logger, Consumer<Event_> delegate, Duration throttleDuration, String threadName) {
    this.logger = Objects.requireNonNull(logger, "logger must not be null");
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.throttleNanos = validateThrottleDuration(throttleDuration);
    this.scheduler = createScheduler(threadName);
  }

  void submit(Event_ event) {
    boolean deliverImmediately = false;
    synchronized (stateLock) {
      switch (state) {
        case ACTIVE:
          pendingEvent = event;
          if (scheduledDelivery == null) {
            scheduledDelivery =
                scheduler.schedule(
                    this::deliverScheduledEvent, throttleNanos, TimeUnit.NANOSECONDS);
          }
          break;
        case TERMINATING:
          pendingEvent = event;
          break;
        case TERMINATED:
          deliverImmediately = true;
          break;
      }
    }
    if (deliverImmediately) {
      deliverEvent(event);
    }
  }

  void terminateAndDeliverPending() {
    ScheduledFuture<?> futureToCancel;
    synchronized (stateLock) {
      while (state == State.TERMINATING) {
        try {
          stateLock.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
      if (state == State.TERMINATED) {
        return;
      }
      state = State.TERMINATING;
      futureToCancel = scheduledDelivery;
      scheduledDelivery = null;
    }
    if (futureToCancel != null) {
      futureToCancel.cancel(false);
    }
    waitForDrainToComplete(scheduler.submit(this::drainPendingEventsAndTerminate));
  }

  @Override
  public void close() {
    terminateAndDeliverPending();
    shutdownScheduler();
  }

  boolean isTerminated() {
    synchronized (stateLock) {
      return state == State.TERMINATED;
    }
  }

  private void deliverScheduledEvent() {
    Event_ event;
    synchronized (stateLock) {
      scheduledDelivery = null;
      if (state != State.ACTIVE) {
        return;
      }
      event = pendingEvent;
      pendingEvent = null;
    }
    if (event != null) {
      deliverEvent(event);
    }
    synchronized (stateLock) {
      if (state == State.ACTIVE && pendingEvent != null && scheduledDelivery == null) {
        scheduledDelivery =
            scheduler.schedule(this::deliverScheduledEvent, throttleNanos, TimeUnit.NANOSECONDS);
      }
    }
  }

  private void drainPendingEventsAndTerminate() {
    while (true) {
      Event_ event;
      synchronized (stateLock) {
        event = pendingEvent;
        pendingEvent = null;
        if (event == null) {
          state = State.TERMINATED;
          stateLock.notifyAll();
          return;
        }
      }
      deliverEvent(event);
    }
  }

  private void deliverEvent(Event_ event) {
    try {
      delegate.accept(event);
    } catch (Throwable throwable) {
      logger.warn(
          "A throttled best solution event consumer/listener failed; the event is considered delivered.",
          throwable);
    }
  }

  private void waitForDrainToComplete(Future<?> drainFuture) {
    try {
      drainFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn(
          "Interrupted while waiting for throttled best solution events to finish delivering.", e);
    } catch (ExecutionException e) {
      logger.warn(
          "Failed while draining throttled best solution events during termination.", e.getCause());
    }
  }

  private ScheduledThreadPoolExecutor createScheduler(String threadName) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    executor.setRemoveOnCancelPolicy(true);
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = new Thread(runnable, threadName);
          thread.setDaemon(true);
          return thread;
        };
    executor.setThreadFactory(threadFactory);
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

  private static long validateThrottleDuration(Duration throttleDuration) {
    Objects.requireNonNull(throttleDuration, "throttleDuration must not be null");
    try {
      long throttleNanos = throttleDuration.toNanos();
      if (throttleNanos <= 0L) {
        throw new IllegalArgumentException(
            "throttleDuration must be positive, was: " + throttleDuration);
      }
      return throttleNanos;
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "throttleDuration is too large to be represented in nanoseconds: " + throttleDuration, e);
    }
  }

  private enum State {
    ACTIVE,
    TERMINATING,
    TERMINATED
  }
}
