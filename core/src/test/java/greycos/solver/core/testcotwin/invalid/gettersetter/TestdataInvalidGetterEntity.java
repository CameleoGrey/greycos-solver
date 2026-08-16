package greycos.solver.core.testcotwin.invalid.gettersetter;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataInvalidGetterEntity {

  @PlanningVariable private TestdataValue valueWithoutSetter;

  public TestdataValue getValueWithoutSetter() {
    return valueWithoutSetter;
  }
}
