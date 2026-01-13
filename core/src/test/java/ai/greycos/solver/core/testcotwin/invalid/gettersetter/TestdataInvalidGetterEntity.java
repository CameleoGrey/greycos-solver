package ai.greycos.solver.core.testcotwin.invalid.gettersetter;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataInvalidGetterEntity {

  @PlanningVariable private TestdataValue valueWithoutSetter;

  public TestdataValue getValueWithoutSetter() {
    return valueWithoutSetter;
  }
}
