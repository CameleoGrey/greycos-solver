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
public class TestdataCascadingWrongMethod {

  public static EntityDescriptor<TestdataSingleCascadingSolution> buildEntityDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
            TestdataSingleCascadingSolution.class,
            TestdataSingleCascadingEntity.class,
            TestdataCascadingWrongMethod.class)
        .findEntityDescriptorOrFail(TestdataCascadingWrongMethod.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataSingleCascadingEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingWrongMethod previous;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingWrongMethod next;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCascadeValue")
  private Integer cascadeValue;

  @CascadingUpdateShadowVariable(targetMethodName = "badUpdateCascadeValueWithReturnType")
  private Integer cascadeValueReturnType;

  private Integer value;

  public TestdataCascadingWrongMethod(Integer value) {
    this.value = value;
  }

  public TestdataSingleCascadingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataSingleCascadingEntity entity) {
    this.entity = entity;
  }

  public TestdataCascadingWrongMethod getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataCascadingWrongMethod previous) {
    this.previous = previous;
  }

  public TestdataCascadingWrongMethod getNext() {
    return next;
  }

  public void setNext(TestdataCascadingWrongMethod next) {
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

  public Integer getCascadeValueReturnType() {
    return cascadeValueReturnType;
  }

  // ---Complex methods---//
  public void updateCascadeValue() {
    if (value != null) {
      value = value + 1;
    }
  }

  public Integer updateCascadeValueWithReturnType() {
    updateCascadeValue();
    cascadeValueReturnType = cascadeValue;
    return cascadeValueReturnType;
  }
}
