package ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataInvalidInverseRelationEntity {

  private TestdataInvalidInverseRelationValue value;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataInvalidInverseRelationValue getValue() {
    return value;
  }

  public void setValue(TestdataInvalidInverseRelationValue value) {
    this.value = value;
  }
}
