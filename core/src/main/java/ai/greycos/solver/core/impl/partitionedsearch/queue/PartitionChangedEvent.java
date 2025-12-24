package ai.greycos.solver.core.impl.partitionedsearch.queue;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;

import org.jspecify.annotations.NullMarked;

/**
 * Event representing a change from a partition solver.
 *
 * <p>Events can be of three types:
 *
 * <ul>
 *   <li>MOVE: A new best solution was found in a partition
 *   <li>FINISHED: The partition solver completed successfully
 *   <li>EXCEPTION_THROWN: The partition solver failed with an exception
 * </ul>
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public final class PartitionChangedEvent<Solution_> {

  private final int partIndex;
  private final long eventIndex;
  private final PartitionChangedEventType type;
  private final PartitionChangeMove<Solution_> move;
  private final Long partCalculationCount;
  private final Throwable throwable;

  /**
   * Creates a MOVE event.
   *
   * @param partIndex Partition index
   * @param eventIndex Sequential event index for this partition
   * @param move The partition change move
   */
  public PartitionChangedEvent(
      int partIndex, long eventIndex, PartitionChangeMove<Solution_> move) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.MOVE;
    this.move = move;
    this.partCalculationCount = null;
    this.throwable = null;
  }

  /**
   * Creates a FINISHED event.
   *
   * @param partIndex Partition index
   * @param eventIndex Sequential event index for this partition
   * @param partCalculationCount Total score calculation count for this partition
   */
  public PartitionChangedEvent(int partIndex, long eventIndex, long partCalculationCount) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.FINISHED;
    this.move = null;
    this.partCalculationCount = partCalculationCount;
    this.throwable = null;
  }

  /**
   * Creates an EXCEPTION_THROWN event.
   *
   * @param partIndex Partition index
   * @param eventIndex Sequential event index for this partition
   * @param throwable The exception that caused the failure
   */
  public PartitionChangedEvent(int partIndex, long eventIndex, Throwable throwable) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.EXCEPTION_THROWN;
    this.move = null;
    this.partCalculationCount = null;
    this.throwable = throwable;
  }

  /**
   * Gets the partition index.
   *
   * @return Partition index
   */
  public int getPartIndex() {
    return partIndex;
  }

  /**
   * Gets the event index.
   *
   * <p>Events from each partition are sequentially indexed to enable deduplication of superseded
   * events.
   *
   * @return Event index for this partition
   */
  public long getEventIndex() {
    return eventIndex;
  }

  /**
   * Gets the event type.
   *
   * @return Event type
   */
  public PartitionChangedEventType getType() {
    return type;
  }

  /**
   * Gets the partition change move (only for MOVE events).
   *
   * @return The move or null if not a MOVE event
   */
  public PartitionChangeMove<Solution_> getMove() {
    return move;
  }

  /**
   * Gets the calculation count (only for FINISHED events).
   *
   * @return Calculation count or null if not a FINISHED event
   */
  public Long getPartCalculationCount() {
    return partCalculationCount;
  }

  /**
   * Gets the exception (only for EXCEPTION_THROWN events).
   *
   * @return The exception or null if not an EXCEPTION_THROWN event
   */
  public Throwable getThrowable() {
    return throwable;
  }

  /** Event types for partition changes. */
  public enum PartitionChangedEventType {
    /** A new best solution was found in a partition */
    MOVE,
    /** The partition solver completed successfully */
    FINISHED,
    /** The partition solver failed with an exception */
    EXCEPTION_THROWN
  }
}
