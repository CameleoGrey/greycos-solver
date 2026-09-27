package greycos.solver.core.impl.partitionedsearch.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PartitionQueueTest {

  @Test
  void coalescesLatestMoveAndKeepsEachPartitionReadyOnce() throws Exception {
    var queue = new PartitionQueue<TestdataSolution>(2);
    var first = move();
    var latest = move();
    var other = move();
    queue.addMove(0, first);
    queue.addMove(1, other);
    queue.addMove(0, latest);

    assertThat(queue.getPendingMoveCount()).isEqualTo(2);
    assertThat(queue.poll(0)).isSameAs(latest);
    assertThat(queue.poll(0)).isSameAs(other);
    assertThat(queue.poll(0)).isNull();
    queue.addMove(0, first);
    assertThat(queue.poll(0)).isSameAs(first);
    assertThat(queue.getPendingMoveCount()).isZero();
  }

  @Test
  void producersCompleteWithoutAConsumerAndStorageStaysBounded() throws Exception {
    var queue = new PartitionQueue<TestdataSolution>(3);
    var executor = Executors.newFixedThreadPool(3);
    var latest = new ArrayList<PartitionChangeMove<TestdataSolution>>();
    var futures = new ArrayList<Future<?>>();
    try {
      for (int index = 0; index < 3; index++) {
        int partIndex = index;
        var move = move();
        latest.add(move);
        futures.add(
            executor.submit(
                () -> {
                  for (int i = 0; i < 10_000; i++) {
                    queue.addMove(partIndex, move);
                  }
                }));
      }
      for (var future : futures) {
        future.get(3, TimeUnit.SECONDS);
      }
      assertThat(queue.getPendingMoveCount()).isEqualTo(3);
      var received = new ArrayList<PartitionChangeMove<TestdataSolution>>();
      for (int i = 0; i < 3; i++) {
        received.add(queue.poll(0));
      }
      assertThat(received).containsExactlyInAnyOrderElementsOf(latest);
      assertThat(queue.poll(0)).isNull();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void interruptedProducerCanPublishWithoutClearingItsInterrupt() throws Exception {
    var queue = new PartitionQueue<TestdataSolution>(1);
    var move = move();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future =
          executor.submit(
              () -> {
                Thread.currentThread().interrupt();
                queue.addMove(0, move);
                return Thread.currentThread().isInterrupted();
              });
      assertThat(future.get(3, TimeUnit.SECONDS)).isTrue();
      assertThat(queue.poll(0)).isSameAs(move);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void stoppingPublicationRetainsPendingMovesAndRejectsNewOnes() throws Exception {
    var queue = new PartitionQueue<TestdataSolution>(2);
    var pending = move();
    queue.addMove(0, pending);
    queue.stopAcceptingMoves();
    queue.addMove(0, move());
    queue.addMove(1, move());

    assertThat(queue.poll(0)).isSameAs(pending);
    assertThat(queue.poll(10_000)).isNull();
  }

  @Test
  void stoppingPublicationWakesTheConsumer() throws Exception {
    var queue = new PartitionQueue<TestdataSolution>(1);
    var polling = new CountDownLatch(1);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future =
          executor.submit(
              () -> {
                polling.countDown();
                return queue.poll(10_000);
              });
      assertThat(polling.await(3, TimeUnit.SECONDS)).isTrue();
      queue.stopAcceptingMoves();
      assertThat(future.get(3, TimeUnit.SECONDS)).isNull();
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void rejectsInvalidBounds() {
    assertThatThrownBy(() -> new PartitionQueue<>(0)).hasMessageContaining("partCount (0)");
    var queue = new PartitionQueue<TestdataSolution>(1);
    assertThatThrownBy(() -> queue.poll(-1)).hasMessageContaining("timeoutMillis (-1)");
    assertThatThrownBy(() -> queue.addMove(1, move()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @SuppressWarnings("unchecked")
  private PartitionChangeMove<TestdataSolution> move() {
    return mock(PartitionChangeMove.class);
  }
}
