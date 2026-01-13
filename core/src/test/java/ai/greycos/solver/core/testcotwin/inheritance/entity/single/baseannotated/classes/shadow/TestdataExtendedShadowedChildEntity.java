package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.shadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningEntity
public class TestdataExtendedShadowedChildEntity extends TestdataExtendedShadowedParentEntity {

  public static EntityDescriptor<TestdataExtendedShadowedSolution> buildEntityDescriptor() {
    return TestdataExtendedShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataExtendedShadowedChildEntity.class);
  }

  private String secondShadow;

  public TestdataExtendedShadowedChildEntity() {}

  public TestdataExtendedShadowedChildEntity(String code) {
    super(code);
  }

  public TestdataExtendedShadowedChildEntity(String code, TestdataValue value) {
    super(code, value);
  }

  @ShadowVariable(
      variableListenerClass = SecondShadowUpdatingVariableListener.class,
      sourceVariableName = "firstShadow")
  public String getSecondShadow() {
    return secondShadow;
  }

  public void setSecondShadow(String secondShadow) {
    this.secondShadow = secondShadow;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  public static class SecondShadowUpdatingVariableListener
      extends DummyVariableListener<
          TestdataExtendedShadowedSolution, TestdataExtendedShadowedParentEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataExtendedShadowedSolution> scoreDirector,
        @NonNull TestdataExtendedShadowedParentEntity entity) {
      updateShadow(scoreDirector, entity);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataExtendedShadowedSolution> scoreDirector,
        @NonNull TestdataExtendedShadowedParentEntity entity) {
      updateShadow(scoreDirector, entity);
    }

    private void updateShadow(
        ScoreDirector<TestdataExtendedShadowedSolution> scoreDirector,
        TestdataExtendedShadowedParentEntity entity) {
      String firstShadow = entity.getFirstShadow();
      if (entity instanceof TestdataExtendedShadowedChildEntity childEntity) {
        scoreDirector.beforeVariableChanged(childEntity, "secondShadow");
        childEntity.setSecondShadow((firstShadow == null) ? null : firstShadow + "/secondShadow");
        scoreDirector.afterVariableChanged(childEntity, "secondShadow");
      }
    }
  }
}
