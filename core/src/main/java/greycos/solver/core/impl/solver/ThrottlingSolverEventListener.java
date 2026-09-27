package greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;

import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.SolverEventListener;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Throttles best solution changed events to limit delivery rate. Delivers at most one event per
 * throttle duration, with the latest pending event taking precedence. Under a sustained event
 * stream, the most recent event is delivered once per interval. Ensures the last pending event is
 * delivered when throttling terminates.
 */
public final class ThrottlingSolverEventListener<Solution_>
    implements SolverEventListener<Solution_>, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ThrottlingSolverEventListener.class);

  private final ThrottledEventDispatcher<BestSolutionChangedEvent<Solution_>> eventDispatcher;

  private ThrottlingSolverEventListener(
      SolverEventListener<Solution_> delegate, Duration throttleDuration) {
    Objects.requireNonNull(delegate, "delegate must not be null");
    this.eventDispatcher =
        new ThrottledEventDispatcher<>(
            LOGGER,
            delegate::bestSolutionChanged,
            throttleDuration,
            "throttling-listener-scheduler");
  }

  @NonNull
  public static <Solution_> ThrottlingSolverEventListener<Solution_> of(
      @NonNull SolverEventListener<Solution_> delegate, @NonNull Duration throttleDuration) {
    return new ThrottlingSolverEventListener<>(delegate, throttleDuration);
  }

  @Override
  public void bestSolutionChanged(@NonNull BestSolutionChangedEvent<Solution_> event) {
    Objects.requireNonNull(event, "event must not be null");
    eventDispatcher.submit(event);
  }

  /**
   * Ends throttling and waits for pending delivery to finish, preserving the caller's interrupt
   * status. Calls from a solver manager consumer or any throttler callback request termination and
   * return so delivery can finish after callbacks return. Further events are delivered
   * synchronously.
   */
  public void terminateAndDeliverPending() {
    eventDispatcher.terminateAndDeliverPending();
  }

  /**
   * Ends throttling and releases its scheduler after pending delivery finishes. Calls from a solver
   * manager consumer or any throttler callback return before shutdown completes. Other callers wait
   * for completion even when interrupted, with their interrupt status restored before returning.
   */
  @Override
  public void close() {
    eventDispatcher.close();
  }

  boolean isTerminated() {
    return eventDispatcher.isTerminated();
  }
}
