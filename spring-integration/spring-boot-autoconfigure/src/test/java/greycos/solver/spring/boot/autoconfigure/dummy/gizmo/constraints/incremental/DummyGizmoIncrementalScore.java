package greycos.solver.spring.boot.autoconfigure.dummy.gizmo.constraints.incremental;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;

import org.jspecify.annotations.NonNull;

public class DummyGizmoIncrementalScore implements IncrementalScoreCalculator<Object, SimpleScore> {

  @Override
  public void resetWorkingSolution(@NonNull Object workingSolution) {}

  @Override
  public void beforeVariableChanged(@NonNull Object entity, @NonNull String variableName) {}

  @Override
  public void afterVariableChanged(@NonNull Object entity, @NonNull String variableName) {}

  @Override
  public @NonNull SimpleScore calculateScore() {
    return null;
  }
}
