package ai.greycos.solver.core.testcotwin.interfaces;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public interface TestdataInterfaceEntity {
  @PlanningId
  String getId();

  @PlanningVariable
  TestdataInterfaceValue getValue();

  void setValue(TestdataInterfaceValue value);
}
