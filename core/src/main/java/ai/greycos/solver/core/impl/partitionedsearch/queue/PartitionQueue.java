package ai.greycos.solver.core.impl.partitionedsearch.queue;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;

import org.jspecify.annotations.NullMarked;

/**
 * Thread-safe queue for communicating partition improvements to the parent solver.
 *
 * <p>Supports multiple producer threads (partition solvers) and single consumer thread (parent
 * solver). Uses event deduplication via atomic event indexing to ensure only the latest improvement
 * per partition is applied.
 *
 * @param <Solution_> solution type, class with {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
@NullMarked
public class PartitionQueue<Solution_> implements Iterable<PartitionChangeMove<Solution_>> {

  /** Blocking queue for events */
  private final ArrayBlockingQueue<PartitionChangedEvent<Solution_>> queue;

  /** Tracks the latest move event per partition for deduplication */
  private final ConcurrentHashMap<Integer, PartitionChangedEvent<Solution_>> moveEventMap =
      new ConcurrentHashMap<>();

  /** Tracks the next event index per partition */
  private final ConcurrentHashMap<Integer, AtomicLong> nextEventIndexMap =
      new ConcurrentHashMap<>();

  /** Tracks processed event indices per partition */
  private final ConcurrentHashMap<Integer, Long> processedEventIndexMap = new ConcurrentHashMap<>();

  /** Tracks calculation counts per partition */
  private final ConcurrentHashMap<Integer, Long> partsCalculationCountMap =
      new ConcurrentHashMap<>();

  /** Number of partitions */
  private final int partCount;

  /** Number of finished partitions */
  private int finishedPartCount = 0;

  /** Exception thrown by a partition thread, if any */
  private volatile Throwable exception = null;

  /**
   * Creates a new partition queue.
   *
   * @param partCount Number of partitions
   */
  public PartitionQueue(int partCount) {
    this.partCount = partCount;
    // Capacity is partCount * 100 to buffer events
    this.queue = new ArrayBlockingQueue<>(partCount * 100);
    // Initialize event index counters
    for (int i = 0; i < partCount; i++) {
      nextEventIndexMap.put(i, new AtomicLong(0));
    }
  }

  /**
   * Adds a move event from a partition.
   *
   * <p>Called by partition solver threads.
   *
   * @param partIndex Partition index
   * @param move The partition change move
   */
  public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event =
        new PartitionChangedEvent<>(partIndex, eventIndex, move);
    moveEventMap.put(partIndex, event);
    queue.add(event);
  }

  /**
   * Adds a finish event from a partition.
   *
   * <p>Called by partition solver threads when they complete.
   *
   * @param partIndex Partition index
   * @param calculationCount Score calculation count
   */
  public void addFinish(int partIndex, long calculationCount) {
    partsCalculationCountMap.put(partIndex, calculationCount);
    finishedPartCount++;
    // Add a finish event to signal completion
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    queue.add(new PartitionChangedEvent<>(partIndex, eventIndex, calculationCount));
  }

  /**
   * Adds an exception event from a partition.
   *
   * <p>Called by partition solver threads when they fail.
   *
   * @param partIndex Partition index
   * @param throwable The exception
   */
  public void addExceptionThrown(int partIndex, Throwable throwable) {
    this.exception = throwable;
    // Add an exception event to unblock the iterator
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    queue.add(new PartitionChangedEvent<>(partIndex, eventIndex, throwable));
  }

  /**
   * Gets the total score calculation count from all partitions.
   *
   * @return Total calculation count
   */
  public long getPartsCalculationCount() {
    return partsCalculationCountMap.values().stream().mapToLong(Long::longValue).sum();
  }

  /**
   * Checks if all partitions have finished.
   *
   * @return true if all partitions finished
   */
  public boolean areAllPartitionsFinished() {
    return finishedPartCount >= partCount;
  }

  /**
   * Gets the exception thrown by any partition, if any.
   *
   * @return The exception or null
   */
  public Throwable getException() {
    return exception;
  }

  /**
   * Returns an iterator over partition change moves.
   *
   * <p>The iterator blocks until all partitions finish or an exception occurs.
   *
   * @return Iterator over moves
   */
  @Override
  public Iterator<PartitionChangeMove<Solution_>> iterator() {
    return new PartitionQueueIterator();
  }

  /**
   * Iterator that consumes the queue and returns moves.
   *
   * <p>Implements event deduplication: if a partition emits multiple improvements before the parent
   * consumes them, only the latest is applied.
   */
  private class PartitionQueueIterator
      extends UpcomingSelectionIterator<PartitionChangeMove<Solution_>> {

    @Override
    protected PartitionChangeMove<Solution_> createUpcomingSelection() {
      try {
        while (true) {
          PartitionChangedEvent<Solution_> event = queue.take();

          // Check for exception
          if (exception != null) {
            throw new IllegalStateException("Partition solver failed", exception);
          }

          // Handle different event types
          PartitionChangedEvent.PartitionChangedEventType eventType = event.getType();
          if (eventType == PartitionChangedEvent.PartitionChangedEventType.FINISHED) {
            // Track calculation count
            Long calcCount = event.getPartCalculationCount();
            if (calcCount != null) {
              partsCalculationCountMap.put(event.getPartIndex(), calcCount);
            }
            finishedPartCount++;
            if (areAllPartitionsFinished()) {
              return noUpcomingSelection();
            }
            continue;
          } else if (eventType
              == PartitionChangedEvent.PartitionChangedEventType.EXCEPTION_THROWN) {
            // Exception already stored in field, throw it
            throw new IllegalStateException("Partition solver failed", exception);
          }

          // Handle MOVE events
          int partIndex = event.getPartIndex();
          PartitionChangedEvent<Solution_> latestMoveEvent = moveEventMap.get(partIndex);

          // Event deduplication: only return the latest event for this partition
          Long processedIndex = processedEventIndexMap.get(partIndex);
          if (processedIndex == null || event.getEventIndex() > processedIndex) {
            processedEventIndexMap.put(partIndex, event.getEventIndex());
            PartitionChangeMove<Solution_> move = event.getMove();
            if (move != null) {
              return move;
            }
          }
          // Event already superseded, continue to next
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return noUpcomingSelection();
      }
    }
  }
}
