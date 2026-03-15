package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScore;

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
  private Score<?> previousBestScore;

  public GlobalBestUpdater(SharedGlobalState<Solution_> globalState, int agentId) {
    this.globalState = globalState;
    this.agentId = agentId;
    this.previousBestScore = null;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();

    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();

    if (bestSolution == null || bestScore == null) {
      return;
    }

    boolean shouldUpdate = shouldUpdateGlobalBest(stepScope, bestScore);

    if (shouldUpdate) {
      var localBestScore = bestScore.raw();
      previousBestScore = localBestScore;
      boolean updated = globalState.tryUpdate(bestSolution, localBestScore);

      if (updated) {
        long timeSpentMs = solverScope.getTimeMillisSpent();
        LOGGER.debug(
            "Agent {} updated global best (score: {}, time spent: {} ms, step index: {})",
            agentId,
            localBestScore,
            timeSpentMs,
            stepScope.getStepIndex());
      }
    }
  }

  private boolean shouldUpdateGlobalBest(
      AbstractStepScope<Solution_> stepScope, InnerScore<?> currentBestScore) {
    if (previousBestScore == null) {
      return true;
    }

    Boolean bestScoreImproved = stepScope.getBestScoreImproved();
    if (bestScoreImproved != null && bestScoreImproved) {
      return true;
    }

    @SuppressWarnings("unchecked")
    var currentScore = (Score) currentBestScore.raw();
    int comparisonResult = currentScore.compareTo((Score) previousBestScore);

    return comparisonResult > 0;
  }
}
