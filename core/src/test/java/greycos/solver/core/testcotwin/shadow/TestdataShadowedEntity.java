package greycos.solver.core.testcotwin.shadow;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataShadowedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataShadowedSolution> buildEntityDescriptor() {
    return TestdataShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataShadowedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataShadowedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;
  private String firstShadow;

  public TestdataShadowedEntity() {}

  public TestdataShadowedEntity(String code) {
    super(code);
  }

  public TestdataShadowedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ShadowVariable(supplierName = "updateFirstShadow")
  public String getFirstShadow() {
    return firstShadow;
  }

  public void setFirstShadow(String firstShadow) {
    this.firstShadow = firstShadow;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @ShadowSources("value")
  public String updateFirstShadow() {
    if (value == null) {
      return null;
    }
    return value.getCode() + "/firstShadow";
  }
}
