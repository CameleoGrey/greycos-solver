package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.List;

import ai.greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.StageProvider;

import org.jspecify.annotations.NonNull;

/**
 * Multistage move selector for list planning variables.
 *
 * <p>Specialized for list-based problems (e.g., vehicle routing, task scheduling with sequences).
 * Extends the basic {@link
 * ai.greycos.solver.core.impl.heuristic.selector.move.generic.MultistageMoveSelector} to provide
 * list-specific optimizations and type safety.
 *
 * <p>Example use cases:
 *
 * <ul>
 *   <li>VRP: k-opt moves that remove and reconnect route segments
 *   <li>Sequencing: Ruin and recreate for sub-chains
 *   <li>Scheduling: Multi-stage time slot adjustments
 * </ul>
 *
 * <p>The behavior is identical to the basic multistage move selector, but this class exists for:
 *
 * <ul>
 *   <li>Type safety when working with list variables
 *   <li>Future list-specific optimizations
 *   <li>Clear separation of concerns in codebase
 * </ul>
 *
 * @param <Solution_> solution type
 */
public class ListMultistageMoveSelector<Solution_>
    extends ai.greycos.solver.core.impl.heuristic.selector.move.generic.MultistageMoveSelector<
        Solution_> {

  /**
   * Constructs a list multistage move selector.
   *
   * @param stageProvider provider that created stage selectors, never null
   * @param stageSelectors list of move selectors, one per stage, never null, never empty
   * @param randomSelection true for random selection, false for sequential
   */
  public ListMultistageMoveSelector(
      @NonNull StageProvider<Solution_> stageProvider,
      @NonNull List<MoveSelector<Solution_>> stageSelectors,
      boolean randomSelection) {
    super(stageProvider, stageSelectors, randomSelection);
  }

  @Override
  public String toString() {
    return "List" + super.toString();
  }
}
