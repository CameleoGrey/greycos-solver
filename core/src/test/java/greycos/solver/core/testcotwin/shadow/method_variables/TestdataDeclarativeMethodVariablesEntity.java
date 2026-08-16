package greycos.solver.core.testcotwin.shadow.method_variables;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeMethodVariablesEntity extends TestdataObject {
  List<TestdataDeclarativeMethodVariablesBaseValue> values;

  public TestdataDeclarativeMethodVariablesEntity() {
    super();
    this.values = new ArrayList<>();
  }

  public TestdataDeclarativeMethodVariablesEntity(String code) {
    super(code);
    this.values = new ArrayList<>();
  }

  @PlanningListVariable
  public List<TestdataDeclarativeMethodVariablesBaseValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDeclarativeMethodVariablesBaseValue> values) {
    this.values = values;
  }
}
