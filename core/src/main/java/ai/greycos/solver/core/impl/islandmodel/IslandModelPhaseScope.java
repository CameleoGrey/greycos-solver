package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Phase scope for island model orchestration.
 *
 * <p>Island model has no direct outer step loop; it coordinates child island phases and agents in
 * parallel.
 */
public final class IslandModelPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {

  private final IslandModelStepScope<Solution_> lastCompletedStepScope;

  public IslandModelPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    this.lastCompletedStepScope = new IslandModelStepScope<>(this, -1);
  }

  @Override
  public IslandModelStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }
}
