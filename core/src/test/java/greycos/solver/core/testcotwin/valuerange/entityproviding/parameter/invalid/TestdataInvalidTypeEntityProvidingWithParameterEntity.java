package greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataInvalidTypeEntityProvidingWithParameterEntity extends TestdataObject {

  public static EntityDescriptor<TestdataInvalidTypeEntityProvidingWithParameterSolution>
      buildEntityDescriptor() {
    return TestdataInvalidTypeEntityProvidingWithParameterSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataInvalidTypeEntityProvidingWithParameterEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataInvalidTypeEntityProvidingWithParameterSolution>
      buildVariableDescriptorForValueRange() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("valueRange");
  }

  private List<TestdataValue> valueRange;

  private TestdataValue value;

  public TestdataInvalidTypeEntityProvidingWithParameterEntity() {
    // Required for cloning
  }

  public TestdataInvalidTypeEntityProvidingWithParameterEntity(
      String code, List<TestdataValue> valueRange) {
    this(code, valueRange, null);
  }

  public TestdataInvalidTypeEntityProvidingWithParameterEntity(
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
  public List<TestdataValue> getValueRange(TestdataSolution solution) {
    return valueRange;
  }

  public void setValueRange(List<TestdataValue> valueRange) {
    this.valueRange = valueRange;
  }
}
