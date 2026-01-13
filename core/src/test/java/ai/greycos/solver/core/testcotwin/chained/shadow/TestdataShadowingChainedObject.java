package ai.greycos.solver.core.testcotwin.chained.shadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataShadowingChainedObject {

  /**
   * @return sometimes null
   */
  @InverseRelationShadowVariable(sourceVariableName = "chainedObject")
  TestdataShadowingChainedEntity getNextEntity();

  void setNextEntity(TestdataShadowingChainedEntity nextEntity);
}
