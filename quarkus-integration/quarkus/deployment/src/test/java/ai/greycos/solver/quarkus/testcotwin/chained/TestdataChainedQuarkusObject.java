package ai.greycos.solver.quarkus.testcotwin.chained;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataChainedQuarkusObject {

  @InverseRelationShadowVariable(sourceVariableName = "previous")
  TestdataChainedQuarkusEntity getNext();

  void setNext(TestdataChainedQuarkusEntity next);
}
