package ai.greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;

import ai.greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.greycos.solver.core.api.solver.event.SolverEventListener;

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

  public void terminateAndDeliverPending() {
    eventDispatcher.terminateAndDeliverPending();
  }

  @Override
  public void close() {
    eventDispatcher.close();
  }

  boolean isTerminated() {
    return eventDispatcher.isTerminated();
  }
}
