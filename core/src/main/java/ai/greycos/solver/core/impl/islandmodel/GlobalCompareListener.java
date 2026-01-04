package ai.greycos.solver.core.impl.islandmodel;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractStepScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodically checks and adopts global best solution from SharedGlobalState.
 *
 * <p>Attached to local search phases to enable compare-to-global functionality. Provides faster
 * convergence and better solution quality. Complements migration.
 *
 * <p>Coordinates with migration to prevent double solution replacement in the same step.
 *
 * @param <Solution_> solution type
 */
public class GlobalCompareListener<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCompareListener.class);

  private final SharedGlobalState<Solution_> globalState;
  private final IslandModelConfig config;
  private final IslandAgent<Solution_> agent;
  private final int agentId;
  private int stepsUntilNextReceive;

  public GlobalCompareListener(
      SharedGlobalState<Solution_> globalState,
      IslandModelConfig config,
      IslandAgent<Solution_> agent,
      int agentId) {
    this.globalState = globalState;
    this.config = config;
    this.agent = agent;
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

    // Check if migration just occurred in this step
    // If so, skip global adoption to prevent double solution replacement
    if (agent.getMigrationJustOccurred()) {
      LOGGER.debug(
          "Agent {} skipping global adoption - migration just occurred in this step", agentId);
      agent.setMigrationJustOccurred(false); // Reset flag for next step
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

    var currentScore = (Score) currentInnerScore.raw();
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
    }
  }

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

    // Synchronize on the ScoreDirector to ensure atomic solution replacement
    // This prevents race conditions when phase thread and global best adoption access the solution
    // concurrently. Without this synchronization, Bavet sessions can become corrupted:
    // - NodeNetwork becomes stale (built for old solution entities)
    // - ScoreInliner cached values become invalid
    // - Shadow variable listeners fire out-of-order
    // See: IslandAgent.replaceCurrentSolution() for the same pattern used in migration
    synchronized (solverScope.getScoreDirector()) {
      solverScope.getScoreDirector().setWorkingSolution(newSolution);

      solverScope.setBestSolution(solverScope.getScoreDirector().cloneSolution(newSolution));

      var newBestScore = solverScope.getScoreDirector().calculateScore();
      solverScope.setBestScore(newBestScore);

      var scoreToSet = (Score) newBestScore.raw();
      solverScope.getScoreDirector().getSolutionDescriptor().setScore(newSolution, scoreToSet);

      stepScope.setScore(newBestScore);
      stepScope.setBestScoreImproved(true);
      phaseScope.setBestSolutionStepIndex(stepScope.getStepIndex());
    }
  }
}
