package ai.greycos.solver.core.testdomain.invalid.gettersetter;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataInvalidGetterEntity {

  @PlanningVariable private TestdataValue valueWithoutSetter;

  public TestdataValue getValueWithoutSetter() {
    return valueWithoutSetter;
  }
}
