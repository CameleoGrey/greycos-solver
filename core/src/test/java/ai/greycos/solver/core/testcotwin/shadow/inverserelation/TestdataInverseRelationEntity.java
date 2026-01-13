package ai.greycos.solver.core.testcotwin.shadow.inverserelation;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInverseRelationEntity extends TestdataObject {

  public static EntityDescriptor<TestdataInverseRelationSolution> buildEntityDescriptor() {
    return TestdataInverseRelationSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataInverseRelationEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataInverseRelationSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataInverseRelationValue value;

  public TestdataInverseRelationEntity() {}

  public TestdataInverseRelationEntity(String code) {
    super(code);
  }

  public TestdataInverseRelationEntity(String code, TestdataInverseRelationValue value) {
    this(code);
    this.value = value;
    if (value != null) {
      value.getEntities().add(this);
    }
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange", allowsUnassigned = true)
  public TestdataInverseRelationValue getValue() {
    return value;
  }

  public void setValue(TestdataInverseRelationValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
