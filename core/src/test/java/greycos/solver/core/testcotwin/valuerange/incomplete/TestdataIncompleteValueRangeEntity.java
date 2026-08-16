package greycos.solver.core.testcotwin.valuerange.incomplete;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataIncompleteValueRangeEntity extends TestdataObject {

  public static EntityDescriptor<TestdataIncompleteValueRangeSolution> buildEntityDescriptor() {
    return TestdataIncompleteValueRangeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataIncompleteValueRangeEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataIncompleteValueRangeSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataIncompleteValueRangeEntity() {}

  public TestdataIncompleteValueRangeEntity(String code) {
    super(code);
  }

  public TestdataIncompleteValueRangeEntity(String code, TestdataValue value) {
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
}
