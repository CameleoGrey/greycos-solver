package ai.greycos.solver.test.api.testcotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public final class TestdataConstraintVerifierSecondEntity {
  @PlanningId private String planningId;

  @PlanningVariable(valueRangeProviderRefs = "stringValueRange")
  private String value;

  public TestdataConstraintVerifierSecondEntity(String planningId, String value) {
    this.planningId = planningId;
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
