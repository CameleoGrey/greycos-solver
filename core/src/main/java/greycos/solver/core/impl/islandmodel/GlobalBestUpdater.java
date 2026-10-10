package greycos.solver.core.impl.islandmodel;

import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that pushes local improvements to global state when an agent finds a better
 * solution. Updates only when local best improves to reduce lock contention on SharedGlobalState.
 */
public class GlobalBestUpdater<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalBestUpdater.class);

  private final SharedGlobalState<Solution_> globalState;
  private final int agentId;
  private InnerScore<?> previousBestScore;

  public GlobalBestUpdater(SharedGlobalState<Solution_> globalState, int agentId) {
    this.globalState = globalState;
    this.agentId = agentId;
    this.previousBestScore = null;
  }

  /**
   * Publishes an administrative adoption before termination observes its best-score improvement.
   */
  public static <Solution_> void publishCurrentBestToGlobal(SolverScope<Solution_> solverScope) {
    if (solverScope.getSolver() instanceof IslandSolver<Solution_> islandSolver) {
      var globalState = islandSolver.getEnclosingGlobalState();
      var solution = solverScope.getBestSolution();
      var score = solverScope.getBestScore();
      if (globalState != null
          && solution != null
          && score != null
          && !score.isStructurallyFlawed()) {
        globalState.tryUpdate(
            solution == solverScope.getWorkingSolution()
                ? solverScope.getScoreDirector().cloneSolution(solution)
                : solution,
            score);
      }
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    publishCurrentBest(stepScope.getPhaseScope().getSolverScope());
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    publishCurrentBest(phaseScope.getSolverScope());
  }

  /** Publishes a final best even when a child phase has no step lifecycle of its own. */
  public void publishCurrentBest(SolverScope<Solution_> solverScope) {
    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();

    if (bestSolution == null
        || bestScore == null
        || bestScore.isStructurallyFlawed()
        || previousBestScore != null && compareInnerScores(bestScore, previousBestScore) <= 0) {
      return;
    }

    previousBestScore = bestScore;
    // Construction heuristics temporarily retain the mutable working solution as their best.
    // Publish an island-owned snapshot before another construction step changes it.
    var publishedSolution =
        bestSolution == solverScope.getWorkingSolution()
            ? solverScope.getScoreDirector().cloneSolution(bestSolution)
            : bestSolution;
    if (globalState.tryUpdate(publishedSolution, bestScore)) {
      LOGGER.debug(
          "Agent {} updated global best (score: {}, time spent: {} ms)",
          agentId,
          bestScore.raw(),
          solverScope.getTimeMillisSpent());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareInnerScores(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }
}
