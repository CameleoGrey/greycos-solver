package ai.greycos.solver.core.testcotwin.reflect.field;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataFieldAnnotatedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataFieldAnnotatedSolution> buildEntityDescriptor() {
    return TestdataFieldAnnotatedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataFieldAnnotatedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataFieldAnnotatedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private TestdataValue value;

  public TestdataFieldAnnotatedEntity() {}

  public TestdataFieldAnnotatedEntity(String code) {
    super(code);
  }

  public TestdataFieldAnnotatedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  public TestdataValue getValue() {
    return value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
