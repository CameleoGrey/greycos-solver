package greycos.solver.core.testcotwin.record;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataRecordEntity extends TestdataObject {

  public static EntityDescriptor<TestdataRecordSolution> buildEntityDescriptor() {
    return TestdataRecordSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataRecordEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataRecordSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataRecordValue value;

  public TestdataRecordEntity() {}

  public TestdataRecordEntity(String code) {
    super(code);
  }

  public TestdataRecordEntity(String code, TestdataRecordValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataRecordValue getValue() {
    return value;
  }

  public void setValue(TestdataRecordValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
