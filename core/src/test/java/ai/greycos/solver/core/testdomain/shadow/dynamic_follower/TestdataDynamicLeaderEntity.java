package ai.greycos.solver.core.testdomain.shadow.dynamic_follower;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataDynamicLeaderEntity extends TestdataObject implements TestdataDynamicHasValue {
  @PlanningVariable TestdataValue value;

  public TestdataDynamicLeaderEntity() {}

  public TestdataDynamicLeaderEntity(String code) {
    super(code);
  }

  @Override
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }
}
