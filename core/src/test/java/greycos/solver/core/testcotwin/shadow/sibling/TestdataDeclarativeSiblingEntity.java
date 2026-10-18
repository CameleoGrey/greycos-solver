package greycos.solver.core.testcotwin.shadow.sibling;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeSiblingEntity extends TestdataObject {
  @PlanningListVariable List<AbstractTestdataDeclarativeSiblingValue> values;

  public TestdataDeclarativeSiblingEntity() {
    super();
    this.values = new ArrayList<>();
  }

  public TestdataDeclarativeSiblingEntity(String code) {
    super(code);
    this.values = new ArrayList<>();
  }

  public List<AbstractTestdataDeclarativeSiblingValue> getValues() {
    return values;
  }

  public void setValues(List<AbstractTestdataDeclarativeSiblingValue> values) {
    this.values = values;
  }
}
