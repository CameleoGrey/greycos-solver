package greycos.solver.core.testcotwin.shadow.mixed;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMixedEntity extends TestdataObject {
  @PlanningListVariable List<TestdataMixedValue> valueList;

  public TestdataMixedEntity() {
    valueList = new ArrayList<>();
  }

  public TestdataMixedEntity(String code) {
    super(code);
    valueList = new ArrayList<>();
  }

  public List<TestdataMixedValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataMixedValue> valueList) {
    this.valueList = valueList;
  }
}
