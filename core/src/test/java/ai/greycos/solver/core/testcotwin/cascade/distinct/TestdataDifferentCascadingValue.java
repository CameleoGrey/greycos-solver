package ai.greycos.solver.core.testcotwin.cascade.distinct;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;

@PlanningEntity
public class TestdataDifferentCascadingValue {

  public static EntityDescriptor<TestdataDifferentCascadingSolution> buildEntityDescriptor() {
    return TestdataDifferentCascadingSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataDifferentCascadingValue.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataDifferentCascadingEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataDifferentCascadingValue previous;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataDifferentCascadingValue next;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCascadeValue")
  private Integer cascadeValue;

  @CascadingUpdateShadowVariable(targetMethodName = "updateSecondCascadeValue")
  private Integer secondCascadeValue;

  private Integer value;
  private int numberOfCalls = 0;
  private int secondNumberOfCalls = 0;

  public TestdataDifferentCascadingValue(Integer value) {
    this.value = value;
  }

  public TestdataDifferentCascadingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataDifferentCascadingEntity entity) {
    this.entity = entity;
  }

  public TestdataDifferentCascadingValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataDifferentCascadingValue previous) {
    this.previous = previous;
  }

  public TestdataDifferentCascadingValue getNext() {
    return next;
  }

  public void setNext(TestdataDifferentCascadingValue next) {
    this.next = next;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Integer value) {
    this.value = value;
  }

  public void setCascadeValue(Integer cascadeValue) {
    this.cascadeValue = cascadeValue;
  }

  public Integer getCascadeValue() {
    return cascadeValue;
  }

  public Integer getSecondCascadeValue() {
    return secondCascadeValue;
  }

  public void setSecondCascadeValue(Integer secondCascadeValue) {
    this.secondCascadeValue = secondCascadeValue;
  }

  public int getNumberOfCalls() {
    return numberOfCalls;
  }

  public int getSecondNumberOfCalls() {
    return secondNumberOfCalls;
  }

  // ---Complex methods---//
  public void updateCascadeValue() {
    numberOfCalls++;
    if (cascadeValue == null || cascadeValue != value + 1) {
      cascadeValue = value + 1;
    }
  }

  public void updateSecondCascadeValue() {
    secondNumberOfCalls++;
    if (secondCascadeValue == null || secondCascadeValue != value + 1) {
      secondCascadeValue = value + 1;
    }
  }

  @Override
  public String toString() {
    return "TestdataDifferentCascadingValue{" + "value=" + value + '}';
  }
}
