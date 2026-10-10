package greycos.solver.core.impl.move;

import java.util.function.BiConsumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;

/**
 * A candidate whose evaluation produces an immutable move for later replay. Realized moves must
 * remain replayable after the request's evaluation context has been released.
 */
public interface PreparableMove<Solution_> extends Move<Solution_> {

  <Score_ extends Score<Score_>> PreparedMoveEvaluation<Solution_, Score_> prepare(
      InnerScoreDirector<Solution_, Score_> director,
      Runnable checkTermination,
      boolean assertFromScratch,
      BiConsumer<SolutionView<Solution_>, Move<Solution_>> finalStateConsumer);

  /** Identifies shared evaluation state; separate requests may share one cleanup registration. */
  default Object cleanupKey() {
    return this;
  }

  /**
   * Releases this director's preparation state at an acknowledged evaluation boundary. Cleanup may
   * occur between episodes or perturbation sequences without ending the owning selector's phase. A
   * later {@link #prepare} must lazily reacquire any required state; candidate behavior must not
   * depend on retaining that state or on initialization/cleanup callback history.
   */
  default void closeEvaluationContext(InnerScoreDirector<Solution_, ?> director) {}
}
