package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterator for multistage moves in sequential order.
 *
 * <p>Generates all combinations of stage moves in a deterministic sequence (Cartesian product).
 */
public class SequentialMultistageMoveIterator<Solution_> implements Iterator<Move<Solution_>> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SequentialMultistageMoveIterator.class);

  private final List<MoveSelector<Solution_>> stageSelectors;
  private final List<Iterator<Move<Solution_>>> stageIterators;
  private final List<Move<Solution_>> currentMoves;
  private final int stageCount;

  private boolean hasNext = true;
  private boolean initialized = false;

  public SequentialMultistageMoveIterator(@NonNull List<MoveSelector<Solution_>> stageSelectors) {
    this.stageSelectors = List.copyOf(stageSelectors);
    this.stageCount = stageSelectors.size();
    this.stageIterators = new ArrayList<>(stageCount);
    this.currentMoves = new ArrayList<>(stageCount);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Created SequentialMultistageMoveIterator with {} stages", stageCount);
    }

    for (int i = 0; i < stageSelectors.size(); i++) {
      MoveSelector<Solution_> selector = stageSelectors.get(i);
      stageIterators.add(selector.iterator());
    }
  }

  @Override
  public boolean hasNext() {
    if (!initialized) {
      initialize();
    }
    return hasNext;
  }

  @Override
  public Move<Solution_> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    if (!initialized) {
      initialize();
    }

    List<Move<Solution_>> moves = new ArrayList<>(currentMoves);

    advanceIterators();

    return CompositeMove.buildMove(moves);
  }

  private void initialize() {
    if (initialized) {
      return;
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Initializing sequential multistage move iterator");
    }

    currentMoves.clear();
    for (int i = 0; i < stageCount; i++) {
      Iterator<Move<Solution_>> iterator = stageIterators.get(i);
      if (iterator.hasNext()) {
        currentMoves.add(iterator.next());
      } else {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Stage {} has no moves, marking iterator as exhausted", i);
        }
        hasNext = false;
        break;
      }
    }

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Initialization complete, hasNext={}", hasNext);
    }

    initialized = true;
  }

  private void advanceIterators() {
    for (int i = stageCount - 1; i >= 0; i--) {
      Iterator<Move<Solution_>> currentIterator = stageIterators.get(i);

      if (currentIterator.hasNext()) {
        currentMoves.set(i, currentIterator.next());
        return;
      } else {
        stageIterators.set(i, stageSelectors.get(i).iterator());
        if (stageIterators.get(i).hasNext()) {
          currentMoves.set(i, stageIterators.get(i).next());
        }
      }
    }

    hasNext = false;
  }
}
