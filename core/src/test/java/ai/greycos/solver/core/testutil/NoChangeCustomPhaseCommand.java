package ai.greycos.solver.core.testutil;

import ai.greycos.solver.core.api.solver.phase.PhaseCommand;
import ai.greycos.solver.core.api.solver.phase.PhaseCommandContext;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NoChangeCustomPhaseCommand implements PhaseCommand<Object> {

  @Override
  public void changeWorkingSolution(PhaseCommandContext<Object> context) {
    // Do nothing
  }
}
