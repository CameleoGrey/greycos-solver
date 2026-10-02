package greycos.solver.core.impl.move;

import java.util.function.BiConsumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;

/** A candidate whose evaluation produces an immutable move for later replay. */
public interface PreparableMove<Solution_> extends Move<Solution_> {

  <Score_ extends Score<Score_>> PreparedMoveEvaluation<Solution_, Score_> prepare(
      InnerScoreDirector<Solution_, Score_> director,
      Runnable checkTermination,
      boolean assertFromScratch,
      BiConsumer<SolutionView<Solution_>, Move<Solution_>> finalStateConsumer);

  default Object cleanupKey() {
    return this;
  }

  default void closeEvaluationContext(InnerScoreDirector<Solution_, ?> director) {}
}
