package ai.greycos.solver.core.testcotwin.shadow.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInvalidDeclarativeEntity extends TestdataObject {
  @PlanningListVariable List<TestdataInvalidDeclarativeValue> values;

  public TestdataInvalidDeclarativeEntity() {}

  public TestdataInvalidDeclarativeEntity(String code) {
    super(code);
  }
}
