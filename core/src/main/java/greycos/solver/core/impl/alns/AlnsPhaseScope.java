package greycos.solver.core.impl.alns;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

public final class AlnsPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {
  private AlnsStepScope<Solution_> lastCompletedStepScope;

  public AlnsPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    lastCompletedStepScope = new AlnsStepScope<>(this, -1);
  }

  @Override
  public AlnsStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(AlnsStepScope<Solution_> stepScope) {
    lastCompletedStepScope = stepScope;
  }
}
