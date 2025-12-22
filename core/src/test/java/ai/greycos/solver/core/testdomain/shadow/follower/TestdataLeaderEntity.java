package ai.greycos.solver.core.testdomain.shadow.follower;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataLeaderEntity extends TestdataObject implements TestdataHasValue {
  @PlanningVariable TestdataValue value;

  public TestdataLeaderEntity() {}

  public TestdataLeaderEntity(String code) {
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
