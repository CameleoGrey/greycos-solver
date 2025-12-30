package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Lifecycle listener that updates global best state when an agent finds a better solution.
 * This ensures that when any agent improves its best solution, the global state
 * is updated so other agents can benefit from it through compare-to-global.
 *
 * <p>This listener complements GlobalCompareListener:
 * <ul>
 *   <li>GlobalBestUpdater: Pushes local improvements to global state
 *   <li>GlobalCompareListener: Pulls global improvements to local agent
 * </ul>
 *
 * @param <Solution_> solution type
 */
public class GlobalBestUpdater<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private final SharedGlobalState<Solution_> globalState;
  private final int agentId;

  public GlobalBestUpdater(SharedGlobalState<Solution_> globalState, int agentId) {
    this.globalState = globalState;
    this.agentId = agentId;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();

    // Update global state with the agent's best solution
    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();

    if (bestSolution != null && bestScore != null) {
      globalState.tryUpdate(bestSolution, bestScore.raw());
    }
  }
}
