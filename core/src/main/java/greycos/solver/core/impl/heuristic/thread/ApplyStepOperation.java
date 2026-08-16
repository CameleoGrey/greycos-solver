package greycos.solver.core.impl.heuristic.thread;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.preview.api.move.Move;

/**
 * Operation to apply a step change across all move threads. This operation contains the step move
 * and its resulting score.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 */
public class ApplyStepOperation<Solution_, Score_ extends Score<Score_>>
    extends MoveThreadOperation<Solution_> {

  private final int stepIndex;
  private final Move<Solution_> step;
  private final Score_ score;

  public ApplyStepOperation(int stepIndex, Move<Solution_> step, Score_ score) {
    this.stepIndex = stepIndex;
    this.step = step;
    this.score = score;
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public Move<Solution_> getStep() {
    return step;
  }

  public Score_ getScore() {
    return score;
  }
}
