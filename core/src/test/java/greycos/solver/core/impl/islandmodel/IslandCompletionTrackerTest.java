package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class IslandCompletionTrackerTest {

  @Test
  void lastCompletionClosesEveryChannel() {
    var first = new BoundedChannel<String>(1);
    var second = new BoundedChannel<String>(1);
    var tracker =
        new IslandCompletionTracker(
            2,
            () -> {
              first.close();
              second.close();
            });
    tracker.countDown();
    assertThat(first.trySend("first")).isTrue();
    assertThat(second.trySend("second")).isTrue();

    tracker.countDown();

    assertThat(tracker.getCount()).isZero();
    assertThat(first.tryReceive()).isNull();
    assertThat(second.tryReceive()).isNull();
    assertThat(first.trySend("late")).isFalse();
    assertThat(second.trySend("late")).isFalse();
  }

  @Test
  void concurrentCompletionsAndRepeatedCountdownCloseOnce() throws Exception {
    var calls = new AtomicInteger();
    var tracker = new IslandCompletionTracker(4, calls::incrementAndGet);
    var start = new CountDownLatch(1);
    var workers = new ArrayList<Thread>();
    for (int i = 0; i < 4; i++) {
      var worker =
          new Thread(
              () -> {
                try {
                  start.await();
                  tracker.countDown();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              },
              "island-completion-" + i);
      workers.add(worker);
      worker.start();
    }
    try {
      start.countDown();
      for (var worker : workers) {
        worker.join(5000);
        assertThat(worker.isAlive()).isFalse();
      }
      tracker.countDown();
      assertThat(tracker.getCount()).isZero();
      assertThat(calls).hasValue(1);
    } finally {
      start.countDown();
      for (var worker : workers) {
        worker.interrupt();
        worker.join(5000);
      }
    }
  }

  @Test
  void zeroIslandsFailFast() {
    assertThatThrownBy(() -> new IslandCompletionTracker(0, () -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Island count (0)");
  }
}
