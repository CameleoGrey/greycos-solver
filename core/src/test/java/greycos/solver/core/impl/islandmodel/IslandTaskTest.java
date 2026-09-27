package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class IslandTaskTest {

  @Test
  void completionIsReadableBeforeItsSignal() throws Exception {
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var signal = new IslandCoordinatorSignal();
    var task = new IslandTask(() -> {}, completed, signal);
    long observed = signal.generation();

    task.run();

    assertThat(signal.generation()).isEqualTo(observed + 1);
    assertThat(completed).containsExactly(task);
    assertThat(task.isDone()).isTrue();
    assertThat(task.get()).isNull();
  }

  @Test
  void failureAndPrestartCancellationSignalExactlyOnce() {
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var signal = new IslandCoordinatorSignal();
    var failure = new AssertionError("Agent failed");
    var failed =
        new IslandTask(
            () -> {
              throw failure;
            },
            completed,
            signal);
    failed.run();
    assertThatThrownBy(failed::get).isInstanceOf(ExecutionException.class).hasCause(failure);

    var started = new AtomicBoolean();
    var cancelled = new IslandTask(() -> started.set(true), completed, signal);
    assertThat(cancelled.cancel(true)).isTrue();
    assertThat(cancelled.cancel(true)).isFalse();
    cancelled.run();

    assertThat(started).isFalse();
    assertThat(signal.generation()).isEqualTo(2L);
    assertThat(completed).containsExactly(failed, cancelled);
  }
}
