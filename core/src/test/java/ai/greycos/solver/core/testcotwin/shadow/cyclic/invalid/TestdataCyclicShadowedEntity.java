package ai.greycos.solver.core.testcotwin.shadow.cyclic.invalid;

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
public class TestdataCyclicShadowedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataCyclicShadowedSolution> buildEntityDescriptor() {
    return TestdataCyclicShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataCyclicShadowedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataCyclicShadowedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;
  private String rockShadow;
  private String paperShadow;
  private String scissorsShadow;

  public TestdataCyclicShadowedEntity() {}

  public TestdataCyclicShadowedEntity(String code) {
    super(code);
  }

  public TestdataCyclicShadowedEntity(String code, TestdataValue value) {
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
      variableListenerClass = RockShadowUpdatingVariableListener.class,
      sourceVariableName = "scissorsShadow")
  public String getRockShadow() {
    return rockShadow;
  }

  public void setRockShadow(String rockShadow) {
    this.rockShadow = rockShadow;
  }

  @ShadowVariable(
      variableListenerClass = PaperShadowUpdatingVariableListener.class,
      sourceVariableName = "rockShadow")
  public String getPaperShadow() {
    return paperShadow;
  }

  public void setPaperShadow(String paperShadow) {
    this.paperShadow = paperShadow;
  }

  @ShadowVariable(
      variableListenerClass = ScissorsShadowUpdatingVariableListener.class,
      sourceVariableName = "paperShadow")
  public String getScissorsShadow() {
    return scissorsShadow;
  }

  public void setScissorsShadow(String scissorsShadow) {
    this.scissorsShadow = scissorsShadow;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  public static class RockShadowUpdatingVariableListener
      extends DummyVariableListener<TestdataCyclicShadowedSolution, TestdataCyclicShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    private void updateShadow(
        TestdataCyclicShadowedEntity entity,
        ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector) {
      String scissors = entity.getScissorsShadow();
      scoreDirector.beforeVariableChanged(entity, "rockShadow");
      entity.setRockShadow("Rock beats (" + scissors + ")");
      scoreDirector.afterVariableChanged(entity, "rockShadow");
    }
  }

  public static class PaperShadowUpdatingVariableListener
      extends DummyVariableListener<TestdataCyclicShadowedSolution, TestdataCyclicShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    private void updateShadow(
        TestdataCyclicShadowedEntity entity,
        ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector) {
      String rock = entity.getRockShadow();
      scoreDirector.beforeVariableChanged(entity, "paperShadow");
      entity.setPaperShadow("Paper beats (" + rock + ")");
      scoreDirector.afterVariableChanged(entity, "paperShadow");
    }
  }

  public static class ScissorsShadowUpdatingVariableListener
      extends DummyVariableListener<TestdataCyclicShadowedSolution, TestdataCyclicShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    private void updateShadow(
        TestdataCyclicShadowedEntity entity,
        ScoreDirector<TestdataCyclicShadowedSolution> scoreDirector) {
      String paper = entity.getPaperShadow();
      scoreDirector.beforeVariableChanged(entity, "scissorsShadow");
      entity.setScissorsShadow("Scissors beats (" + paper + ")");
      scoreDirector.afterVariableChanged(entity, "scissorsShadow");
    }
  }
}
