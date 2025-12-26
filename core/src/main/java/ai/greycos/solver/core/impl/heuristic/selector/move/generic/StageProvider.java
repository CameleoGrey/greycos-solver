package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.List;

import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;

import org.jspecify.annotations.NonNull;

/**
 * Provider that creates move selectors for each stage of a multistage move.
 *
 * <p>A multistage move consists of multiple stages, where each stage generates moves for a specific
 * purpose. The StageProvider defines what stages exist and how they are constructed.
 *
 * <p>Example stages for a VRP problem:
 *
 * <ul>
 *   <li>Stage 1: Select a sub-route (pillar selection)
 *   <li>Stage 2: Remove it (ruin)
 *   <li>Stage 3: Reinsert using heuristic (recreate)
 * </ul>
 *
 * <p>The order of stages matters: they are executed sequentially in the order returned by {@link
 * #createStages(HeuristicConfigPolicy)}. Each stage's move selector generates moves that contribute
 * to the overall multistage move.
 *
 * @param <Solution_> the solution type
 */
public interface StageProvider<Solution_> {

  /**
   * Creates the move selectors for each stage.
   *
   * <p>The order of the list matters: stages are executed sequentially. Each stage's move selector
   * generates moves that contribute to the overall multistage move.
   *
   * <p>This method is called once during factory construction, not per-solve. The returned
   * selectors are reused across multiple solving phases.
   *
   * @param configPolicy the configuration policy, never null
   * @return a list of move selectors, one for each stage, never null, never empty
   * @throws IllegalStateException if the stage list is empty
   */
  @NonNull List<MoveSelector<Solution_>> createStages(
      @NonNull HeuristicConfigPolicy<Solution_> configPolicy);

  /**
   * Returns the number of stages.
   *
   * <p>This must match the size of the list returned by {@link
   * #createStages(HeuristicConfigPolicy)}.
   *
   * @return the stage count, must be at least 1
   */
  int getStageCount();
}
