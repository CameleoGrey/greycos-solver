package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that updates global best state when an agent finds a better solution.
 *
 * <p>Unlike the original implementation that updated global best on every step, this optimized
 * version only updates global best when the island's local best improves. This reduces lock
 * contention on SharedGlobalState while maintaining correctness.
 *
 * <p>This listener complements GlobalCompareListener:
 *
 * <ul>
 *   <li>GlobalBestUpdater: Pushes local improvements to global state (only when improved)
 *   <li>GlobalCompareListener: Pulls global improvements to local agent (periodically)
 * </ul>
 *
 * @param <Solution_> solution type
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

    // Get current best solution and score
    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();

    if (bestSolution == null || bestScore == null) {
      return;
    }

    // Only update global best if local best improved
    boolean shouldUpdate = shouldUpdateGlobalBest(stepScope, bestScore);

    if (shouldUpdate) {
      boolean updated = globalState.tryUpdate(bestSolution, bestScore.raw());
      if (updated) {
        LOGGER.debug("Agent {} updated global best (score: {})", agentId, bestScore.raw());
        previousBestScore = bestScore.raw();
      }
    }
  }

  /**
   * Determines whether the global best should be updated.
   *
   * <p>Updates occur when:
   *
   * <ul>
   *   <li>This is the first step (previousBestScore is null)
   *   <li>The best score improved in this step
   *   <li>The current best score is better than the previous best score
   * </ul>
   *
   * @param stepScope the step scope containing improvement information
   * @param currentBestScore the current best score
   * @return true if global best should be updated, false otherwise
   */
  private boolean shouldUpdateGlobalBest(
      AbstractStepScope<Solution_> stepScope, InnerScore<?> currentBestScore) {
    // First step - always update
    if (previousBestScore == null) {
      return true;
    }

    // Check if best score improved in this step
    Boolean bestScoreImproved = stepScope.getBestScoreImproved();
    if (bestScoreImproved != null && bestScoreImproved) {
      return true;
    }

    // Compare current best with previous best
    // This handles edge cases where bestScoreImproved might be null
    @SuppressWarnings("unchecked")
    var currentScore = (Score) currentBestScore.raw();
    int comparisonResult = currentScore.compareTo((Score) previousBestScore);

    return comparisonResult > 0;
  }
}
