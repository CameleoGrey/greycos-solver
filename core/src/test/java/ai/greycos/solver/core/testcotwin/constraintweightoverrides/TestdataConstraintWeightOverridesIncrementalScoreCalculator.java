package ai.greycos.solver.core.testcotwin.constraintweightoverrides;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.TestdataEntity;

import org.jspecify.annotations.NonNull;

public final class TestdataConstraintWeightOverridesIncrementalScoreCalculator
    implements IncrementalScoreCalculator<TestdataConstraintWeightOverridesSolution, SimpleScore> {

  private TestdataConstraintWeightOverridesSolution workingSolution;
  private List<TestdataEntity> entityList;

  @Override
  public void resetWorkingSolution(
      @NonNull TestdataConstraintWeightOverridesSolution workingSolution) {
    this.workingSolution = workingSolution;
    this.entityList = new ArrayList<>(workingSolution.getEntityList());
  }

  @Override
  public void beforeEntityAdded(@NonNull Object entity) {}

  @Override
  public void afterEntityAdded(@NonNull Object entity) {
    entityList.add((TestdataEntity) entity);
  }

  @Override
  public void beforeVariableChanged(@NonNull Object entity, @NonNull String variableName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void afterVariableChanged(@NonNull Object entity, @NonNull String variableName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void beforeEntityRemoved(@NonNull Object entity) {}

  @Override
  public void afterEntityRemoved(@NonNull Object entity) {
    entityList.remove((TestdataEntity) entity);
  }

  @Override
  public @NonNull SimpleScore calculateScore() {
    var firstWeight =
        workingSolution.getConstraintWeightOverrides().getConstraintWeight("First weight");
    if (firstWeight != null) {
      return SimpleScore.of(entityList.size() * firstWeight.score());
    }
    return SimpleScore.of(entityList.size());
  }
}
