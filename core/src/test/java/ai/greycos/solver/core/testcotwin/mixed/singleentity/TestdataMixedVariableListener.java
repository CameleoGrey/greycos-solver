package ai.greycos.solver.core.testcotwin.mixed.singleentity;

import ai.greycos.solver.core.api.cotwin.variable.VariableListener;
import ai.greycos.solver.core.api.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class TestdataMixedVariableListener
    implements VariableListener<TestdataMixedSolution, TestdataMixedValue> {
  @Override
  public void beforeVariableChanged(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    // Ignore
  }

  @Override
  public void afterVariableChanged(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    value.setShadowVariableListenerValue(value.getIndex());
  }

  @Override
  public void beforeEntityAdded(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    // Ignore
  }

  @Override
  public void afterEntityAdded(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    // Ignore
  }

  @Override
  public void beforeEntityRemoved(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    // Ignore
  }

  @Override
  public void afterEntityRemoved(
      @NonNull ScoreDirector<TestdataMixedSolution> scoreDirector,
      @NonNull TestdataMixedValue value) {
    // Ignore
  }
}
