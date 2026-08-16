package greycos.solver.core.testcotwin.constraintverifier;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public final class TestdataConstraintVerifierFirstEntity extends TestdataObject {

  private TestdataValue value;

  public TestdataConstraintVerifierFirstEntity(String code) {
    super(code);
  }

  public TestdataConstraintVerifierFirstEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @Override
  public String toString() {
    return "TestdataConstraintVerifierFirstEntity(" + "code='" + code + '\'' + ')';
  }
}
