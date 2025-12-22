package ai.greycos.solver.core.testdomain.comparable;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity(difficultyComparatorClass = TestdataCodeComparator.class)
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
      strengthComparatorClass = TestdataCodeComparator.class)
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
