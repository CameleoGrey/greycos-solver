package ai.greycos.solver.core.api.cotwin.lookup;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

/**
 * Determines how {@link ScoreDirector#lookUpWorkingObject(Object)} maps a {@link
 * ProblemFactCollectionProperty problem fact} or a {@link PlanningEntity planning entity} from an
 * external copy to the internal one.
 *
 * @deprecated When multi-threaded solving, ensure your cotwin classes use @{@link PlanningId}
 *     instead.
 */
@Deprecated(forRemoval = true, since = "1.10.0")
public enum LookUpStrategyType {
  /**
   * Map by the same {@link PlanningId} field or method. If there is no such field or method, there
   * is no mapping and {@link ScoreDirector#lookUpWorkingObject(Object)} must not be used. If there
   * is such a field or method, but it returns null, it fails fast.
   *
   * <p>This is the default.
   */
  PLANNING_ID_OR_NONE,
  /**
   * Map by the same {@link PlanningId} field or method. If there is no such field or method, it
   * fails fast.
   */
  PLANNING_ID_OR_FAIL_FAST,
  /**
   * Map by {@link Object#equals(Object) equals(Object)} and {@link Object#hashCode() hashCode()}.
   * If any of these two methods is not overridden by the working object's class or some of its
   * superclasses, {@link ScoreDirector#lookUpWorkingObject(Object)} must not be used because it
   * cannot work correctly with {@link Object}'s equals and hashCode implementations.
   */
  EQUALITY,
  /** There is no mapping and {@link ScoreDirector#lookUpWorkingObject(Object)} must not be used. */
  NONE;
}
