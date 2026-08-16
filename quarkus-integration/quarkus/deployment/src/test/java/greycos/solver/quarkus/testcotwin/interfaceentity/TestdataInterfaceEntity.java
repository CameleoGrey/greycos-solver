package greycos.solver.quarkus.testcotwin.interfaceentity;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataInterfaceEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  Integer getValue();

  void setValue(Integer value);
}
