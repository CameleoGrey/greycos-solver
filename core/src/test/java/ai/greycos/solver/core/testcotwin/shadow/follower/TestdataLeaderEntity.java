package ai.greycos.solver.core.testcotwin.shadow.follower;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

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
