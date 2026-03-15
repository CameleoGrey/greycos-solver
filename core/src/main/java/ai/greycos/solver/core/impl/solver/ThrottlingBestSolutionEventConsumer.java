package ai.greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.solver.event.NewBestSolutionEvent;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Throttles new best solution events to limit delivery rate. Delivers at most one event per
 * throttle duration, with the latest pending event taking precedence. Under a sustained event
 * stream, the most recent event is delivered once per interval. Ensures the last pending event is
 * delivered when throttling terminates.
 */
public final class ThrottlingBestSolutionEventConsumer<Solution_>
    implements Consumer<NewBestSolutionEvent<Solution_>>, AutoCloseable {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ThrottlingBestSolutionEventConsumer.class);

  private final ThrottledEventDispatcher<NewBestSolutionEvent<Solution_>> eventDispatcher;

  private ThrottlingBestSolutionEventConsumer(
      Consumer<NewBestSolutionEvent<Solution_>> delegate, Duration throttleDuration) {
    Objects.requireNonNull(delegate, "delegate must not be null");
    this.eventDispatcher =
        new ThrottledEventDispatcher<>(
            LOGGER, delegate, throttleDuration, "throttling-consumer-scheduler");
  }

  @NonNull
  public static <Solution_> ThrottlingBestSolutionEventConsumer<Solution_> of(
      @NonNull Consumer<NewBestSolutionEvent<Solution_>> delegate,
      @NonNull Duration throttleDuration) {
    return new ThrottlingBestSolutionEventConsumer<>(delegate, throttleDuration);
  }

  @Override
  public void accept(@NonNull NewBestSolutionEvent<Solution_> event) {
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
