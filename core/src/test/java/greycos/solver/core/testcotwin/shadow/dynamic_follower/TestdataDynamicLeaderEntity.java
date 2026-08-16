package greycos.solver.core.testcotwin.shadow.dynamic_follower;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

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
