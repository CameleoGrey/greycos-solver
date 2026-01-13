package ai.greycos.solver.core.testcotwin.shadow.multiplelistener;

import ai.greycos.solver.core.api.cotwin.variable.VariableListener;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class TestdataListMultipleShadowVariableListener
    implements VariableListener<
        TestdataListMultipleShadowVariableSolution, TestdataListMultipleShadowVariableValue> {

  @Override
  public void beforeVariableChanged(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    // Do nothing
  }

  @Override
  public void afterVariableChanged(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    scoreDirector.beforeVariableChanged(value, "listenerValue");
    value.setListenerValue(value.getIndex() + 20);
    scoreDirector.afterVariableChanged(value, "listenerValue");
  }

  @Override
  public void beforeEntityAdded(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    // Do nothing
  }

  @Override
  public void afterEntityAdded(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    // Do nothing
  }

  @Override
  public void beforeEntityRemoved(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    // Do nothing
  }

  @Override
  public void afterEntityRemoved(
      @NonNull ScoreDirector<TestdataListMultipleShadowVariableSolution> scoreDirector,
      @NonNull TestdataListMultipleShadowVariableValue value) {
    // Do nothing
  }
}
