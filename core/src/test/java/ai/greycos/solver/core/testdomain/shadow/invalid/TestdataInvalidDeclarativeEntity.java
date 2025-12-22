package ai.greycos.solver.core.testdomain.shadow.invalid;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningListVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataInvalidDeclarativeEntity extends TestdataObject {
  @PlanningListVariable List<TestdataInvalidDeclarativeValue> values;

  public TestdataInvalidDeclarativeEntity() {}

  public TestdataInvalidDeclarativeEntity(String code) {
    super(code);
  }
}
