package ai.greycos.solver.core.testcotwin.unassignedvar.composite;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataAllowsUnassignedCompositeEntity extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedCompositeSolution>
      buildEntityDescriptor() {
    return TestdataAllowsUnassignedCompositeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedCompositeEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataAllowsUnassignedCompositeSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataAllowsUnassignedCompositeEntity() {
    // Required for cloning
  }

  public TestdataAllowsUnassignedCompositeEntity(String code) {
    super(code);
  }

  public TestdataAllowsUnassignedCompositeEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"valueRange1", "valueRange2"},
      allowsUnassigned = true)
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }
}
