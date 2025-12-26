package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.NonNull;

/**
 * Iterator for multistage moves in sequential order.
 *
 * <p>Generates all combinations of stage moves in a deterministic sequence (Cartesian product).
 */
public class SequentialMultistageMoveIterator<Solution_> implements Iterator<Move<Solution_>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SequentialMultistageMoveIterator.class);

  private final List<MoveSelector<Solution_>> stageSelectors;
  private final List<Iterator<Move<Solution_>>> stageIterators;
  private final List<Move<Solution_>> currentMoves;
  private final int stageCount;

  private boolean hasNext = true;
  private boolean initialized = false;

  /**
   * Constructs iterator for multistage moves.
   *
   * @param stageSelectors list of move selectors, one per stage, never null, never empty
   */
  public SequentialMultistageMoveIterator(@NonNull List<MoveSelector<Solution_>> stageSelectors) {
    this.stageSelectors = List.copyOf(stageSelectors);
    this.stageCount = stageSelectors.size();
    this.stageIterators = new ArrayList<>(stageCount);
    this.currentMoves = new ArrayList<>(stageCount);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Created SequentialMultistageMoveIterator with {} stages", stageCount);
    }

    // Initialize iterators for each stage
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

    // Get the current combination
    List<Move<Solution_>> moves = new ArrayList<>(currentMoves);

    // Advance to next combination for next call
    advanceIterators();

    // Compose into a single composite move
    return CompositeMove.buildMove(moves);
  }

  /** Initializes the iterator by fetching the first move from each stage. */
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
        // A stage has no moves, so no combinations are possible
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

  /**
   * Advances iterators to next combination using Cartesian product logic.
   *
   * <p>Starts from last stage and works backwards. When a stage is exhausted, it's reset and
   * previous stage is advanced. This implements standard lexicographic ordering of combinations.
   *
   * <p>Example with 2 stages (moves: [A,B] and [X,Y,Z]):
   *
   * <pre>
   * Iteration 1: CompositeMove(A, X)
   * Iteration 2: CompositeMove(A, Y)
   * Iteration 3: CompositeMove(A, Z)
   * Iteration 4: CompositeMove(B, X)
   * Iteration 5: CompositeMove(B, Y)
   * Iteration 6: CompositeMove(B, Z)
   * </pre>
   */
  private void advanceIterators() {
    // Start from last stage and work backwards
    for (int i = stageCount - 1; i >= 0; i--) {
      Iterator<Move<Solution_>> currentIterator = stageIterators.get(i);

      // Check if this iterator has more elements
      if (currentIterator.hasNext()) {
        // Advance this iterator to next element
        currentMoves.set(i, currentIterator.next());
        return; // We're done - ready for next call to next()
      } else {
        // This iterator is exhausted, reset it and try to advance previous stage
        stageIterators.set(i, stageSelectors.get(i).iterator());
        // Get first element of this stage
        if (stageIterators.get(i).hasNext()) {
          currentMoves.set(i, stageIterators.get(i).next());
        }
        // Continue loop to try advancing previous stage
      }
    }

    // If we get here, all stages are exhausted
    hasNext = false;
  }
}
