package ai.greycos.solver.core.testcotwin.reflect.generic;

import java.util.Map;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataGenericEntity<T> extends TestdataObject {

  public static EntityDescriptor<TestdataGenericSolution> buildEntityDescriptor() {
    return TestdataGenericSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataGenericEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataGenericSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataGenericValue<T> value;
  private TestdataGenericValue<T> subTypeValue;
  private TestdataGenericValue<Map<T, TestdataGenericValue<T>>> complexGenericValue;

  public TestdataGenericEntity() {}

  public TestdataGenericEntity(String code) {
    super(code);
  }

  public TestdataGenericEntity(String code, TestdataGenericValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataGenericValue<T> getValue() {
    return value;
  }

  @PlanningVariable(valueRangeProviderRefs = "subTypeValueRange")
  public TestdataGenericValue<T> getSubTypeValue() {
    return subTypeValue;
  }

  public void setValue(TestdataGenericValue<T> value) {
    this.value = value;
  }

  public void setSubTypeValue(TestdataGenericValue<T> subTypeValue) {
    this.subTypeValue = subTypeValue;
  }

  @PlanningVariable(valueRangeProviderRefs = "complexGenericValueRange")
  public TestdataGenericValue<Map<T, TestdataGenericValue<T>>> getComplexGenericValue() {
    return complexGenericValue;
  }

  public void setComplexGenericValue(
      TestdataGenericValue<Map<T, TestdataGenericValue<T>>> complexGenericValue) {
    this.complexGenericValue = complexGenericValue;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
