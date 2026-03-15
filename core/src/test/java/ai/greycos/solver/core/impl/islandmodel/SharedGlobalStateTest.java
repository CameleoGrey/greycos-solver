package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.score.director.InnerScore;

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
}
