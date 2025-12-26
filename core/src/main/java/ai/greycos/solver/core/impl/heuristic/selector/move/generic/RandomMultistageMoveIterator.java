package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import ai.greycos.solver.core.impl.heuristic.move.CompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.NonNull;

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

  /**
   * Threshold for caching moves from finite selectors.
   *
   * <p>Selectors with size <= CACHE_THRESHOLD will have their moves cached to avoid O(n) skip
   * operations. This balances memory usage against performance.
   */
  private static final int CACHE_THRESHOLD = 1000;

  /**
   * Cached moves for finite selectors, indexed by stage selector.
   *
   * <p>Each entry is either null (not cached), an empty list (no moves), or a list of moves.
   */
  private final List<List<Move<Solution_>>> cachedMoves;

  /**
   * Constructs iterator for multistage moves with random selection.
   *
   * @param stageSelectors list of move selectors, one per stage, never null, never empty
   * @param random random number generator for reproducibility
   */
  public RandomMultistageMoveIterator(
      @NonNull List<MoveSelector<Solution_>> stageSelectors, @NonNull Random random) {
    this.stageSelectors = List.copyOf(stageSelectors);
    this.random = random;
    this.cachedMoves = new ArrayList<>(stageSelectors.size());

    // Initialize cache entries for all selectors
    for (int i = 0; i < stageSelectors.size(); i++) {
      cachedMoves.add(null);
    }
  }

  @Override
  public boolean hasNext() {
    // Random iterator is never ending
    return true;
  }

  @Override
  public Move<Solution_> next() {
    List<Move<Solution_>> moves = new ArrayList<>(stageSelectors.size());

    // Get one random move from each stage
    for (int i = 0; i < stageSelectors.size(); i++) {
      MoveSelector<Solution_> selector = stageSelectors.get(i);
      Move<Solution_> move = getRandomMove(selector, i);
      moves.add(move);
    }

    return CompositeMove.buildMove(moves);
  }

  /**
   * Gets a random move from the given selector.
   *
   * <p>If selector is never-ending (random), we just take the first move. For finite selectors:
   *
   * <ul>
   *   <li>If size <= CACHE_THRESHOLD: cache moves and select randomly (O(1) after cache)</li>
   *   <li>If size > CACHE_THRESHOLD: skip to random position (O(n), but n is large so memory
   *       would be worse)</li>
   * </ul>
   *
   * @param selector move selector to get random move from
   * @param stageIndex index of the stage (used for caching)
   * @return a randomly selected move
   * @throws IllegalStateException if selector has no moves
   */
  private Move<Solution_> getRandomMove(MoveSelector<Solution_> selector, int stageIndex) {
    // For never-ending selectors, just take first move (it's already random)
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

    // For finite selectors, check cache or use skip strategy
    long size = selector.getSize();

    if (size == 0) {
      throw new IllegalStateException("Stage selector " + selector + " has no moves");
    }

    // Use caching for small selectors to avoid O(n) skip operations
    if (size <= CACHE_THRESHOLD) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Stage {} size {} <= cache threshold {}, using cache",
            stageIndex, size, CACHE_THRESHOLD);
      }
      return getRandomMoveFromCache(selector, stageIndex, (int) size);
    } else {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Stage {} size {} > cache threshold {}, using skip",
            stageIndex, size, CACHE_THRESHOLD);
      }
      // For large selectors, use skip strategy (trade memory for time)
      return getRandomMoveBySkipping(selector);
    }
  }

  /**
   * Gets a random move from a cached list of moves.
   *
   * <p>This method lazily populates the cache on first access, then uses O(1) random selection.
   *
   * @param selector move selector to get random move from
   * @param stageIndex index of the stage (used for caching)
   * @param size the size of the selector (must be <= CACHE_THRESHOLD)
   * @return a randomly selected move
   */
  private Move<Solution_> getRandomMoveFromCache(
      MoveSelector<Solution_> selector, int stageIndex, int size) {
    List<Move<Solution_>> cache = cachedMoves.get(stageIndex);

    // Lazy initialization of cache
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

    // O(1) random selection from cache
    int randomIndex = random.nextInt(cache.size());
    return cache.get(randomIndex);
  }

  /**
   * Gets a random move by skipping to a random position in the iterator.
   *
   * <p>This method is used for large selectors where caching would consume too much memory.
   *
   * @param selector move selector to get random move from
   * @return a randomly selected move
   */
  private Move<Solution_> getRandomMoveBySkipping(MoveSelector<Solution_> selector) {
    Iterator<Move<Solution_>> iterator = selector.iterator();

    if (!iterator.hasNext()) {
      throw new IllegalStateException("Stage selector " + selector + " has no moves");
    }

    long size = selector.getSize();
    long skip = (long) (random.nextDouble() * size);

    // Skip to random position (O(n) but acceptable for large n where caching would be worse)
    for (long i = 0; i < skip && iterator.hasNext(); i++) {
      iterator.next();
    }

    if (iterator.hasNext()) {
      return iterator.next();
    } else {
      // Fallback: take the first available move
      iterator = selector.iterator();
      if (iterator.hasNext()) {
        return iterator.next();
      } else {
        throw new IllegalStateException("Stage selector " + selector + " has no moves");
      }
    }
  }
}
