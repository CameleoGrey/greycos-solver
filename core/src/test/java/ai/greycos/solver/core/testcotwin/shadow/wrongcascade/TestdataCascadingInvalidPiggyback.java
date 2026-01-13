package ai.greycos.solver.core.testcotwin.shadow.wrongcascade;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PiggybackShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingEntity;
import ai.greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingSolution;

@PlanningEntity
public class TestdataCascadingInvalidPiggyback {

  public static EntityDescriptor<TestdataSingleCascadingSolution> buildEntityDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
            TestdataSingleCascadingSolution.class,
            TestdataSingleCascadingEntity.class,
            TestdataCascadingInvalidPiggyback.class)
        .findEntityDescriptorOrFail(TestdataCascadingInvalidPiggyback.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataSingleCascadingEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingInvalidPiggyback previous;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataCascadingInvalidPiggyback next;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCascadeValue")
  private Integer cascadeValue;

  @PiggybackShadowVariable(shadowVariableName = "cascadeValue")
  private Integer cascadeValue2;

  private Integer value;

  public TestdataCascadingInvalidPiggyback(Integer value) {
    this.value = value;
  }

  public TestdataSingleCascadingEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataSingleCascadingEntity entity) {
    this.entity = entity;
  }

  public TestdataCascadingInvalidPiggyback getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataCascadingInvalidPiggyback previous) {
    this.previous = previous;
  }

  public TestdataCascadingInvalidPiggyback getNext() {
    return next;
  }

  public void setNext(TestdataCascadingInvalidPiggyback next) {
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
