package greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.composite;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataAllowsUnassignedCompositeEntityProvidingEntity extends TestdataObject {

  public static EntityDescriptor<TestdataAllowsUnassignedCompositeEntityProvidingSolution>
      buildEntityDescriptor() {
    return TestdataAllowsUnassignedCompositeEntityProvidingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataAllowsUnassignedCompositeEntityProvidingEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataAllowsUnassignedCompositeEntityProvidingSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private List<TestdataValue> valueRange1;
  private List<TestdataValue> valueRange2;

  private TestdataValue value;

  public TestdataAllowsUnassignedCompositeEntityProvidingEntity() {
    // Required for cloning
  }

  public TestdataAllowsUnassignedCompositeEntityProvidingEntity(
      String code, List<TestdataValue> valueRange1, List<TestdataValue> valueRange2) {
    super(code);
    this.valueRange1 = valueRange1;
    this.valueRange2 = valueRange2;
  }

  @PlanningVariable(
      valueRangeProviderRefs = {"valueRange1", "valueRange2"},
      allowsUnassigned = true)
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "valueRange1")
  public List<TestdataValue> getValueRange1() {
    return valueRange1;
  }

  public void setValueRange1(List<TestdataValue> valueRange1) {
    this.valueRange1 = valueRange1;
  }

  @ValueRangeProvider(id = "valueRange2")
  public List<TestdataValue> getValueRange2() {
    return valueRange2;
  }

  public void setValueRange2(List<TestdataValue> valueRange2) {
    this.valueRange2 = valueRange2;
  }
}
