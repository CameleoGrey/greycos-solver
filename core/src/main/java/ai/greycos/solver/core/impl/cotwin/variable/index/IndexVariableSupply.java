package ai.greycos.solver.core.impl.cotwin.variable.index;

import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;

/** Only supported for {@link PlanningListVariable list variables}. */
public interface IndexVariableSupply extends Supply {

  /**
   * Get {@code planningValue}'s index in the {@link PlanningListVariable list variable} it is an
   * element of.
   *
   * @param planningValue never null
   * @return {@code planningValue}'s index in the list variable it is an element of or {@code null}
   *     if the value is unassigned
   */
  Integer getIndex(Object planningValue);
}
