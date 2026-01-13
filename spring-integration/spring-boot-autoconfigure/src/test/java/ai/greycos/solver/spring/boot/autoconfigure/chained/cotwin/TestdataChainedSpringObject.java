package ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataChainedSpringObject {

  @InverseRelationShadowVariable(sourceVariableName = "previous")
  TestdataChainedSpringEntity getNext();

  void setNext(TestdataChainedSpringEntity next);
}
