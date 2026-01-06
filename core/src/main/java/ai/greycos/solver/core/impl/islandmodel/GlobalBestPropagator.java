package ai.greycos.solver.core.impl.islandmodel;

import java.util.Objects;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.event.SolverEventSupport;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Propagates global best solution updates from SharedGlobalState to main solver scope.
 *
 * <p>This ensures that:
 *
 * <ul>
 *   <li>Main solver's best solution is updated during solving (not just at end)
 *   <li>BestSolutionChangedEvent events are fired for user listeners
 *   <li>Termination criteria based on best score work correctly
 * </ul>
 *
 * @param <Solution_> solution type
 */
public class GlobalBestPropagator<Solution_> implements Consumer<Solution_> {

  private final SharedGlobalState<Solution_> globalState;
  private final SolverScope<Solution_> mainSolverScope;
  private final SolverEventSupport<Solution_> solverEventSupport;
  private final EventProducerId eventProducerId;

  private volatile Solution_ lastKnownBestSolution;
  private volatile Score<?> lastKnownBestScore;

  public GlobalBestPropagator(
      SharedGlobalState<Solution_> globalState,
      SolverScope<Solution_> mainSolverScope,
      SolverEventSupport<Solution_> solverEventSupport,
      EventProducerId eventProducerId) {
    this.globalState = Objects.requireNonNull(globalState);
    this.mainSolverScope = Objects.requireNonNull(mainSolverScope);
    this.solverEventSupport = Objects.requireNonNull(solverEventSupport);
    this.eventProducerId = Objects.requireNonNull(eventProducerId);
  }

  /** Start observing global best changes. Must be called before island agents start solving. */
  public void start() {
    globalState.addObserver(this);
  }

  /** Stop observing global best changes. Must be called after island agents complete. */
  public void stop() {
    globalState.removeObserver(this);
  }

  @Override
  public void accept(Solution_ newGlobalBest) {
    if (newGlobalBest == null) {
      return;
    }

    var newGlobalBestScore = globalState.getBestScore();
    if (newGlobalBestScore == null) {
      return;
    }

    // Only update if score actually improved
    boolean shouldUpdate = shouldUpdateMainSolverScope(newGlobalBestScore);

    if (shouldUpdate) {
      var clonedSolution = updateMainSolverScope(newGlobalBest, newGlobalBestScore);
      fireBestSolutionChangedEvent(clonedSolution);

      lastKnownBestSolution = clonedSolution;
      lastKnownBestScore = newGlobalBestScore;
    }
  }

  private boolean shouldUpdateMainSolverScope(Score<?> newGlobalBestScore) {
    if (lastKnownBestScore == null) {
      return true; // First update
    }

    @SuppressWarnings("unchecked")
    var comparisonResult = ((Score) newGlobalBestScore).compareTo((Score) lastKnownBestScore);
    return comparisonResult > 0;
  }

  private Solution_ updateMainSolverScope(Solution_ newBestSolution, Score<?> newBestScore) {
    // Clone to avoid sharing references with island agents
    var clonedSolution = mainSolverScope.getScoreDirector().cloneSolution(newBestSolution);

    // Update main solver scope
    mainSolverScope.setBestSolution(clonedSolution);

    @SuppressWarnings("unchecked")
    var innerScore = InnerScore.fullyAssigned((Score) newBestScore);
    mainSolverScope.setBestScore(innerScore);

    mainSolverScope.setBestSolutionTimeMillis(mainSolverScope.getClock().millis());
    return clonedSolution;
  }

  private void fireBestSolutionChangedEvent(Solution_ newBestSolution) {
    solverEventSupport.fireBestSolutionChanged(mainSolverScope, eventProducerId, newBestSolution);
  }
}
