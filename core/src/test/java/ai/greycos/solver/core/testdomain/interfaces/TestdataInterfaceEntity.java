package ai.greycos.solver.core.testdomain.interfaces;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public interface TestdataInterfaceEntity {
  @PlanningId
  String getId();

  @PlanningVariable
  TestdataInterfaceValue getValue();

  void setValue(TestdataInterfaceValue value);
}
