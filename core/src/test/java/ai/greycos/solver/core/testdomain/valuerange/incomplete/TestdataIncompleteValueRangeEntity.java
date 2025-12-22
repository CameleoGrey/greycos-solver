package ai.greycos.solver.core.testdomain.valuerange.incomplete;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
