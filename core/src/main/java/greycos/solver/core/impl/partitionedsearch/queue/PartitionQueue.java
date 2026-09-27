package greycos.solver.core.impl.partitionedsearch.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;

import org.jspecify.annotations.Nullable;

/**
 * A bounded mailbox containing at most one pending improvement per partition. Completion and
 * failure are reported separately through the partition tasks' futures.
 *
 * <p>Publication never waits for capacity, including when the parent stops consuming. The monitor
 * protects both the latest moves and their ready indices, so replacing a move cannot lose a wakeup.
 */
public final class PartitionQueue<Solution_> {

  private final List<@Nullable PartitionChangeMove<Solution_>> latestMoves;
  private final ArrayDeque<Integer> readyPartitions;
  private boolean acceptingMoves = true;

  public PartitionQueue(int partCount) {
    if (partCount < 1) {
      throw new IllegalArgumentException("The partCount (" + partCount + ") must be positive.");
    }
    latestMoves = new ArrayList<>(Collections.nCopies(partCount, null));
    readyPartitions = new ArrayDeque<>(partCount);
  }

  public synchronized void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
    Objects.checkIndex(partIndex, latestMoves.size());
    Objects.requireNonNull(move);
    if (!acceptingMoves) {
      return;
    }
    if (latestMoves.set(partIndex, move) == null) {
      readyPartitions.addLast(partIndex);
    }
    notifyAll();
  }

  /** Returns a pending move, or null after the timeout or after publication has stopped. */
  public synchronized @Nullable PartitionChangeMove<Solution_> poll(long timeoutMillis)
      throws InterruptedException {
    if (timeoutMillis < 0L) {
      throw new IllegalArgumentException(
          "The timeoutMillis (" + timeoutMillis + ") must not be negative.");
    }
    long remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    long deadlineNanos = System.nanoTime() + remainingNanos;
    while (readyPartitions.isEmpty() && acceptingMoves && remainingNanos > 0L) {
      TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
      remainingNanos = deadlineNanos - System.nanoTime();
    }
    var partIndex = readyPartitions.pollFirst();
    return partIndex == null ? null : latestMoves.set(partIndex, null);
  }

  /** Stops new publications while retaining the bounded batch already published. */
  public synchronized void stopAcceptingMoves() {
    acceptingMoves = false;
    notifyAll();
  }

  synchronized int getPendingMoveCount() {
    return readyPartitions.size();
  }
}
