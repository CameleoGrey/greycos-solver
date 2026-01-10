package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterator for multistage moves in random order.
 *
 * <p>Generates random combinations of stage moves. Each iteration returns a composite move that
 * combines one randomly selected move from each stage.
 *
 * <p>This iterator is never-ending as long as at least one stage is never-ending (e.g., uses random
 * selection).
 *
 * <p>Time complexity: O(stageCount) per move (with caching for small finite selectors) Space
 * complexity: O(stageCount * cachedMoves) for cached moves
 *
 * @param <Solution_> solution type
 */
public class RandomMultistageMoveIterator<Solution_> implements Iterator<Move<Solution_>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RandomMultistageMoveIterator.class);

  private final List<MoveSelector<Solution_>> stageSelectors;
  private final Random random;

  private static final int CACHE_THRESHOLD = 1000;

  private final List<List<Move<Solution_>>> cachedMoves;

  public RandomMultistageMoveIterator(
      @NonNull List<MoveSelector<Solution_>> stageSelectors, @NonNull Random random) {
    this.stageSelectors = List.copyOf(stageSelectors);
    this.random = random;
    this.cachedMoves = new ArrayList<>(stageSelectors.size());

    for (int i = 0; i < stageSelectors.size(); i++) {
      cachedMoves.add(null);
    }
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @Override
  public Move<Solution_> next() {
    List<Move<Solution_>> moves = new ArrayList<>(stageSelectors.size());

    for (int i = 0; i < stageSelectors.size(); i++) {
      MoveSelector<Solution_> selector = stageSelectors.get(i);
      Move<Solution_> move = getRandomMove(selector, i);
      moves.add(move);
    }

    return CompositeMove.buildMove(moves);
  }

  private Move<Solution_> getRandomMove(MoveSelector<Solution_> selector, int stageIndex) {
    if (selector.isNeverEnding()) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Stage {} is never-ending, taking first move", stageIndex);
      }

      Iterator<Move<Solution_>> iterator = selector.iterator();
      if (!iterator.hasNext()) {
        throw new IllegalStateException("Stage selector " + selector + " has no moves");
      }
      return iterator.next();
    }

    long size = selector.getSize();

    if (size == 0) {
      throw new IllegalStateException("Stage selector " + selector + " has no moves");
    }

    if (size <= CACHE_THRESHOLD) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Stage {} size {} <= cache threshold {}, using cache",
            stageIndex,
            size,
            CACHE_THRESHOLD);
      }
      return getRandomMoveFromCache(selector, stageIndex, (int) size);
    } else {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Stage {} size {} > cache threshold {}, using skip", stageIndex, size, CACHE_THRESHOLD);
      }
      return getRandomMoveBySkipping(selector);
    }
  }

  private Move<Solution_> getRandomMoveFromCache(
      MoveSelector<Solution_> selector, int stageIndex, int size) {
    List<Move<Solution_>> cache = cachedMoves.get(stageIndex);

    if (cache == null) {
      cache = new ArrayList<>(size);
      Iterator<Move<Solution_>> iterator = selector.iterator();
      while (iterator.hasNext()) {
        cache.add(iterator.next());
      }
      cachedMoves.set(stageIndex, cache);
    }

    if (cache.isEmpty()) {
      throw new IllegalStateException("Stage selector " + selector + " has no moves");
    }

    int randomIndex = random.nextInt(cache.size());
    return cache.get(randomIndex);
  }

  private Move<Solution_> getRandomMoveBySkipping(MoveSelector<Solution_> selector) {
    Iterator<Move<Solution_>> iterator = selector.iterator();

    if (!iterator.hasNext()) {
      throw new IllegalStateException("Stage selector " + selector + " has no moves");
    }

    long size = selector.getSize();
    long skip = (long) (random.nextDouble() * size);

    for (long i = 0; i < skip && iterator.hasNext(); i++) {
      iterator.next();
    }

    if (iterator.hasNext()) {
      return iterator.next();
    } else {
      iterator = selector.iterator();
      if (iterator.hasNext()) {
        return iterator.next();
      } else {
        throw new IllegalStateException("Stage selector " + selector + " has no moves");
      }
    }
  }
}
