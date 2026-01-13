package ai.greycos.solver.core.testcotwin.shadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

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

  @ShadowVariable(
      variableListenerClass = FirstShadowUpdatingVariableListener.class,
      sourceVariableName = "value")
  public String getFirstShadow() {
    return firstShadow;
  }

  public void setFirstShadow(String firstShadow) {
    this.firstShadow = firstShadow;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  public static class FirstShadowUpdatingVariableListener
      extends DummyVariableListener<TestdataShadowedSolution, TestdataShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataShadowedSolution> scoreDirector,
        @NonNull TestdataShadowedEntity entity) {
      updateShadow(scoreDirector, entity);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataShadowedSolution> scoreDirector,
        @NonNull TestdataShadowedEntity entity) {
      updateShadow(scoreDirector, entity);
    }

    private void updateShadow(
        ScoreDirector<TestdataShadowedSolution> scoreDirector, TestdataShadowedEntity entity) {
      TestdataValue value = entity.getValue();
      scoreDirector.beforeVariableChanged(entity, "firstShadow");
      entity.setFirstShadow((value == null) ? null : value.getCode() + "/firstShadow");
      scoreDirector.afterVariableChanged(entity, "firstShadow");
    }
  }
}
