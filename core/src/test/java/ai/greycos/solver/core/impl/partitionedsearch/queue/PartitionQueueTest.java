package ai.greycos.solver.core.impl.partitionedsearch.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

/** Tests for {@link PartitionQueue}. */
class PartitionQueueTest {

  @Test
  void constructor() {
    var queue = new PartitionQueue<TestdataSolution>(3);
    assertThat(queue).isNotNull();
  }

  @Test
  void singlePartitionMoveAndFinish() {
    var queue = new PartitionQueue<TestdataSolution>(1);
    var move = createMove(0);

    queue.addMove(0, move);
    queue.addFinish(0, 1000);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).hasSize(1);
    assertThat(moves.get(0)).isSameAs(move);
    assertThat(queue.getPartsCalculationCount()).isEqualTo(1000);
  }

  @Test
  void multiplePartitionsAllFinish() {
    var queue = new PartitionQueue<TestdataSolution>(3);
    var move0 = createMove(0);
    var move1 = createMove(1);
    var move2 = createMove(2);

    queue.addMove(0, move0);
    queue.addMove(1, move1);
    queue.addMove(2, move2);
    queue.addFinish(0, 500);
    queue.addFinish(1, 300);
    queue.addFinish(2, 200);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).hasSize(3);
    assertThat(moves).containsExactly(move0, move1, move2);
    assertThat(queue.getPartsCalculationCount()).isEqualTo(1000);
  }

  @Test
  void moveDeduplicationOnlyLatestReturned() {
    var queue = new PartitionQueue<TestdataSolution>(2);
    var move1 = createMove(0);
    var move2 = createMove(0);
    var move3 = createMove(1);

    // Add 2 moves from partition 0, should only get the latest (move2)
    queue.addMove(0, move1);
    queue.addMove(0, move2);
    queue.addMove(1, move3);
    queue.addFinish(0, 100);
    queue.addFinish(1, 100);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).hasSize(2);
    assertThat(moves.get(0)).isSameAs(move2); // Latest from partition 0
    assertThat(moves.get(1)).isSameAs(move3);
  }

  @Test
  void moveDeduplicationSupersededNotReturned() {
    var queue = new PartitionQueue<TestdataSolution>(1);
    var move1 = createMove(0);
    var move2 = createMove(0);
    var move3 = createMove(0);

    // Add 3 moves, only last should be returned
    queue.addMove(0, move1);
    queue.addMove(0, move2);
    queue.addMove(0, move3);
    queue.addFinish(0, 100);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).hasSize(1);
    assertThat(moves.get(0)).isSameAs(move3);
  }

  @Test
  void iteratorBlocksUntilAllFinished() throws InterruptedException {
    var queue = new PartitionQueue<TestdataSolution>(2);
    var move0 = createMove(0);
    var move1 = createMove(1);

    var startLatch = new CountDownLatch(1);
    var iterationCompleteLatch = new CountDownLatch(1);

    queue.addMove(0, move0);
    queue.addMove(1, move1);

    // Start iteration in separate thread
    var thread =
        new Thread(
            () -> {
              try {
                startLatch.countDown();
                List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
                for (PartitionChangeMove<TestdataSolution> m : queue) {
                  moves.add(m);
                }
                assertThat(moves).hasSize(2);
              } finally {
                iterationCompleteLatch.countDown();
              }
            });
    thread.start();

    // Wait for thread to start and block waiting for FINISH events
    startLatch.await();
    Thread.sleep(100); // Give it time to block

    // Now send FINISH events
    queue.addFinish(0, 100);
    queue.addFinish(1, 100);

    // Wait for iteration to complete
    assertThat(iterationCompleteLatch.await(5, TimeUnit.SECONDS)).isTrue();
    thread.join();
  }

  @Test
  void exceptionPropagatedImmediately() {
    var queue = new PartitionQueue<TestdataSolution>(2);
    var move = createMove(0);
    var exception = new RuntimeException("Test exception");

    queue.addMove(0, move);
    queue.addExceptionThrown(1, exception);

    assertThatThrownBy(
            () -> {
              for (PartitionChangeMove<TestdataSolution> m : queue) {
                // This will throw when it encounters the exception
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasCauseExactlyInstanceOf(RuntimeException.class)
        .hasMessageContaining("partIndex (1)")
        .hasMessageContaining("Relayed here in the parent thread");
  }

  @Test
  void calculationCountAggregation() {
    var queue = new PartitionQueue<TestdataSolution>(3);

    queue.addFinish(0, 100L);
    queue.addFinish(1, 200L);
    queue.addFinish(2, 300L);

    // Consume the queue (iterate through it)
    for (@SuppressWarnings("unused") PartitionChangeMove<TestdataSolution> m : queue) {
      // Just consume
    }

    assertThat(queue.getPartsCalculationCount()).isEqualTo(600L);
  }

  @Test
  void concurrentAccessMultipleThreads() throws InterruptedException {
    int threadCount = 3;
    int partitionsPerThread = 2;
    var queue = new PartitionQueue<TestdataSolution>(threadCount * partitionsPerThread);
    var executor = Executors.newFixedThreadPool(threadCount);
    var movesAdded = new AtomicInteger(0);
    var finishLatch = new CountDownLatch(threadCount);

    try {
      // Each thread adds moves and finishes for its partitions
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        executor.submit(
            () -> {
              try {
                int startPartition = threadId * partitionsPerThread;
                for (int p = 0; p < partitionsPerThread; p++) {
                  int partIndex = startPartition + p;
                  var move = createMove(partIndex);
                  queue.addMove(partIndex, move);
                  queue.addFinish(partIndex, 100);
                  movesAdded.incrementAndGet();
                }
              } finally {
                finishLatch.countDown();
              }
            });
      }

      // Wait for all threads to finish
      assertThat(finishLatch.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(movesAdded.get()).isEqualTo(threadCount * partitionsPerThread);

      // Consume all moves
      List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
      for (PartitionChangeMove<TestdataSolution> m : queue) {
        moves.add(m);
      }

      assertThat(moves).hasSize(threadCount * partitionsPerThread);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void noMovesOnlyFinish() {
    var queue = new PartitionQueue<TestdataSolution>(2);

    queue.addFinish(0, 100);
    queue.addFinish(1, 200);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).isEmpty();
    assertThat(queue.getPartsCalculationCount()).isEqualTo(300);
  }

  @Test
  void singlePartition() {
    var queue = new PartitionQueue<TestdataSolution>(1);
    var move = createMove(0);

    queue.addMove(0, move);
    queue.addFinish(0, 1000);

    List<PartitionChangeMove<TestdataSolution>> moves = new ArrayList<>();
    for (PartitionChangeMove<TestdataSolution> m : queue) {
      moves.add(m);
    }

    assertThat(moves).hasSize(1);
    assertThat(moves.get(0)).isSameAs(move);
  }

  // Helper method to create mock PartitionChangeMove for testing
  private PartitionChangeMove<TestdataSolution> createMove(int partIndex) {
    // PartitionQueue doesn't call methods on the move, just stores and returns it
    // So a simple mock is sufficient for testing queue behavior
    return mock(PartitionChangeMove.class);
  }
}
