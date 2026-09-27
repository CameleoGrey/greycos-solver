package greycos.solver.core.impl.solver;

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
  private final Object deliveryLock = new Object();

  private @Nullable Event_ pendingEvent = null;
  private @Nullable ScheduledFuture<?> scheduledDelivery = null;
  private @Nullable Future<?> drainFuture = null;
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
    waitForCompletion(requestTermination(), false);
  }

  private Future<?> requestTermination() {
    synchronized (stateLock) {
      if (drainFuture == null) {
        state = State.TERMINATING;
        if (scheduledDelivery != null) {
          scheduledDelivery.cancel(false);
          scheduledDelivery = null;
        }
        // Publish the drain before another caller can shut down the scheduler.
        drainFuture = scheduler.submit(this::drainPendingEventsAndTerminate);
      }
      return drainFuture;
    }
  }

  @Override
  public void close() {
    var terminationFuture = requestTermination();
    scheduler.shutdown();
    waitForCompletion(terminationFuture, true);
  }

  boolean isTerminated() {
    synchronized (stateLock) {
      return state == State.TERMINATED;
    }
  }

  long getThrottleNanos() {
    return throttleNanos;
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
          return;
        }
      }
      deliverEvent(event);
    }
  }

  private void deliverEvent(Event_ event) {
    // Also serialize synchronous deliveries after throttling has terminated.
    synchronized (deliveryLock) {
      try {
        SolverEventThreadContext.run(() -> delegate.accept(event));
      } catch (Throwable throwable) {
        logger.warn(
            "A throttled best solution event consumer/listener failed; the event is considered delivered.",
            throwable);
      }
    }
  }

  private void waitForCompletion(Future<?> terminationFuture, boolean waitForScheduler) {
    if (SolverEventThreadContext.isActive()) {
      // Waiting from another event delivery worker can create a cycle between callbacks.
      return;
    }
    boolean interrupted = false;
    try {
      while (true) {
        try {
          terminationFuture.get();
          break;
        } catch (InterruptedException e) {
          interrupted = true;
        } catch (ExecutionException e) {
          logger.warn(
              "Failed while draining throttled best solution events during termination.",
              e.getCause());
          break;
        }
      }
      if (waitForScheduler) {
        while (true) {
          try {
            if (scheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
              break;
            }
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private ScheduledThreadPoolExecutor createScheduler(String threadName) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    executor.setRemoveOnCancelPolicy(true);
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = new Thread(() -> SolverEventThreadContext.run(runnable), threadName);
          thread.setDaemon(true);
          return thread;
        };
    executor.setThreadFactory(threadFactory);
    return executor;
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
