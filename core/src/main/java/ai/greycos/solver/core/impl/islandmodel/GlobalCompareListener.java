package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;

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

  /**
   * Creates a new global compare listener.
   *
   * @param globalState shared global state containing the best solution across all islands
   * @param config island model configuration
   * @param agentId ID of the agent this listener is attached to
   */
  public GlobalCompareListener(
      SharedGlobalState<Solution_> globalState, IslandModelConfig config, int agentId) {
    this.globalState = globalState;
    this.config = config;
    this.agentId = agentId;
    this.stepsUntilNextCheck = config.getCompareGlobalFrequency();
  }

  @Override
  public void stepEnded(
      ai.greycos.solver.core.impl.phase.scope.AbstractStepScope<Solution_> stepScope) {
    // Check if feature is enabled
    if (!config.isCompareGlobalEnabled()) {
      return;
    }

    // Only perform global comparison for local search phases
    if (!(stepScope instanceof LocalSearchStepScope)) {
      return;
    }

    var localSearchStepScope = (LocalSearchStepScope) stepScope;

    // Decrement counter
    stepsUntilNextCheck--;

    // Check if it's time to compare to global
    if (stepsUntilNextCheck <= 0) {
      checkAndAdoptGlobalBest(localSearchStepScope);
      stepsUntilNextCheck = config.getCompareGlobalFrequency();
    }
  }

  /**
   * Checks the global best solution and adopts it if it's better than the current best.
   *
   * <p>This method only performs meaningful work when attached to local search phases, as it
   * requires access to {@link LocalSearchStepScope}.
   *
   * @param stepScope current step scope
   */
  private void checkAndAdoptGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
    // Get global best
    Solution_ globalBest = globalState.getBestSolution();
    if (globalBest == null) {
      return; // No global best yet
    }

    // Get current best from phase scope
    var phaseScope = stepScope.getPhaseScope();
    var currentInnerScore = phaseScope.getBestScore();
    var globalScore = globalState.getBestScore();

    if (globalScore == null || currentInnerScore == null) {
      return;
    }

    // Extract raw Score from InnerScore
    // phaseScope.getBestScore() returns InnerScore<?>, which wraps Score<?>
    // We need to extract raw Score<?> for comparison
    // Cast both to raw Score before calling compareTo, same pattern as IslandAgent
    @SuppressWarnings("unchecked")
    var currentScore = (Score) currentInnerScore.raw();
    @SuppressWarnings("unchecked")
    var globalScoreCast = (Score) globalScore;

    // Compare (higher score is better in Greycos)
    // Both are raw Score types after casting
    // This is safe because we're comparing scores from the same problem type
    int comparisonResult = globalScoreCast.compareTo(currentScore);

    if (comparisonResult > 0) {
      LOGGER.info(
          "Agent {} adopting global best (score: {} vs {})",
          agentId,
          globalScoreCast,
          currentScore);

      // Clone global best solution
      Solution_ clonedGlobalBest = deepClone(globalBest, stepScope);

      // Replace current solution with global best
      replaceCurrentSolution(clonedGlobalBest, stepScope);

      // Update global best (in case this agent now has the global best)
      updateGlobalBest(stepScope);
    }
  }

  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution, LocalSearchStepScope<Solution_> stepScope) {
    if (solution == null) {
      return null;
    }
    // Use Greycos's solution cloner from score director
    return stepScope.getScoreDirector().cloneSolution(solution);
  }

  private void replaceCurrentSolution(
      Solution_ newSolution, LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();

    // Set the new solution as the working solution
    solverScope.getScoreDirector().setWorkingSolution(newSolution);

    // Update the best solution
    solverScope.setBestSolution(solverScope.getScoreDirector().cloneSolution(newSolution));

    // Calculate and set the best score
    var newBestScore = solverScope.getScoreDirector().calculateScore();
    solverScope.setBestScore(newBestScore);

    // CRITICAL: Update the embedded score in the working solution
    // This ensures that when the phase compares working solution's score
    // against the best score, it uses the correct (updated) score
    // Use raw cast to handle wildcard type, same pattern as IslandAgent
    @SuppressWarnings("unchecked")
    var scoreToSet = (Score) newBestScore.raw();
    solverScope.getScoreDirector().getSolutionDescriptor().setScore(newSolution, scoreToSet);
  }

  private void updateGlobalBest(LocalSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var solverScope = phaseScope.getSolverScope();
    var scoreDirector = solverScope.getScoreDirector();
    var currentSolution = scoreDirector.getWorkingSolution();

    // Try to update global best - extract raw Score from InnerScore
    // calculateScore() returns InnerScore<?>, which has .raw() method returning Score<?>>
    // SharedGlobalState.tryUpdate accepts Score<?>, so we extract and pass the raw Score
    // This is correct because both scores come from the same problem type
    globalState.tryUpdate(currentSolution, scoreDirector.calculateScore().raw());
  }
}
