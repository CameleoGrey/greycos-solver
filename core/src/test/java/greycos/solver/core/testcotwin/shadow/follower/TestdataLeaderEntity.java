package greycos.solver.core.testcotwin.shadow.follower;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

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
