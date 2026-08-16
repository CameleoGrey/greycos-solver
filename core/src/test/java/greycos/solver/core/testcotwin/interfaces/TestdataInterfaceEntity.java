package greycos.solver.core.testcotwin.interfaces;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataInterfaceEntity {
  @PlanningId
  String getId();

  @PlanningVariable
  TestdataInterfaceValue getValue();

  void setValue(TestdataInterfaceValue value);
}
