package ai.greycos.solver.core.testcotwin.clone.deepcloning.field.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.cloner.DeepPlanningClone;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataInvalidEntityProvidingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataInvalidEntityProvidingSolution> buildEntityDescriptor() {
    return TestdataInvalidEntityProvidingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataInvalidEntityProvidingEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataInvalidEntityProvidingSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  @ValueRangeProvider(id = "valueRange")
  private List<TestdataValue> valueRange;

  @DeepPlanningClone private TestdataValue value;

  public TestdataInvalidEntityProvidingEntity() {}

  public TestdataInvalidEntityProvidingEntity(String code, List<TestdataValue> valueRange) {
    this(code, valueRange, null);
  }

  public TestdataInvalidEntityProvidingEntity(
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

  public List<TestdataValue> getValueRange() {
    return valueRange;
  }

  public void setValueRange(List<TestdataValue> valueRange) {
    this.valueRange = valueRange;
  }
}
