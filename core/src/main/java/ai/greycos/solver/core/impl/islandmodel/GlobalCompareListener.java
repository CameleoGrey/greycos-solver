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
 * <p>This listener provides "compare to global" mechanism where agents periodically check the
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
  private int stepsUntilNextCheck;

  public GlobalCompareListener(
      SharedGlobalState<Solution_> globalState, IslandModelConfig config, int agentId) {
    this.globalState = globalState;
    this.config = config;
    this.agentId = agentId;
    this.stepsUntilNextCheck = config.getCompareGlobalFrequency();
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

    stepsUntilNextCheck--;

    if (stepsUntilNextCheck <= 0) {
      checkAndAdoptGlobalBest(localSearchStepScope);
      stepsUntilNextCheck = config.getCompareGlobalFrequency();
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

      replaceCurrentSolution(clonedGlobalBest, stepScope);

      updateGlobalBest(stepScope);
    }
  }

  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution, LocalSearchStepScope<Solution_> stepScope) {
    if (solution == null) {
      return null;
    }
    return stepScope.getScoreDirector().cloneSolution(solution);
  }

  private void replaceCurrentSolution(
      Solution_ newSolution, LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();

    solverScope.getScoreDirector().setWorkingSolution(newSolution);

    solverScope.setBestSolution(solverScope.getScoreDirector().cloneSolution(newSolution));

    var newBestScore = solverScope.getScoreDirector().calculateScore();
    solverScope.setBestScore(newBestScore);

    @SuppressWarnings("unchecked")
    var scoreToSet = (Score) newBestScore.raw();
    solverScope.getScoreDirector().getSolutionDescriptor().setScore(newSolution, scoreToSet);

    // Update step scope's score to reflect the new best solution
    stepScope.setScore(newBestScore);
    // Mark that the best score was improved in this step (for logging)
    stepScope.setBestScoreImproved(true);
    // Update the phase scope's best solution step index
    phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
  }

  private void updateGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    
    // Use the best solution and its score directly, not the working solution
    var bestSolution = solverScope.getBestSolution();
    var bestScore = solverScope.getBestScore();
    
    if (bestSolution != null && bestScore != null) {
      globalState.tryUpdate(bestSolution, bestScore.raw());
    }
  }
}
