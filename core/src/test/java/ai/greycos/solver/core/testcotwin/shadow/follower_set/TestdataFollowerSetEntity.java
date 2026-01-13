package ai.greycos.solver.core.testcotwin.shadow.follower_set;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.shadow.follower.TestdataHasValue;
import ai.greycos.solver.core.testcotwin.shadow.follower.TestdataLeaderEntity;

@PlanningEntity
public class TestdataFollowerSetEntity extends TestdataObject implements TestdataHasValue {
  List<TestdataLeaderEntity> leaders;

  @ShadowVariable(supplierName = "valueSupplier")
  TestdataValue value;

  public TestdataFollowerSetEntity() {}

  public TestdataFollowerSetEntity(String code, List<TestdataLeaderEntity> leaders) {
    super(code);
    this.leaders = leaders;
  }

  @Override
  public TestdataValue getValue() {
    return value;
  }

  @ShadowSources("leaders[].value")
  public TestdataValue valueSupplier() {
    var min = leaders.get(0).getValue();
    for (int i = 1; i < leaders.size(); i++) {
      var leader = leaders.get(i);
      var leaderValue = leader.getValue();
      if (min == null
          || (leaderValue != null && leaderValue.getCode().compareTo(min.getCode()) < 0)) {
        min = leaderValue;
      }
    }
    return min;
  }
}
