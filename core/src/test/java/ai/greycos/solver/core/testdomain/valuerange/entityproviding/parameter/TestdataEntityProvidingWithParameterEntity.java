package ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataEntityProvidingWithParameterEntity extends TestdataObject {

  public static EntityDescriptor<TestdataEntityProvidingWithParameterSolution>
      buildEntityDescriptor() {
    return TestdataEntityProvidingWithParameterSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEntityProvidingWithParameterEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataEntityProvidingWithParameterSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private List<TestdataValue> valueRange;

  private TestdataValue value;

  public TestdataEntityProvidingWithParameterEntity() {
    // Required for cloning
  }

  public TestdataEntityProvidingWithParameterEntity(String code, List<TestdataValue> valueRange) {
    this(code, valueRange, null);
  }

  public TestdataEntityProvidingWithParameterEntity(
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
  public List<TestdataValue> getValueRange(TestdataEntityProvidingWithParameterSolution solution) {
    return valueRange;
  }

  public void setValueRange(List<TestdataValue> valueRange) {
    this.valueRange = valueRange;
  }
}
