package ai.greycos.solver.core.api.cotwin.lookup;

import ai.greycos.solver.core.api.solver.change.ProblemChange;

import org.jspecify.annotations.Nullable;

/**
 * Allows to transfer an entity or fact instance (often from another {@link Thread}) to another
 * working solution.
 */
public interface Lookup {

  /**
   * Translates an entity or fact instance (often from another {@link Thread}) to another working
   * solution. Useful for move rebasing and in a {@link ProblemChange} and for multi-threaded
   * solving.
   *
   * <p>Matching uses {@link PlanningId}.
   *
   * @param problemFactOrPlanningEntity The fact or entity to rebase.
   * @return null if problemFactOrPlanningEntity is null
   * @throws IllegalArgumentException if there is no working object for the fact or entity, if it
   *     cannot be looked up, or if its class is not supported.
   * @throws IllegalStateException if it cannot be looked up
   * @param <T> the object type
   */
  <T> @Nullable T lookUpWorkingObject(@Nullable T problemFactOrPlanningEntity);
}
