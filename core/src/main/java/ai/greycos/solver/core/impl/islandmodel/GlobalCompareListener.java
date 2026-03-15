package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that periodically checks and adopts global best solution from
 * SharedGlobalState. Attached to local search phases to enable compare-to-global functionality.
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
    var globalSnapshot = globalState.getBestSnapshot();
    if (globalSnapshot == null) {
      return;
    }
    Solution_ globalBest = globalSnapshot.getSolution();

    var phaseScope = stepScope.getPhaseScope();
    var currentInnerScore = phaseScope.getBestScore();
    var globalInnerScore = globalSnapshot.getInnerScore();

    if (globalInnerScore == null || currentInnerScore == null) {
      return;
    }

    int comparisonResult = compareInnerScores(globalInnerScore, currentInnerScore);

    if (comparisonResult > 0) {
      LOGGER.info(
          "Agent {} adopting global best (score: {} vs {})",
          agentId,
          globalInnerScore.raw(),
          currentInnerScore.raw());

      var syncMove = SolutionSyncMove.createMove(stepScope.getScoreDirector(), globalBest);
      var solverScope = stepScope.getPhaseScope().getSolverScope();
      solverScope.setPendingMoveIfBetter(syncMove, globalInnerScore, true);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareInnerScores(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }
}
