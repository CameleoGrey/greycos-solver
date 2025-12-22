package ai.greycos.solver.core.testutil;

import java.util.function.BooleanSupplier;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.api.solver.phase.PhaseCommand;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NoChangeCustomPhaseCommand implements PhaseCommand<Object> {

  @Override
  public void changeWorkingSolution(
      ScoreDirector<Object> scoreDirector, BooleanSupplier isPhaseTerminated) {
    // Do nothing
  }
}
