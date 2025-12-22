package ai.greycos.solver.quarkus.testdomain.chained;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataChainedQuarkusObject {

  @InverseRelationShadowVariable(sourceVariableName = "previous")
  TestdataChainedQuarkusEntity getNext();

  void setNext(TestdataChainedQuarkusEntity next);
}
