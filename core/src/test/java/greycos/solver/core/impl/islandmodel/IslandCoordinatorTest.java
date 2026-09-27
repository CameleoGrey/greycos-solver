package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class IslandCoordinatorTest {

  @Test
  void publicationAndCompletionWakeTheSameIdleCoordinator() throws Exception {
    var scope = new SolverScope<Object>();
    var mailbox = mailbox(scope, 1);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var coordinatorResult = coordinatorResult(scope, mailbox, completed);
    var coordinator = new Thread(coordinatorResult, "island-event-coordinator");
    var delivered = new CountDownLatch(1);
    var callbackThread = new AtomicReference<Thread>();
    coordinator.start();
    try {
      awaitWaiting(coordinator);
      mailbox.enqueue(
          () -> {
            callbackThread.set(Thread.currentThread());
            delivered.countDown();
          });
      assertThat(delivered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(callbackThread.get()).isSameAs(coordinator);
      assertThat(coordinatorResult.isDone()).isFalse();

      new IslandTask(() -> {}, completed, mailbox.getSignal()).run();

      assertThat(coordinatorResult.get(5, TimeUnit.SECONDS)).isNull();
    } finally {
      stop(coordinator, mailbox);
    }
  }

  @Test
  void activityBetweenEmptyChecksAndWaitingIsNotLost() throws Exception {
    var scope = spy(new SolverScope<Object>());
    var emptyChecksFinished = new CountDownLatch(1);
    var allowWait = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              emptyChecksFinished.countDown();
              if (!allowWait.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Test did not release coordinator wait");
              }
              return null;
            })
        .when(scope)
        .destroyYielding();
    var mailbox = mailbox(scope, 1);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var coordinatorResult = coordinatorResult(scope, mailbox, completed);
    var coordinator = new Thread(coordinatorResult, "island-racing-event-coordinator");
    var delivered = new AtomicBoolean();
    coordinator.start();
    try {
      assertThat(emptyChecksFinished.await(5, TimeUnit.SECONDS)).isTrue();
      mailbox.enqueue(() -> delivered.set(true));
      new IslandTask(() -> {}, completed, mailbox.getSignal()).run();
      allowWait.countDown();

      assertThat(coordinatorResult.get(5, TimeUnit.SECONDS)).isNull();
      assertThat(delivered).isTrue();
    } finally {
      allowWait.countDown();
      stop(coordinator, mailbox);
    }
  }

  @Test
  void knownFailurePreventsFurtherDeliveryAndClosingReleasesPublisher() throws Exception {
    var scope = new SolverScope<Object>();
    var mailbox = mailbox(scope, 1);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var delivered = new AtomicInteger();
    mailbox.enqueue(delivered::incrementAndGet);
    var publisher =
        new Thread(() -> mailbox.enqueue(delivered::incrementAndGet), "island-blocked-publication");
    publisher.start();
    try {
      awaitWaiting(publisher);
      var failure = new AssertionError("Peer failed");
      new IslandTask(
              () -> {
                throw failure;
              },
              completed,
              mailbox.getSignal())
          .run();

      assertThatThrownBy(() -> DefaultIslandModelPhase.awaitAgents(scope, mailbox, completed, 1))
          .isInstanceOf(ExecutionException.class)
          .hasCause(failure);
      assertThat(delivered).hasValue(0);

      mailbox.stop();
      publisher.join(5000);
      assertThat(publisher.isAlive()).isFalse();
      assertThat(mailbox.drain()).isZero();
    } finally {
      stop(publisher, mailbox);
    }
  }

  @Test
  void continuousPublicationsCannotStarveFailureDetection() {
    var scope = new SolverScope<Object>();
    var mailbox = mailbox(scope, 2);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var calls = new AtomicInteger();
    var failure = new IllegalStateException("Peer failed during delivery");
    var failed =
        new IslandTask(
            () -> {
              throw failure;
            },
            completed,
            mailbox.getSignal());
    Runnable publication =
        new Runnable() {
          @Override
          public void run() {
            calls.incrementAndGet();
            failed.run();
            mailbox.enqueue(this);
          }
        };
    mailbox.enqueue(publication);
    try {
      assertThatThrownBy(() -> DefaultIslandModelPhase.awaitAgents(scope, mailbox, completed, 1))
          .isInstanceOf(ExecutionException.class)
          .hasCause(failure);
      assertThat(calls).hasValue(2);
    } finally {
      mailbox.stop();
    }
  }

  @Test
  void continuousPublicationsDoNotHideInterruption() throws Exception {
    var scope = new SolverScope<Object>();
    var mailbox = mailbox(scope, 1);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    Runnable publication =
        new Runnable() {
          @Override
          public void run() {
            Thread.currentThread().interrupt();
            mailbox.enqueue(this);
          }
        };
    mailbox.enqueue(publication);
    var result = coordinatorResult(scope, mailbox, completed);
    var coordinator = new Thread(result, "interrupted-busy-island-coordinator");
    coordinator.start();
    try {
      assertThatThrownBy(() -> result.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(InterruptedException.class);
    } finally {
      stop(coordinator, mailbox);
    }
  }

  @Test
  void idleCoordinatorReleasesItsPartitionRunnablePermit() throws Exception {
    var scope = new SolverScope<Object>();
    var permits = new Semaphore(1);
    scope.setRunnableThreadSemaphore(permits);
    var mailbox = mailbox(scope, 1);
    Queue<Future<Void>> completed = new ConcurrentLinkedQueue<>();
    var result = coordinatorResult(scope, mailbox, completed);
    var coordinator = new Thread(result, "yielding-island-coordinator");
    coordinator.start();
    boolean permitHeld = false;
    try {
      awaitWaiting(coordinator);
      assertThat(permitHeld = permits.tryAcquire()).isTrue();
      new IslandTask(() -> {}, completed, mailbox.getSignal()).run();
      permits.release();
      permitHeld = false;

      assertThat(result.get(5, TimeUnit.SECONDS)).isNull();
      assertThat(permits.availablePermits()).isEqualTo(1);
    } finally {
      if (permitHeld) {
        permits.release();
      }
      stop(coordinator, mailbox);
    }
  }

  private static FutureTask<Void> coordinatorResult(
      SolverScope<Object> scope,
      GlobalBestPropagator<Object> mailbox,
      Queue<Future<Void>> completed) {
    return new FutureTask<>(
        () -> {
          try {
            DefaultIslandModelPhase.awaitAgents(scope, mailbox, completed, 1);
            return null;
          } finally {
            scope.destroyYielding();
          }
        });
  }

  @SuppressWarnings("unchecked")
  private static GlobalBestPropagator<Object> mailbox(SolverScope<Object> scope, int capacity) {
    return new GlobalBestPropagator<>(
        new SharedGlobalState<>(),
        scope,
        (SolverEventSupport<Object>) mock(SolverEventSupport.class),
        EventProducerId.customPhase(0),
        capacity);
  }

  private static void awaitWaiting(Thread thread) {
    await().untilAsserted(() -> assertThat(thread.getState()).isEqualTo(Thread.State.WAITING));
  }

  private static void stop(Thread thread, GlobalBestPropagator<?> mailbox)
      throws InterruptedException {
    mailbox.stop();
    thread.interrupt();
    thread.join(5000);
    assertThat(thread.isAlive()).isFalse();
  }
}
