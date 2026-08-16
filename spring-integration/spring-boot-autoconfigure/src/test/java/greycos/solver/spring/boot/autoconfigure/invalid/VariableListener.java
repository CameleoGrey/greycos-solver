package greycos.solver.spring.boot.autoconfigure.invalid;

import greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NonNull;

public class VariableListener implements greycos.solver.core.api.cotwin.variable.VariableListener {
  @Override
  public void beforeEntityAdded(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}

  @Override
  public void afterEntityAdded(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}

  @Override
  public void beforeEntityRemoved(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}

  @Override
  public void afterEntityRemoved(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}

  @Override
  public void beforeVariableChanged(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}

  @Override
  public void afterVariableChanged(@NonNull ScoreDirector scoreDirector, @NonNull Object o) {}
}
