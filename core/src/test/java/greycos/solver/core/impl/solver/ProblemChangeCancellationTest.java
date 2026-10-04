package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class ProblemChangeCancellationTest {

  @Test
  void cancellationPublishesEveryStateWhileContinuationsWaitAndExplicitCloseDrainsTheBatch()
      throws Exception {
    var cancellation = new ProblemChangeCancellation();
    var entered = new CountDownLatch(128);
    var release = new CountDownLatch(1);
    var changes =
        IntStream.range(0, 128).mapToObj(ignored -> new CompletableFuture<Void>()).toList();
    var continuations =
        changes.stream()
            .map(
                change ->
                    change.handle(
                        (ignored, failure) -> {
                          entered.countDown();
                          try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException e) {
                            throw new AssertionError(e);
                          }
                          return null;
                        }))
            .toList();
    FutureTask<Boolean> closure = null;
    Thread closer = null;
    try {
      cancellation.dispatch(changes);
      cancellation.awaitPublication(System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
      assertThat(changes).allSatisfy(change -> assertThat(change).isCancelled());
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      closure =
          new FutureTask<>(
              () -> {
                Thread.currentThread().interrupt();
                cancellation.awaitCompletion();
                return Thread.currentThread().isInterrupted();
              });
      closer = Thread.ofPlatform().daemon().start(closure);
      var waitingClosure = closure;
      assertThatThrownBy(() -> waitingClosure.get(100, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      release.countDown();
      assertThat(closure.get(5, TimeUnit.SECONDS)).isTrue();
      for (var continuation : continuations) continuation.get(5, TimeUnit.SECONDS);
      assertThat(cancellation).extracting("tasks").isEqualTo(Map.of());
    } finally {
      release.countDown();
      if (closer != null) closer.join(5000);
      cancellation.awaitCompletion();
    }
  }

  @Test
  void completionReentryDoesNotAwaitItsOwnTaskAndPublicationHonorsAnExpiredDeadline()
      throws Exception {
    var cancellation = new ProblemChangeCancellation();
    var notDispatched = new CompletableFuture<Void>();
    ProblemChangeCancellation.awaitPublication(List.of(notDispatched), System.nanoTime());
    assertThat(notDispatched).isNotCompleted();
    var change = new CompletableFuture<Void>();
    var continuation =
        change.handle(
            (ignored, failure) -> {
              assertThat(SolverEventThreadContext.isActive()).isTrue();
              cancellation.awaitCompletion();
              return null;
            });
    cancellation.dispatch(List.of(change));
    continuation.get(5, TimeUnit.SECONDS);
    cancellation.awaitCompletion();
    assertThat(cancellation).extracting("tasks").isEqualTo(Map.of());
  }
}
