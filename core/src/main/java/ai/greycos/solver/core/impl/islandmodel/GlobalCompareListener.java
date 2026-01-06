package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle listener that checks and adopts global best solution periodically. Attached to local
 * search phases to enable compare-to-global functionality.
 *
 * <p>This listener provides "receive global update" mechanism where agents periodically check the
 * shared global best solution (the best solution found across ALL islands) and adopt it if it's
 * better than their current best. This provides:
 *
 * <ul>
 *   <li>Faster convergence - Global best is immediately available to all agents
 *   <li>Better solution quality - Prevents getting stuck in local optima
 *   <li>Complementary to migration - Migration provides diversity, global comparison provides
 *       intensification
 * </ul>
 *
 * <p>The frequency of checking the global best is controlled by the {@code
 * receiveGlobalUpdateFrequency} parameter. Islands will check the global best every N steps and
 * adopt it if it's better than their local best.
 *
 * <p>This listener only performs meaningful work when attached to local search phases, as it
 * requires access to {@link LocalSearchStepScope}. When attached to other phase types, it will
 * simply do nothing.
 *
 * @param <Solution_> solution type
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
    // Use receiveGlobalUpdateFrequency (or fall back to deprecated compareGlobalFrequency)
    this.stepsUntilNextReceive = getReceiveFrequency(config);
  }

  /** Gets the receive frequency from config. */
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
