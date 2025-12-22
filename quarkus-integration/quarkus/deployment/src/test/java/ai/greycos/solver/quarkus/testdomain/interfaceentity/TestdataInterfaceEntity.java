package ai.greycos.solver.quarkus.testdomain.interfaceentity;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public interface TestdataInterfaceEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  Integer getValue();

  void setValue(Integer value);
}
