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
 * <p>Updates main solver during solving, fires events, and enables termination criteria.
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

  public void start() {
    globalState.addObserver(this);
  }

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

    boolean shouldUpdate = shouldUpdateMainSolverScope(newGlobalBestScore);

    if (shouldUpdate) {
      updateMainSolverScope(newGlobalBest, newGlobalBestScore);
      fireBestSolutionChangedEvent(newGlobalBest);

      lastKnownBestSolution = newGlobalBest;
      lastKnownBestScore = newGlobalBestScore;
    }
  }

  private boolean shouldUpdateMainSolverScope(Score<?> newGlobalBestScore) {
    if (lastKnownBestScore == null) {
      return true;
    }

    var comparisonResult = ((Score) newGlobalBestScore).compareTo((Score) lastKnownBestScore);
    return comparisonResult > 0;
  }

  private void updateMainSolverScope(Solution_ newBestSolution, Score<?> newBestScore) {
    var clonedSolution = mainSolverScope.getScoreDirector().cloneSolution(newBestSolution);

    mainSolverScope.setBestSolution(clonedSolution);

    var innerScore = InnerScore.fullyAssigned((Score) newBestScore);
    mainSolverScope.setBestScore(innerScore);

    mainSolverScope.setBestSolutionTimeMillis(mainSolverScope.getClock().millis());
  }

  private void fireBestSolutionChangedEvent(Solution_ newBestSolution) {
    solverEventSupport.fireBestSolutionChanged(mainSolverScope, eventProducerId, newBestSolution);
  }
}
