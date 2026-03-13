package ai.greycos.solver.quarkus.testcotwin.gizmo;

import ai.greycos.solver.core.api.cotwin.variable.VariableListener;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class DummyVariableListener implements VariableListener {

  @Override
  public void beforeEntityAdded(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }

  @Override
  public void afterEntityAdded(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }

  @Override
  public void beforeVariableChanged(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }

  @Override
  public void afterVariableChanged(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }

  @Override
  public void beforeEntityRemoved(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }

  @Override
  public void afterEntityRemoved(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {
    // Ignore
  }
}
