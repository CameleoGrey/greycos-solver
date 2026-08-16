package greycos.solver.core.testcotwin.shadow.counting;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.testcotwin.TestdataObject;

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
