package ai.greycos.solver.core.testdomain.chained.shadow;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataShadowingChainedObject {

  /**
   * @return sometimes null
   */
  @InverseRelationShadowVariable(sourceVariableName = "chainedObject")
  TestdataShadowingChainedEntity getNextEntity();

  void setNextEntity(TestdataShadowingChainedEntity nextEntity);
}
