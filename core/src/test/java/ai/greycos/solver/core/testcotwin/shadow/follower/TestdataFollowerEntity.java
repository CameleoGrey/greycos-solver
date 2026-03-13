package ai.greycos.solver.core.testcotwin.shadow.follower;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataFollowerEntity extends TestdataObject implements TestdataHasValue {
  TestdataLeaderEntity leader;

  @ShadowVariable(supplierName = "valueSupplier")
  TestdataValue value;

  public TestdataFollowerEntity() {}

  public TestdataFollowerEntity(String code, TestdataLeaderEntity leader) {
    super(code);
    this.leader = leader;
  }

  @Override
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ShadowSources(value = "leader.value", alignmentKey = "leader")
  public TestdataValue valueSupplier() {
    return leader.value;
  }

  public TestdataLeaderEntity getLeader() {
    return leader;
  }

  public void setLeader(TestdataLeaderEntity leader) {
    this.leader = leader;
  }
}
