package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testutil.MockClock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class GlobalBestPropagatorMailboxTest {

  @Test
  @SuppressWarnings("unchecked")
  void delayedDeliveryRetainsPublicationTimesAndTheFirstInitializedScore() {
    var clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var scope = new SolverScope<String>(clock);
    var director = (InnerScoreDirector<String, ?>) mock(InnerScoreDirector.class);
    when(director.cloneSolution("initialized")).thenReturn("initialized clone");
    when(director.cloneSolution("improved")).thenReturn("improved clone");
    scope.setScoreDirector(director);
    scope.setBestScore(InnerScore.withUnassignedCount(SimpleScore.ZERO, 1));
    scope.startingNow();
    var events = new ArrayList<BestSolutionChangedEvent<String>>();
    var eventSupport = new SolverEventSupport<>((Solver<String>) mock(Solver.class));
    eventSupport.addEventListener(events::add);
    var state = new SharedGlobalState<String>();
    state.reset(clock, null);
    var mailbox =
        new GlobalBestPropagator<>(state, scope, eventSupport, EventProducerId.customPhase(0), 2);
    mailbox.start();
    try {
      clock.tick(Duration.ofMillis(100));
      state.tryUpdate("initialized", InnerScore.fullyAssigned(SimpleScore.of(-20)));
      clock.tick(Duration.ofMillis(100));
      state.tryUpdate("improved", InnerScore.fullyAssigned(SimpleScore.of(-10)));
      clock.tick(Duration.ofHours(1));
      assertThat(events).isEmpty();

      assertThat(mailbox.drain()).isEqualTo(2);

      assertThat(events)
          .extracting(BestSolutionChangedEvent::getTimeMillisSpent)
          .containsExactly(100L, 200L);
      assertThat(events)
          .extracting(BestSolutionChangedEvent::getNewBestSolution)
          .containsExactly("initialized clone", "improved clone");
      assertThat(scope.<SimpleScore>getStartingInitializedScore()).isEqualTo(SimpleScore.of(-20));
      assertThat(scope.getBestSolution()).isEqualTo("improved clone");
      assertThat(scope.getBestSolutionTimeMillis()).isEqualTo(200L);
    } finally {
      mailbox.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  @SuppressWarnings("unchecked")
  void preservesOrderAndBlocksPublicationWhenTheConfiguredCapacityIsFull(int capacity)
      throws InterruptedException {
    var mailbox =
        new GlobalBestPropagator<>(
            new SharedGlobalState<String>(),
            new SolverScope<>(),
            (SolverEventSupport<String>) mock(SolverEventSupport.class),
            EventProducerId.customPhase(0),
            capacity);
    var delivered = new ArrayList<Integer>();
    var callbackThreads = new ArrayList<Thread>();
    for (int i = 0; i < capacity; i++) {
      var index = i;
      mailbox.enqueue(
          () -> {
            delivered.add(index);
            callbackThreads.add(Thread.currentThread());
          });
    }
    var published = new AtomicBoolean();
    var producer =
        new Thread(
            () -> {
              mailbox.enqueue(
                  () -> {
                    delivered.add(capacity);
                    callbackThreads.add(Thread.currentThread());
                  });
              published.set(true);
            },
            "full-publication-mailbox");
    producer.start();
    try {
      awaitWaiting(producer);
      assertThat(published).isFalse();
      assertThat(delivered).isEmpty();

      assertThat(mailbox.drain()).isEqualTo(capacity);
      producer.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(producer.isAlive()).isFalse();
      assertThat(published).isTrue();
      assertThat(mailbox.drain()).isEqualTo(1);
      assertThat(mailbox.drain()).isZero();
      assertThat(delivered)
          .containsExactlyElementsOf(
              java.util.stream.IntStream.rangeClosed(0, capacity).boxed().toList());
      assertThat(callbackThreads).containsOnly(Thread.currentThread());
    } finally {
      mailbox.stop();
      producer.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(producer.isAlive()).isFalse();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void closingReleasesAPublisherThatHoldsTheSharedStateLockAndDiscardsPendingEvents()
      throws InterruptedException {
    var state = new SharedGlobalState<String>();
    var events = (SolverEventSupport<String>) mock(SolverEventSupport.class);
    var mailbox =
        new GlobalBestPropagator<>(
            state, new SolverScope<>(), events, EventProducerId.customPhase(0), 1);
    var published = new AtomicBoolean();
    var producer =
        new Thread(
            () -> {
              state.tryUpdate("second", InnerScore.fullyAssigned(SimpleScore.of(2)));
              published.set(true);
            },
            "shared-state-publication-lock");
    mailbox.start();
    state.tryUpdate("first", InnerScore.fullyAssigned(SimpleScore.of(1)));
    producer.start();
    try {
      awaitWaiting(producer);
      assertThat(published).isFalse();

      mailbox.stop();

      producer.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(producer.isAlive()).isFalse();
      assertThat(published).isTrue();
      assertThat(mailbox.drain()).isZero();
      state.tryUpdate("after close", InnerScore.fullyAssigned(SimpleScore.of(3)));
      mailbox.enqueue(
          () -> {
            throw new AssertionError("A closed mailbox must reject late publications.");
          });
      assertThat(mailbox.drain()).isZero();
      verifyNoInteractions(events);
    } finally {
      mailbox.stop();
      producer.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(producer.isAlive()).isFalse();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void interruptingABlockedPublicationPreservesTheInterruptAndDoesNotEnqueueIt()
      throws InterruptedException {
    var mailbox =
        new GlobalBestPropagator<>(
            new SharedGlobalState<String>(),
            new SolverScope<>(),
            (SolverEventSupport<String>) mock(SolverEventSupport.class),
            EventProducerId.customPhase(0),
            1);
    var delivered = new ArrayList<String>();
    mailbox.enqueue(() -> delivered.add("first"));
    var failure = new AtomicReference<Throwable>();
    var interrupted = new AtomicBoolean();
    var producer =
        new Thread(
            () -> {
              try {
                mailbox.enqueue(() -> delivered.add("interrupted"));
              } catch (Throwable thrown) {
                failure.set(thrown);
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            },
            "interrupted-publication-mailbox");
    producer.start();
    try {
      awaitWaiting(producer);
      producer.interrupt();
      producer.join(TimeUnit.SECONDS.toMillis(5));

      assertThat(producer.isAlive()).isFalse();
      assertThat(failure.get())
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(interrupted).isTrue();
      assertThat(mailbox.drain()).isEqualTo(1);
      assertThat(delivered).isEqualTo(List.of("first"));
    } finally {
      mailbox.stop();
      producer.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(producer.isAlive()).isFalse();
    }
  }

  private static void awaitWaiting(Thread producer) {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (producer.getState() != Thread.State.WAITING
        && producer.isAlive()
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertThat(producer.getState()).isEqualTo(Thread.State.WAITING);
  }
}
