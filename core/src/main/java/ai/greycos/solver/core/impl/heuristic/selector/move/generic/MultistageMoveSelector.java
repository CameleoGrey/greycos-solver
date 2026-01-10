package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A move selector that generates multistage moves.
 *
 * <p>A multistage move consists of multiple coordinated changes that are executed atomically. This
 * selector manages the stage transitions and composes the final composite move.
 *
 * <p>Example: Ruin and Recreate
 *
 * <pre>
 * Stage 1: Select subset of entities
 * Stage 2: Unassign them (ruin)
 * Stage 3: Reassign using heuristic (recreate)
 * </pre>
 *
 * <p>The selector supports both sequential (deterministic) and random move generation. Sequential
 * mode iterates through all combinations (Cartesian product), while random mode generates random
 * combinations.
 *
 * <p>Time complexity:
 *
 * <ul>
 *   <li>Size calculation: O(stageCount)
 *   <li>Sequential iteration: O(Π stageSizes) total
 *   <li>Random iteration: O(stageCount) per move
 * </ul>
 *
 * <p>Space complexity: O(stageCount) for storing stage selectors
 *
 * @param <Solution_> solution type
 */
public class MultistageMoveSelector<Solution_> extends GenericMoveSelector<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(MultistageMoveSelector.class);

  private final StageProvider<Solution_> stageProvider;
  private final List<MoveSelector<Solution_>> stageSelectors;
  private final boolean randomSelection;

  public MultistageMoveSelector(
      @NonNull StageProvider<Solution_> stageProvider,
      @NonNull List<MoveSelector<Solution_>> stageSelectors,
      boolean randomSelection) {
    this.stageProvider = Objects.requireNonNull(stageProvider);
    this.stageSelectors = List.copyOf(Objects.requireNonNull(stageSelectors));
    this.randomSelection = randomSelection;

    if (stageSelectors.isEmpty()) {
      throw new IllegalArgumentException("Stage selectors list cannot be empty");
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Created MultistageMoveSelector with {} stages ({} selection)",
          stageSelectors.size(),
          randomSelection ? "random" : "sequential");
    }

    for (MoveSelector<Solution_> stageSelector : stageSelectors) {
      phaseLifecycleSupport.addEventListener(stageSelector);
    }
  }

  @Override
  public boolean supportsPhaseAndSolverCaching() {
    return false;
  }

  @Override
  public void stepStarted(@NonNull AbstractStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);

    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Step started for multistage move selector");
    }
  }

  @Override
  public void stepEnded(@NonNull AbstractStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
  }

  @Override
  public boolean isCountable() {
    return stageSelectors.stream().allMatch(MoveSelector::isCountable);
  }

  @Override
  public long getSize() {
    long size = 1L;
    for (int i = 0; i < stageSelectors.size(); i++) {
      MoveSelector<Solution_> stageSelector = stageSelectors.get(i);
      long stageSize = stageSelector.getSize();
      if (stageSize == 0) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Stage {} has size 0, returning total size 0", i);
        }
        return 0L;
      }
      size *= stageSize;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Multistage move selector size: {}", size);
    }

    return size;
  }

  @Override
  public boolean isNeverEnding() {
    return stageSelectors.stream().anyMatch(MoveSelector::isNeverEnding);
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Creating {} multistage move iterator", randomSelection ? "random" : "sequential");
    }

    if (randomSelection) {
      return new RandomMultistageMoveIterator<>(stageSelectors, workingRandom);
    } else {
      return new SequentialMultistageMoveIterator<>(stageSelectors);
    }
  }

  StageProvider<Solution_> getStageProvider() {
    return stageProvider;
  }

  List<MoveSelector<Solution_>> getStageSelectors() {
    return stageSelectors;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "("
        + stageSelectors.size()
        + " stages, "
        + (randomSelection ? "random" : "sequential")
        + ")";
  }
}
