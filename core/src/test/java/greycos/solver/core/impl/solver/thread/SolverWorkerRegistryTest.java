package greycos.solver.core.impl.solver.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class SolverWorkerRegistryTest {

  @Test
  void noDeferredOwnersDoesNotRequireLegitimateSiblingWorkersToStop() throws Exception {
    var registry = new SolverWorkerRegistry();
    var executor = Executors.newSingleThreadExecutor();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    registry.registerExecutor(executor, "active sibling worker");
    try {
      executor.execute(
          () -> {
            entered.countDown();
            awaitUninterruptibly(release);
          });
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatCode(registry::runDeferredCleanup).doesNotThrowAnyException();
      assertThatThrownBy(registry::assertNoActiveWorkers)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("active sibling worker");
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void deferredOwnersRunInRegistrationOrderOnceAndNeverWhileAWorkerIsActive() throws Exception {
    var registry = new SolverWorkerRegistry();
    var executor = Executors.newSingleThreadExecutor();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new ArrayList<String>();
    var firstOwner = new Object();
    registry.registerExecutor(executor, "deferred cleanup worker");
    registry.deferCleanup(firstOwner, () -> calls.add("first"));
    registry.deferCleanup(firstOwner, () -> calls.add("duplicate"));
    registry.deferCleanup(new Object(), () -> calls.add("second"));
    try {
      executor.execute(
          () -> {
            entered.countDown();
            awaitUninterruptibly(release);
          });
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      executor.shutdownNow();
      assertThatThrownBy(registry::runDeferredCleanup).isInstanceOf(IllegalStateException.class);
      assertThat(calls).isEmpty();
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    registry.runDeferredCleanup();
    registry.runDeferredCleanup();
    assertThat(calls).containsExactly("first", "second");
  }

  @Test
  void deferredCleanupFailurePreservesItsCauseAndRetriesOnlyTheUnfinishedOwner() {
    var registry = new SolverWorkerRegistry();
    var calls = new ArrayList<String>();
    var fail = new AtomicBoolean(true);
    var original = new IllegalStateException("old owner cleanup failed");
    registry.deferCleanup(
        new Object(),
        () -> {
          calls.add("retryable");
          if (fail.get()) throw original;
        });
    registry.deferCleanup(new Object(), () -> calls.add("complete"));
    assertThatThrownBy(registry::runDeferredCleanup).isSameAs(original);
    assertThat(calls).containsExactly("retryable", "complete");
    fail.set(false);
    registry.runDeferredCleanup();
    registry.runDeferredCleanup();
    assertThat(calls).containsExactly("retryable", "complete", "retryable");
  }

  @Test
  @SuppressWarnings("unchecked")
  void childScopesShareRootRegistry() {
    var root = new SolverScope<Object>();
    root.setWorkingRandom(DefaultRandomSource.seeded(0L));
    root.setScoreDirector((InnerScoreDirector<Object, ?>) mock(InnerScoreDirector.class));

    var child = root.createChildThreadSolverScope(ChildThreadType.PART_THREAD);

    assertThat(child.getWorkerRegistry()).isSameAs(root.getWorkerRegistry());
    var thread = new Thread(() -> {});
    child.getWorkerRegistry().registerThread(thread, "child raw worker");
    assertThatThrownBy(root.getWorkerRegistry()::assertNoActiveWorkers)
        .hasMessageContaining("child raw worker");
    child.getWorkerRegistry().unregisterThread(thread);
    assertThatCode(root.getWorkerRegistry()::assertNoActiveWorkers).doesNotThrowAnyException();
  }

  @Test
  void cancelledFutureStillBlocksReuseUntilItsWorkerExits() throws Exception {
    var registry = new SolverWorkerRegistry();
    var executor = Executors.newSingleThreadExecutor();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    registry.registerExecutor(executor, "island move workers");
    try {
      var future =
          executor.submit(
              () -> {
                entered.countDown();
                awaitUninterruptibly(release);
              });
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      future.cancel(true);
      executor.shutdownNow();

      assertThat(future.isDone()).isTrue();
      assertThat(executor.isShutdown()).isTrue();
      assertThatThrownBy(registry::assertNoActiveWorkers)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("island move workers")
          .hasMessageContaining("Wait for those workers to exit");
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    assertThatCode(registry::assertNoActiveWorkers).doesNotThrowAnyException();
  }

  @Test
  void completedTaskDoesNotRemoveAnOpenPool() throws Exception {
    var registry = new SolverWorkerRegistry();
    var executor = Executors.newSingleThreadExecutor();
    registry.registerExecutor(executor, "open pool");
    try {
      executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
      assertThatThrownBy(registry::assertNoActiveWorkers).hasMessageContaining("open pool");
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    assertThatCode(registry::assertNoActiveWorkers).doesNotThrowAnyException();
  }

  @Test
  void registeringAnotherWorkerCannotPruneAnUnstartedThread() {
    var registry = new SolverWorkerRegistry();
    var first = new Thread(() -> {}, "not-started-first");
    var second = new Thread(() -> {}, "not-started-second");
    registry.registerThread(first, "first raw worker");
    registry.registerThread(second, "second raw worker");

    registry.unregisterThread(second);
    assertThatThrownBy(registry::assertNoActiveWorkers).hasMessageContaining("first raw worker");
    registry.unregisterThread(first);
    assertThatCode(registry::assertNoActiveWorkers).doesNotThrowAnyException();
  }

  @Test
  void liveRawThreadCannotBeUnregisteredAndCompletedThreadAllowsReuse() throws Exception {
    var registry = new SolverWorkerRegistry();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var worker =
        new Thread(
            () -> {
              entered.countDown();
              awaitUninterruptibly(release);
            },
            "repair-attempt-worker");
    registry.registerThread(worker, "repair attempts");
    worker.start();
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(() -> registry.unregisterThread(worker))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("repair-attempt-worker");
      assertThatThrownBy(registry::assertNoActiveWorkers).hasMessageContaining("repair attempts");
    } finally {
      release.countDown();
      worker.join(5000);
      assertThat(worker.isAlive()).isFalse();
    }
    assertThatCode(registry::assertNoActiveWorkers).doesNotThrowAnyException();
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) Thread.currentThread().interrupt();
  }
}
