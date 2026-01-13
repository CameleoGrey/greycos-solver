package ai.greycos.solver.core.testcotwin.equals;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

@PlanningEntity
public class TestdataEqualsByCodeEntity extends TestdataEqualsByCodeObject {

  public static EntityDescriptor<TestdataEqualsByCodeSolution> buildEntityDescriptor() {
    return TestdataEqualsByCodeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEqualsByCodeEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataEqualsByCodeSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataEqualsByCodeValue value;

  public TestdataEqualsByCodeEntity(String code) {
    super(code);
  }

  public TestdataEqualsByCodeEntity(String code, TestdataEqualsByCodeValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataEqualsByCodeValue getValue() {
    return value;
  }

  public void setValue(TestdataEqualsByCodeValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************
  public void updateValue() {
    this.value = new TestdataEqualsByCodeValue(value.code + "/" + value.code);
  }
}
