package ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.invalid;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.TestdataEntityProvidingWithParameterSolution;

@PlanningEntity
public class TestdataInvalidCountEntityProvidingWithParameterEntity extends TestdataObject {

  public static EntityDescriptor<TestdataInvalidCountEntityProvidingWithParameterSolution>
      buildEntityDescriptor() {
    return TestdataInvalidCountEntityProvidingWithParameterSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataInvalidCountEntityProvidingWithParameterEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataInvalidCountEntityProvidingWithParameterSolution>
      buildVariableDescriptorForValueRange() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("valueRange");
  }

  private List<TestdataValue> valueRange;

  private TestdataValue value;

  public TestdataInvalidCountEntityProvidingWithParameterEntity() {
    // Required for cloning
  }

  public TestdataInvalidCountEntityProvidingWithParameterEntity(
      String code, List<TestdataValue> valueRange) {
    this(code, valueRange, null);
  }

  public TestdataInvalidCountEntityProvidingWithParameterEntity(
      String code, List<TestdataValue> valueRange, TestdataValue value) {
    super(code);
    this.valueRange = valueRange;
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValueRange(
      TestdataEntityProvidingWithParameterSolution solution1,
      TestdataEntityProvidingWithParameterSolution solution2) {
    return valueRange;
  }

  public void setValueRange(List<TestdataValue> valueRange) {
    this.valueRange = valueRange;
  }
}
