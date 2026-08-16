package greycos.solver.core.testcotwin.composite;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataCompositeEntity extends TestdataObject {

  public static EntityDescriptor<TestdataCompositeSolution> buildEntityDescriptor() {
    return TestdataCompositeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataCompositeEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataCompositeSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataCompositeEntity() {
    // Required for cloning
  }

  public TestdataCompositeEntity(String code) {
    super(code);
  }

  public TestdataCompositeEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = {"valueRange1", "valueRange2"})
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }
}
