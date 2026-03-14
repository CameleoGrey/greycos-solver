package ai.greycos.solver.core.testcotwin.shadow.invalid.parameter;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInvalidDeclarativeParameterEntity extends TestdataObject {
  @PlanningListVariable List<TestdataInvalidDeclarativeParameterValue> values;

  public TestdataInvalidDeclarativeParameterEntity() {}

  public TestdataInvalidDeclarativeParameterEntity(String code) {
    super(code);
  }

  public List<TestdataInvalidDeclarativeParameterValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataInvalidDeclarativeParameterValue> values) {
    this.values = values;
  }
}
