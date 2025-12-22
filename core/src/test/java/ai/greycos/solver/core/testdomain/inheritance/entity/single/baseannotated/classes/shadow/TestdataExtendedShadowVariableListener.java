package ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.classes.shadow;

import ai.greycos.solver.core.api.domain.variable.VariableListener;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class TestdataExtendedShadowVariableListener
    implements VariableListener<TestdataExtendedShadowSolution, TestdataExtendedShadowEntity> {

  @Override
  public void beforeEntityAdded(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }

  @Override
  public void afterEntityAdded(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }

  @Override
  public void beforeEntityRemoved(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }

  @Override
  public void afterEntityRemoved(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }

  @Override
  public void beforeVariableChanged(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }

  @Override
  public void afterVariableChanged(
      @NonNull ScoreDirector<TestdataExtendedShadowSolution> scoreDirector,
      @NonNull TestdataExtendedShadowEntity entity) {
    // Ignore
  }
}
