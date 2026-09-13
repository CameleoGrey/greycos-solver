package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.testutil.MockClock;

import org.junit.jupiter.api.Test;

class SharedGlobalStateTest {

  @Test
  void concurrentUpdatesAreThreadSafe() throws InterruptedException {
    SharedGlobalState<String> state = new SharedGlobalState<>();
    int threadCount = 10;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int scoreValue = i;
      new Thread(
              () -> {
                try {
                  startLatch.await();
                  SimpleScore score = SimpleScore.of(scoreValue);
                  state.tryUpdate("solution" + scoreValue, InnerScore.fullyAssigned(score));
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  doneLatch.countDown();
                }
              })
          .start();
    }

    startLatch.countDown();
    doneLatch.await();

    assertThat(state.getBestSolution()).isEqualTo("solution" + (threadCount - 1));
    assertThat(state.getBestScore()).isEqualTo(SimpleScore.of(threadCount - 1));
  }

  @Test
  void observerIsNotifiedOnUpdate() {
    SharedGlobalState<String> state = new SharedGlobalState<>();
    AtomicReference<String> notifiedSolution = new AtomicReference<>();
    AtomicReference<SimpleScore> notifiedScore = new AtomicReference<>();
    AtomicReference<Integer> notificationCount = new AtomicReference<>(0);

    state.addObserver(
        snapshot -> {
          notifiedSolution.set(snapshot.getSolution());
          notifiedScore.set((SimpleScore) snapshot.getScore());
          notificationCount.getAndSet(notificationCount.get() + 1);
        });

    state.tryUpdate("test solution", InnerScore.fullyAssigned(SimpleScore.of(10)));

    assertThat(notifiedSolution.get()).isEqualTo("test solution");
    assertThat(notifiedScore.get()).isEqualTo(SimpleScore.of(10));
    assertThat(notificationCount.get()).isEqualTo(1);
  }

  @Test
  void observerIsNotNotifiedOnWorseScore() {
    SharedGlobalState<String> state = new SharedGlobalState<>();
    AtomicReference<Integer> notificationCount = new AtomicReference<>(0);

    state.addObserver(snapshot -> notificationCount.getAndSet(notificationCount.get() + 1));

    state.tryUpdate("first", InnerScore.fullyAssigned(SimpleScore.of(10)));
    state.tryUpdate("second", InnerScore.fullyAssigned(SimpleScore.of(5)));

    assertThat(notificationCount.get()).isEqualTo(1);
  }

  @Test
  void resetClearsState() {
    SharedGlobalState<String> state = new SharedGlobalState<>();

    state.tryUpdate("solution", InnerScore.fullyAssigned(SimpleScore.of(10)));
    assertThat(state.getBestSolution()).isNotNull();
    assertThat(state.getBestScore()).isNotNull();

    state.reset();
    assertThat(state.getBestSolution()).isNull();
    assertThat(state.getBestScore()).isNull();
  }

  @Test
  void getObserversReturnsCopy() {
    SharedGlobalState<String> state = new SharedGlobalState<>();
    state.addObserver(snapshot -> {});

    List observers1 = state.getObservers();
    List observers2 = state.getObservers();

    assertThat(observers1).isNotSameAs(observers2);
    assertThat(observers1).hasSize(1);
  }

  @Test
  void updateRejectsNullSolution() {
    SharedGlobalState<String> state = new SharedGlobalState<>();

    assertThatThrownBy(() -> state.tryUpdate(null, InnerScore.fullyAssigned(SimpleScore.of(10))))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Candidate solution cannot be null");
  }

  @Test
  void updateRejectsNullScore() {
    SharedGlobalState<String> state = new SharedGlobalState<>();

    assertThatThrownBy(() -> state.tryUpdate("solution", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Candidate score cannot be null");
  }

  @Test
  void getBestSnapshotProvidesConsistentState() {
    SharedGlobalState<String> state = new SharedGlobalState<>();

    state.tryUpdate("solution", InnerScore.fullyAssigned(SimpleScore.of(10)));
    var snapshot = state.getBestSnapshot();

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.getSolution()).isEqualTo("solution");
    assertThat(snapshot.getScore()).isEqualTo(SimpleScore.of(10));
  }

  @Test
  void fullyAssignedScoreBeatsHigherRawScoreWithUnassignedValues() {
    SharedGlobalState<String> state = new SharedGlobalState<>();

    state.tryUpdate("fullyAssigned", InnerScore.fullyAssigned(SimpleScore.of(0)));
    state.tryUpdate("unassigned", InnerScore.withUnassignedCount(SimpleScore.of(1000), 1));

    assertThat(state.getBestSolution()).isEqualTo("fullyAssigned");
    assertThat(state.getBestScore()).isEqualTo(SimpleScore.of(0));
  }

  @Test
  void progressIsTimestampedOnlyForStrictImprovementsAndResetBetweenSolves() {
    var clock = new MockClock(Clock.systemUTC());
    var state = new SharedGlobalState<String>();
    var progress = new CopyOnWriteArrayList<SharedGlobalState.BestSolutionSnapshot<String>>();
    state.reset(clock, progress::add);
    state.tryUpdate("initial", InnerScore.fullyAssigned(SimpleScore.ZERO));
    var initial = state.getBestSnapshot();
    clock.tick(Duration.ofMillis(100));
    assertThat(state.tryUpdate("tie", InnerScore.fullyAssigned(SimpleScore.ZERO))).isFalse();
    assertThat(state.tryUpdate("worse", InnerScore.fullyAssigned(SimpleScore.of(-1)))).isFalse();
    assertThat(state.getBestSnapshot()).isSameAs(initial);
    state.tryUpdate("better", InnerScore.fullyAssigned(SimpleScore.ONE));
    assertThat(progress).hasSize(2);
    assertThat(progress.get(0).getVersion()).isEqualTo(1L);
    assertThat(progress.get(1).getVersion()).isEqualTo(2L);
    assertThat(progress.get(1).getTimestampMillis() - progress.get(0).getTimestampMillis())
        .isEqualTo(100L);

    state.reset(clock, null);
    state.tryUpdate("next solve", InnerScore.fullyAssigned(SimpleScore.ZERO));
    assertThat(state.getBestSnapshot().getVersion()).isEqualTo(1L);
    assertThat(progress).hasSize(2);
  }

  @Test
  void orderedInternalProgressDoesNotWaitForExternalObservers() throws Exception {
    var state = new SharedGlobalState<String>();
    var progressVersions = new CopyOnWriteArrayList<Long>();
    var enteredObserver = new CountDownLatch(1);
    var releaseObserver = new CountDownLatch(1);
    state.reset(
        new MockClock(Clock.systemUTC()), snapshot -> progressVersions.add(snapshot.getVersion()));
    state.addObserver(
        snapshot -> {
          if (snapshot.getVersion() == 1L) {
            enteredObserver.countDown();
            try {
              if (!releaseObserver.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Observer was not released");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          }
        });
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () -> state.tryUpdate("first", InnerScore.fullyAssigned(SimpleScore.ZERO)));
      assertThat(enteredObserver.await(5, TimeUnit.SECONDS)).isTrue();
      var second =
          executor.submit(
              () -> state.tryUpdate("second", InnerScore.fullyAssigned(SimpleScore.ONE)));
      assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
      assertThat(progressVersions).containsExactly(1L, 2L);
      assertThat(state.getBestSnapshot().getSolution()).isEqualTo("second");
      releaseObserver.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      releaseObserver.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }
}
