package ai.greycos.solver.core.impl.heuristic.thread;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * Operation to set up a move thread with a score director. This operation initializes the move
 * thread with a child thread score director.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
public class SetupOperation<Solution_, Score_ extends Score<Score_>>
    extends MoveThreadOperation<Solution_> {

  private final InnerScoreDirector<Solution_, Score_> scoreDirector;

  public SetupOperation(InnerScoreDirector<Solution_, Score_> scoreDirector) {
    this.scoreDirector = scoreDirector;
  }

  public InnerScoreDirector<Solution_, Score_> getScoreDirector() {
    return scoreDirector;
  }
}
