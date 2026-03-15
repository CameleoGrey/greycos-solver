package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

/**
 * Step scope used for island model phase lifecycle bookkeeping.
 *
 * <p>The island model orchestrates internal island phases and does not expose its own outer steps,
 * therefore this scope is primarily used as a placeholder for phase lifecycle contracts.
 */
final class IslandModelStepScope<Solution_> extends AbstractStepScope<Solution_> {

  private final IslandModelPhaseScope<Solution_> phaseScope;

  IslandModelStepScope(IslandModelPhaseScope<Solution_> phaseScope, int stepIndex) {
    super(stepIndex);
    this.phaseScope = phaseScope;
  }

  @Override
  public IslandModelPhaseScope<Solution_> getPhaseScope() {
    return phaseScope;
  }
}
