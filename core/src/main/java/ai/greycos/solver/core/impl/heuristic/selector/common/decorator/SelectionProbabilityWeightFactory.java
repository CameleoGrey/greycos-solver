package ai.greycos.solver.core.impl.heuristic.selector.common.decorator;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.Selector;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

/**
 * Create a probabilityWeight for a selection (which is a {@link PlanningEntity}, a planningValue, a
 * {@link Move} or a {@link Selector}). A probabilityWeight represents the random chance that a
 * selection will be selected. Some use cases benefit from focusing moves more actively on specific
 * selections.
 *
 * <p>Implementations are expected to be stateless. The solver may choose to reuse instances.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <T> the selection type
 */
@FunctionalInterface
public interface SelectionProbabilityWeightFactory<Solution_, T> {

  /**
   * @param scoreDirector never null, the {@link ScoreDirector} which has the {@link
   *     ScoreDirector#getWorkingSolution()} to which the selection belongs or applies to
   * @param selection never null, a {@link PlanningEntity}, a planningValue, a {@link Move} or a
   *     {@link Selector} to create the probabilityWeight for
   * @return {@code 0.0 <= returnValue <} {@link Double#POSITIVE_INFINITY}
   */
  double createProbabilityWeight(ScoreDirector<Solution_> scoreDirector, T selection);
}
