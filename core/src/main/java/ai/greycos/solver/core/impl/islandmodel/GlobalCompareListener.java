package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that periodically checks and adopts global best solution from SharedGlobalState.
 * Attached to local search phases to enable compare-to-global functionality.
 */
public class GlobalCompareListener<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCompareListener.class);

  private final SharedGlobalState<Solution_> globalState;
  private final IslandModelConfig config;
  private final int agentId;
  private int stepsUntilNextReceive;

  public GlobalCompareListener(
      SharedGlobalState<Solution_> globalState, IslandModelConfig config, int agentId) {
    this.globalState = globalState;
    this.config = config;
    this.agentId = agentId;
    this.stepsUntilNextReceive = getReceiveFrequency(config);
  }

  private int getReceiveFrequency(IslandModelConfig config) {
    return config.getReceiveGlobalUpdateFrequency();
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (!config.isCompareGlobalEnabled()) {
      return;
    }

    if (!(stepScope instanceof LocalSearchStepScope)) {
      return;
    }

    var localSearchStepScope = (LocalSearchStepScope) stepScope;

    stepsUntilNextReceive--;

    if (stepsUntilNextReceive <= 0) {
      checkAndAdoptGlobalBest(localSearchStepScope);
      stepsUntilNextReceive = getReceiveFrequency(config);
    }
  }

  private void checkAndAdoptGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
    Solution_ globalBest = globalState.getBestSolution();
    if (globalBest == null) {
      return;
    }

    var phaseScope = stepScope.getPhaseScope();
    var currentInnerScore = phaseScope.getBestScore();
    var globalScore = globalState.getBestScore();

    if (globalScore == null || currentInnerScore == null) {
      return;
    }

    @SuppressWarnings("unchecked")
    var currentScore = (Score) currentInnerScore.raw();
    @SuppressWarnings("unchecked")
    var globalScoreCast = (Score) globalScore;

    int comparisonResult = globalScoreCast.compareTo(currentScore);

    if (comparisonResult > 0) {
      LOGGER.info(
          "Agent {} adopting global best (score: {} vs {})",
          agentId,
          globalScoreCast,
          currentScore);

      Solution_ clonedGlobalBest = deepClone(globalBest, stepScope);
      var syncMove = SolutionSyncMove.createMove(stepScope.getScoreDirector(), clonedGlobalBest);
      var solverScope = stepScope.getPhaseScope().getSolverScope();
      solverScope.setPendingMove(syncMove, true);
    }
  }

  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution, LocalSearchStepScope<Solution_> stepScope) {
    if (solution == null) {
      return null;
    }
    return stepScope.getScoreDirector().cloneSolution(solution);
  }
}
