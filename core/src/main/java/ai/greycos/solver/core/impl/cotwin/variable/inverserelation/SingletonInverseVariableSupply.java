package ai.greycos.solver.core.impl.cotwin.variable.inverserelation;

import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;

/**
 * Currently only supported for chained variables and {@link PlanningListVariable list variables},
 * which guarantee that no 2 entities use the same planningValue.
 */
public interface SingletonInverseVariableSupply extends Supply {

  /**
   * If entity1.varA = x then the inverse of x is entity1.
   *
   * @param planningValue never null
   * @return sometimes null, an entity for which the planning variable is the planningValue.
   */
  Object getInverseSingleton(Object planningValue);
}
