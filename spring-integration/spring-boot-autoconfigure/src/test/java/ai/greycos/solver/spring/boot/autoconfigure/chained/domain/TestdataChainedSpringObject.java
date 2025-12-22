package ai.greycos.solver.spring.boot.autoconfigure.chained.domain;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;

@PlanningEntity
public interface TestdataChainedSpringObject {

  @InverseRelationShadowVariable(sourceVariableName = "previous")
  TestdataChainedSpringEntity getNext();

  void setNext(TestdataChainedSpringEntity next);
}
