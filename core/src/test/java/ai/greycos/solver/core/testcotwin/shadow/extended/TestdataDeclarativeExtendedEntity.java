package ai.greycos.solver.core.testcotwin.shadow.extended;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeExtendedEntity extends TestdataObject {
  @PlanningListVariable List<TestdataDeclarativeExtendedBaseValue> values;

  public TestdataDeclarativeExtendedEntity() {
    super();
    this.values = new ArrayList<>();
  }

  public TestdataDeclarativeExtendedEntity(String code) {
    super(code);
    this.values = new ArrayList<>();
  }

  public List<TestdataDeclarativeExtendedBaseValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataDeclarativeExtendedBaseValue> values) {
    this.values = values;
  }
}
