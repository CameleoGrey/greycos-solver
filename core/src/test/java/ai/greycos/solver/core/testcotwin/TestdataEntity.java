package ai.greycos.solver.core.testcotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

@PlanningEntity
public class TestdataEntity extends TestdataObject {

  public static final String VALUE_FIELD = "value";

  public static EntityDescriptor<TestdataSolution> buildEntityDescriptor() {
    return TestdataSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataSolution> buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataEntity() {}

  public TestdataEntity(String code) {
    super(code);
  }

  public TestdataEntity(String code, TestdataValue value) {
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
  public void updateValue() {
    this.value = new TestdataValue(value.code + "/" + value.code);
  }
}
