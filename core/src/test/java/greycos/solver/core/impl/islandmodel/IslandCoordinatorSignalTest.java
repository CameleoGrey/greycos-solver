package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class IslandCoordinatorSignalTest {

  @Test
  void notificationBeforeWaitingIsRetainedAndSignalsAreIndependent() throws InterruptedException {
    var signal = new IslandCoordinatorSignal();
    var otherRun = new IslandCoordinatorSignal();
    long observed = signal.generation();
    signal.signal();
    signal.signal();

    signal.awaitChange(observed);

    assertThat(signal.generation()).isEqualTo(observed + 2);
    assertThat(otherRun.generation()).isZero();
  }

  @Test
  void notificationWakesAnIdleCoordinator() throws Exception {
    var signal = new IslandCoordinatorSignal();
    var result =
        new FutureTask<Void>(
            () -> {
              signal.awaitChange(0L);
              return null;
            });
    var waiter = new Thread(result, "island-signal-waiter");
    waiter.start();
    try {
      await().untilAsserted(() -> assertThat(waiter.getState()).isEqualTo(Thread.State.WAITING));
      signal.signal();
      assertThat(result.get(5, TimeUnit.SECONDS)).isNull();
    } finally {
      waiter.interrupt();
      waiter.join(5000);
      assertThat(waiter.isAlive()).isFalse();
    }
  }

  @Test
  void interruptWakesAnIdleCoordinator() throws Exception {
    var signal = new IslandCoordinatorSignal();
    var result =
        new FutureTask<Void>(
            () -> {
              signal.awaitChange(0L);
              return null;
            });
    var waiter = new Thread(result, "interrupted-island-signal-waiter");
    waiter.start();
    try {
      await().untilAsserted(() -> assertThat(waiter.getState()).isEqualTo(Thread.State.WAITING));
      waiter.interrupt();
      assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(InterruptedException.class);
    } finally {
      waiter.interrupt();
      waiter.join(5000);
      assertThat(waiter.isAlive()).isFalse();
    }
  }

  @Test
  void interruptIsObservedEvenWhenANotificationIsAlreadyAvailable() throws Exception {
    var signal = new IslandCoordinatorSignal();
    signal.signal();
    var result =
        new FutureTask<Void>(
            () -> {
              Thread.currentThread().interrupt();
              signal.awaitChange(0L);
              return null;
            });
    var waiter = new Thread(result, "already-interrupted-island-signal-waiter");
    waiter.start();
    try {
      assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(InterruptedException.class);
    } finally {
      waiter.interrupt();
      waiter.join(5000);
      assertThat(waiter.isAlive()).isFalse();
    }
  }
}
