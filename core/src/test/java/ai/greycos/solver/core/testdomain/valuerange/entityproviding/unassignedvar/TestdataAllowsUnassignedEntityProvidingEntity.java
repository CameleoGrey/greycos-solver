package ai.greycos.solver.core.testdomain.valuerange.entityproviding.unassignedvar;

import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

@PlanningEntity
public class TestdataAllowsUnassignedEntityProvidingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedEntityProvidingSolution>
      buildEntityDescriptor() {
    return TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedEntityProvidingEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataAllowsUnassignedEntityProvidingSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private List<TestdataValue> valueRange;

  private TestdataValue value;

  public TestdataAllowsUnassignedEntityProvidingEntity() {
    // Required for cloning
  }

  public TestdataAllowsUnassignedEntityProvidingEntity(
      String code, List<TestdataValue> valueRange) {
    this(code, valueRange, null);
  }

  public TestdataAllowsUnassignedEntityProvidingEntity(
      String code, List<TestdataValue> valueRange, TestdataValue value) {
    super(code);
    this.valueRange = valueRange;
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange", allowsUnassigned = true)
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValueRange() {
    return valueRange;
  }

  public void setValueRange(List<TestdataValue> valueRange) {
    this.valueRange = valueRange;
  }
}
