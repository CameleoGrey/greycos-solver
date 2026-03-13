package ai.greycos.solver.core.impl.heuristic.move;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;

import org.jspecify.annotations.NullMarked;

/**
 * Selector-based no-change move name used by the upstream refactor.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class SelectorBasedNoChangeMove<Solution_> extends NoChangeMove<Solution_> {

  public static final SelectorBasedNoChangeMove<?> INSTANCE = new SelectorBasedNoChangeMove<>();

  @SuppressWarnings("unchecked")
  public static <Solution_> SelectorBasedNoChangeMove<Solution_> getInstance() {
    return (SelectorBasedNoChangeMove<Solution_>) INSTANCE;
  }

  private SelectorBasedNoChangeMove() {}
}
