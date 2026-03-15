package ai.greycos.solver.quarkus.benchmark.it.cotwin;

import ai.greycos.solver.core.api.cotwin.variable.VariableListener;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class StringLengthVariableListener
    implements VariableListener<TestdataStringLengthShadowSolution, TestdataListValueShadowEntity> {

  @Override
  public void beforeEntityAdded(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    /* Nothing to do */
  }

  @Override
  public void afterEntityAdded(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    /* Nothing to do */
  }

  @Override
  public void beforeVariableChanged(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    /* Nothing to do */
  }

  @Override
  public void afterVariableChanged(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    int oldLength = entity.getLength();
    int newLength =
        entity.getEntity() != null
            ? entity.getEntity().getValues().stream()
                .map(TestdataListValueShadowEntity::getValue)
                .mapToInt(StringLengthVariableListener::getLength)
                .sum()
            : 0;
    if (oldLength != newLength) {
      scoreDirector.beforeVariableChanged(entity, "length");
      entity.setLength(newLength);
      scoreDirector.afterVariableChanged(entity, "length");
    }
  }

  @Override
  public void beforeEntityRemoved(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    /* Nothing to do */
  }

  @Override
  public void afterEntityRemoved(
      @NonNull ScoreDirector<TestdataStringLengthShadowSolution> scoreDirector,
      @NonNull TestdataListValueShadowEntity entity) {
    /* Nothing to do */
  }

  private static int getLength(String value) {
    if (value != null) {
      return value.length();
    } else {
      return 0;
    }
  }
}
