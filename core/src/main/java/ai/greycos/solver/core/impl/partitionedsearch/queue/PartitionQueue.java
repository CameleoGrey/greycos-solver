package ai.greycos.solver.core.impl.partitionedsearch.queue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe queue for communicating partition improvements to parent solver.
 *
 * <p>Multiple producers (partition threads) add events; single consumer (parent) iterates moves.
 * Deduplicates superseded events via atomic indexing.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public class PartitionQueue<Solution_> implements Iterable<PartitionChangeMove<Solution_>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionQueue.class);

  private final BlockingQueue<PartitionChangedEvent<Solution_>> queue;
  private final Map<Integer, PartitionChangedEvent<Solution_>> moveEventMap; // Key is partIndex

  private final Map<Integer, AtomicLong> nextEventIndexMap;

  private int openPartCount;
  private long partsCalculationCount;
  private final Map<Integer, Long> processedEventIndexMap; // Key is partIndex

  public PartitionQueue(int partCount) {
    queue = new ArrayBlockingQueue<>(partCount * 100);
    moveEventMap = new ConcurrentHashMap<>(partCount);
    Map<Integer, AtomicLong> nextEventIndexMap = new HashMap<>(partCount);
    for (int i = 0; i < partCount; i++) {
      nextEventIndexMap.put(i, new AtomicLong(0));
    }
    this.nextEventIndexMap = Collections.unmodifiableMap(nextEventIndexMap);
    openPartCount = partCount;
    partsCalculationCount = 0L;
    processedEventIndexMap = new HashMap<>(partCount);
    for (int i = 0; i < partCount; i++) {
      processedEventIndexMap.put(i, -1L);
    }
  }

  public void addMove(int partIndex, PartitionChangeMove<Solution_> move) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event =
        new PartitionChangedEvent<>(partIndex, eventIndex, move);
    moveEventMap.put(event.getPartIndex(), event);
    queue.add(event);
  }

  public void addFinish(int partIndex, long partCalculationCount) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event =
        new PartitionChangedEvent<>(partIndex, eventIndex, partCalculationCount);
    queue.add(event);
  }

  public void addExceptionThrown(int partIndex, Throwable throwable) {
    long eventIndex = nextEventIndexMap.get(partIndex).getAndIncrement();
    PartitionChangedEvent<Solution_> event =
        new PartitionChangedEvent<>(partIndex, eventIndex, throwable);
    queue.add(event);
  }

  public long getPartsCalculationCount() {
    return partsCalculationCount;
  }

  @Override
  public Iterator<PartitionChangeMove<Solution_>> iterator() {
    return new PartitionQueueIterator();
  }

  private class PartitionQueueIterator
      extends UpcomingSelectionIterator<PartitionChangeMove<Solution_>> {

    @Override
    protected PartitionChangeMove<Solution_> createUpcomingSelection() {
      while (true) {
        PartitionChangedEvent<Solution_> triggerEvent;
        try {
          triggerEvent = queue.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(
              "Solver thread was interrupted in Partitioned Search.", e);
        }
        switch (triggerEvent.getType()) {
          case MOVE:
            int partIndex = triggerEvent.getPartIndex();
            long processedEventIndex = processedEventIndexMap.get(partIndex);
            if (triggerEvent.getEventIndex() <= processedEventIndex) {
              // Skip this one because it or a better version was already processed
              LOGGER.trace("    Skipped event of partIndex ({}).", partIndex);
              continue;
            }
            PartitionChangedEvent<Solution_> latestMoveEvent = moveEventMap.get(partIndex);
            processedEventIndexMap.put(partIndex, latestMoveEvent.getEventIndex());
            return latestMoveEvent.getMove();
          case FINISHED:
            openPartCount--;
            partsCalculationCount += triggerEvent.getPartCalculationCount();
            if (openPartCount <= 0) {
              return noUpcomingSelection();
            } else {
              continue;
            }
          case EXCEPTION_THROWN:
            throw new IllegalStateException(
                "The partition child thread with partIndex ("
                    + triggerEvent.getPartIndex()
                    + ") has thrown an exception."
                    + " Relayed here in the parent thread.",
                triggerEvent.getThrowable());
          default:
            throw new IllegalStateException(
                "The partitionChangedEventType ("
                    + triggerEvent.getType()
                    + ") is not implemented.");
        }
      }
    }
  }
}
