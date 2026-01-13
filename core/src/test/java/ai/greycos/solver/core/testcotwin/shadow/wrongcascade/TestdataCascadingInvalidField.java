package ai.greycos.solver.core.testcotwin.shadow.wrongcascade;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingEntity;
import ai.greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingSolution;

@PlanningEntity
public class TestdataCascadingInvalidField {

  public static EntityDescriptor<TestdataSingleCascadingSolution> buildEntityDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
            TestdataSingleCascadingSolution.class,
            TestdataSingleCascadingEntity.class,
            TestdataCascadingInvalidField.class)
        .findEntityDescriptorOrFail(TestdataCascadingInvalidField.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataSingleCascadingEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingInvalidField previous;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingInvalidField next;

  @CascadingUpdateShadowVariable(targetMethodName = "value")
  private Integer cascadeValue;

  private Integer value;

  public TestdataCascadingInvalidField(Integer value) {
    this.value = value;
  }

  public TestdataSingleCascadingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataSingleCascadingEntity entity) {
    this.entity = entity;
  }

  public TestdataCascadingInvalidField getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataCascadingInvalidField previous) {
    this.previous = previous;
  }

  public TestdataCascadingInvalidField getNext() {
    return next;
  }

  public void setNext(TestdataCascadingInvalidField next) {
    this.next = next;
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Integer value) {
    this.value = value;
  }

  public Integer getCascadeValue() {
    return cascadeValue;
  }

  // ---Complex methods---//
  public void updateCascadeValue() {
    if (value != null) {
      value = value + 1;
    }
  }
}
