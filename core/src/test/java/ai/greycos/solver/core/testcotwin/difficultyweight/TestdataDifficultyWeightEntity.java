package ai.greycos.solver.core.testcotwin.difficultyweight;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity(difficultyWeightFactoryClass = TestdataDifficultyFactory.class)
public class TestdataDifficultyWeightEntity extends TestdataObject {

  public static EntityDescriptor<TestdataDifficultyWeightSolution> buildEntityDescriptor() {
    return TestdataDifficultyWeightSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataDifficultyWeightEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataDifficultyWeightSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataDifficultyWeightValue value;

  public TestdataDifficultyWeightEntity(String code) {
    super(code);
  }

  public TestdataDifficultyWeightEntity(String code, TestdataDifficultyWeightValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataDifficultyWeightValue getValue() {
    return value;
  }

  public void setValue(TestdataDifficultyWeightValue value) {
    this.value = value;
  }
}
