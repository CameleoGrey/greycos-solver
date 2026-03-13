package ai.greycos.solver.core.testcotwin.comparable;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity(comparatorClass = TestdataCodeComparator.class)
public class TestdataDifficultyComparingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataDifficultyComparingSolution> buildEntityDescriptor() {
    return TestdataDifficultyComparingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataDifficultyComparingEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataDifficultyComparingSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;

  public TestdataDifficultyComparingEntity() {}

  public TestdataDifficultyComparingEntity(String code) {
    super(code);
  }

  public TestdataDifficultyComparingEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"valueRange"},
      comparatorClass = TestdataCodeComparator.class)
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
