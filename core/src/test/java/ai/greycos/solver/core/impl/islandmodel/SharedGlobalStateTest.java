package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SharedGlobalState}. */
class SharedGlobalStateTest {

  private SharedGlobalState<String> globalState;

  @BeforeEach
  void setUp() {
    globalState = new SharedGlobalState<>();
  }

  @Test
  void initialBestIsNull() {
    assertNull(globalState.getBestSolution());
    assertNull(globalState.getBestScore());
  }

  @Test
  void tryUpdateWhenBestIsNull() {
    String solution = "solution1";
    SimpleScore score = SimpleScore.of(-10);

    boolean updated = globalState.tryUpdate(solution, score);

    assertTrue(updated);
    assertEquals(solution, globalState.getBestSolution());
    assertEquals(score, globalState.getBestScore());
  }

  @Test
  void tryUpdateWithBetterScore() {
    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    // Better score (higher is better)
    boolean updated = globalState.tryUpdate("solution2", SimpleScore.of(-5));

    assertTrue(updated);
    assertEquals("solution2", globalState.getBestSolution());
    assertEquals(SimpleScore.of(-5), globalState.getBestScore());
  }

  @Test
  void tryUpdateWithWorseScore() {
    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    // Worse score (lower is worse)
    boolean updated = globalState.tryUpdate("solution2", SimpleScore.of(-20));

    assertFalse(updated);
    assertEquals("solution1", globalState.getBestSolution());
    assertEquals(SimpleScore.of(-10), globalState.getBestScore());
  }

  @Test
  void tryUpdateWithSameScore() {
    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    // Same score - should not update
    boolean updated = globalState.tryUpdate("solution2", SimpleScore.of(-10));

    assertFalse(updated);
    assertEquals("solution1", globalState.getBestSolution());
  }

  @Test
  void tryUpdateWithNullSolutionThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          globalState.tryUpdate(null, SimpleScore.of(-10));
        });
  }

  @Test
  void tryUpdateWithNullScoreThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          globalState.tryUpdate("solution", null);
        });
  }

  @Test
  void addObserverAndNotify() {
    List<String> observedSolutions = new ArrayList<>();
    globalState.addObserver(solution -> observedSolutions.add(solution));

    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    assertEquals(1, observedSolutions.size());
    assertEquals("solution1", observedSolutions.get(0));
  }

  @Test
  void removeObserverDoesNotNotify() {
    List<String> observedSolutions = new ArrayList<>();
    java.util.function.Consumer<String> observer = solution -> observedSolutions.add(solution);

    globalState.addObserver(observer);
    globalState.removeObserver(observer);

    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    assertTrue(observedSolutions.isEmpty());
  }

  @Test
  void multipleObserversAllNotified() {
    List<String> observed1 = new ArrayList<>();
    List<String> observed2 = new ArrayList<>();
    List<String> observed3 = new ArrayList<>();

    globalState.addObserver(solution -> observed1.add(solution));
    globalState.addObserver(solution -> observed2.add(solution));
    globalState.addObserver(solution -> observed3.add(solution));

    String solution = "solution1";
    globalState.tryUpdate(solution, SimpleScore.of(-10));

    assertEquals(1, observed1.size());
    assertEquals(1, observed2.size());
    assertEquals(1, observed3.size());
    assertEquals(solution, observed1.get(0));
    assertEquals(solution, observed2.get(0));
    assertEquals(solution, observed3.get(0));
  }

  @Test
  void observerExceptionDoesNotAffectOtherObservers() {
    List<String> observed1 = new ArrayList<>();
    List<String> observed2 = new ArrayList<>();

    globalState.addObserver(
        solution -> {
          observed1.add(solution);
          throw new RuntimeException("Test exception");
        });
    globalState.addObserver(solution -> observed2.add(solution));

    // Should not throw exception
    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    // Both observers should be called
    assertEquals(1, observed1.size());
    assertEquals(1, observed2.size());
  }

  @Test
  void concurrentUpdatesAreThreadSafe() throws InterruptedException {
    int threadCount = 10;
    int updatesPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger updateCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int j = 0; j < updatesPerThread; j++) {
                String solution = "thread" + threadId + "_solution" + j;
                SimpleScore score = SimpleScore.of(-j);
                if (globalState.tryUpdate(solution, score)) {
                  updateCount.incrementAndGet();
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
    executor.shutdown();

    // Should have at least one update (the best one)
    assertTrue(updateCount.get() > 0);
    assertNotNull(globalState.getBestSolution());
    assertEquals(SimpleScore.of(0), globalState.getBestScore());
  }

  @Test
  void resetClearsState() {
    globalState.tryUpdate("solution1", SimpleScore.of(-10));

    globalState.reset();

    assertNull(globalState.getBestSolution());
    assertNull(globalState.getBestScore());
  }

  @Test
  void getObserversReturnsCopy() {
    globalState.addObserver(solution -> {});

    List<java.util.function.Consumer<String>> observers = globalState.getObservers();

    assertEquals(1, observers.size());

    // Modifying returned list should not affect global state
    observers.clear();

    assertEquals(1, globalState.getObservers().size());
  }
}
