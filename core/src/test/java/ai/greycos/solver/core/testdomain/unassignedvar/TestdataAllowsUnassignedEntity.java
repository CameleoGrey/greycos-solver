package ai.greycos.solver.core.testdomain.unassignedvar;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataAllowsUnassignedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedSolution> buildEntityDescriptor() {
    return TestdataAllowsUnassignedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataAllowsUnassignedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataAllowsUnassignedEntity() {}

  public TestdataAllowsUnassignedEntity(String code) {
    super(code);
  }

  public TestdataAllowsUnassignedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange", allowsUnassigned = true)
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
