package ai.greycos.solver.core.testcotwin.shadow.counting;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataCountingEntity extends TestdataObject {
  @PlanningListVariable List<TestdataCountingValue> values;

  public TestdataCountingEntity() {
    values = new ArrayList<>();
  }

  public TestdataCountingEntity(String code) {
    super(code);
    values = new ArrayList<>();
  }

  public List<TestdataCountingValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataCountingValue> values) {
    this.values = values;
  }
}
