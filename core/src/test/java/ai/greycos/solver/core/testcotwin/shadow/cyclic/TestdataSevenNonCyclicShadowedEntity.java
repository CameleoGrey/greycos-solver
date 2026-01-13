package ai.greycos.solver.core.testcotwin.shadow.cyclic;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataSevenNonCyclicShadowedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataSevenNonCyclicShadowedSolution> buildEntityDescriptor() {
    return TestdataSevenNonCyclicShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataSevenNonCyclicShadowedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataSevenNonCyclicShadowedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;
  // Intentionally out of order
  private String thirdShadow;
  private String fifthShadow;
  private String firstShadow;
  private String fourthShadow;
  private String secondShadow;
  private String seventhShadow;
  private String sixthShadow;

  public TestdataSevenNonCyclicShadowedEntity() {}

  public TestdataSevenNonCyclicShadowedEntity(String code) {
    super(code);
  }

  public TestdataSevenNonCyclicShadowedEntity(String code, TestdataValue value) {
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

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "secondShadow")
  public String getThirdShadow() {
    return thirdShadow;
  }

  public void setThirdShadow(String thirdShadow) {
    this.thirdShadow = thirdShadow;
  }

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "fourthShadow")
  public String getFifthShadow() {
    return fifthShadow;
  }

  public void setFifthShadow(String fifthShadow) {
    this.fifthShadow = fifthShadow;
  }

  @ShadowVariable(variableListenerClass = DummyVariableListener.class, sourceVariableName = "value")
  public String getFirstShadow() {
    return firstShadow;
  }

  public void setFirstShadow(String firstShadow) {
    this.firstShadow = firstShadow;
  }

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "thirdShadow")
  public String getFourthShadow() {
    return fourthShadow;
  }

  public void setFourthShadow(String fourthShadow) {
    this.fourthShadow = fourthShadow;
  }

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "firstShadow")
  public String getSecondShadow() {
    return secondShadow;
  }

  public void setSecondShadow(String secondShadow) {
    this.secondShadow = secondShadow;
  }

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "sixthShadow")
  public String getSeventhShadow() {
    return seventhShadow;
  }

  public void setSeventhShadow(String seventhShadow) {
    this.seventhShadow = seventhShadow;
  }

  @ShadowVariable(
      variableListenerClass = DummyVariableListener.class,
      sourceVariableName = "fifthShadow")
  public String getSixthShadow() {
    return sixthShadow;
  }

  public void setSixthShadow(String sixthShadow) {
    this.sixthShadow = sixthShadow;
  }
}
