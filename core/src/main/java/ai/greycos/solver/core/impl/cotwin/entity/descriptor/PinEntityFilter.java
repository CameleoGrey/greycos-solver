package ai.greycos.solver.core.impl.cotwin.entity.descriptor;

import ai.greycos.solver.core.api.cotwin.entity.PlanningPin;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;

/**
 * Filters out entities that return true for the {@link PlanningPin} annotated boolean member.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
record PinEntityFilter<Solution_>(MemberAccessor memberAccessor)
    implements MovableFilter<Solution_> {

  @Override
  public boolean test(Solution_ solution, Object entity) {
    var pinned = (Boolean) memberAccessor.executeGetter(entity);
    if (pinned == null) {
      throw new IllegalStateException(
          "The entity ("
              + entity
              + ") has a @"
              + PlanningPin.class.getSimpleName()
              + " annotated property ("
              + memberAccessor.getName()
              + ") that returns null.");
    }
    return !pinned;
  }

  @Override
  public String toString() {
    return "Non-pinned entities only";
  }
}
