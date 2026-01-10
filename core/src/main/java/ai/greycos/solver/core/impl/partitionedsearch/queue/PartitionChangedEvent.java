package ai.greycos.solver.core.impl.partitionedsearch.queue;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;

import org.jspecify.annotations.NullMarked;

/**
 * Event from partition solver (MOVE, FINISHED, or EXCEPTION_THROWN).
 *
 * <p>Sequenced per partition for deduplication; only latest improvement per partition applied.
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

  public PartitionChangedEvent(
      int partIndex, long eventIndex, PartitionChangeMove<Solution_> move) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.MOVE;
    this.move = move;
    this.partCalculationCount = null;
    this.throwable = null;
  }

  public PartitionChangedEvent(int partIndex, long eventIndex, long partCalculationCount) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.FINISHED;
    this.move = null;
    this.partCalculationCount = partCalculationCount;
    this.throwable = null;
  }

  public PartitionChangedEvent(int partIndex, long eventIndex, Throwable throwable) {
    this.partIndex = partIndex;
    this.eventIndex = eventIndex;
    this.type = PartitionChangedEventType.EXCEPTION_THROWN;
    this.move = null;
    this.partCalculationCount = null;
    this.throwable = throwable;
  }

  public int getPartIndex() {
    return partIndex;
  }

  public long getEventIndex() {
    return eventIndex;
  }

  public PartitionChangedEventType getType() {
    return type;
  }

  public PartitionChangeMove<Solution_> getMove() {
    return move;
  }

  public Long getPartCalculationCount() {
    return partCalculationCount;
  }

  public Throwable getThrowable() {
    return throwable;
  }

  public enum PartitionChangedEventType {
    MOVE,
    FINISHED,
    EXCEPTION_THROWN
  }
}
