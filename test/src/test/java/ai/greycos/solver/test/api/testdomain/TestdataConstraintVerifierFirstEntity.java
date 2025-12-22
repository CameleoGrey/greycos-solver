package ai.greycos.solver.test.api.testdomain;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
