package ai.greycos.solver.core.impl.heuristic.thread;

import ai.greycos.solver.core.impl.heuristic.move.Move;

/**
 * Operation to evaluate a move in a move thread. This operation contains the move to be evaluated
 * and the indices for ordering.
 *
 * @param <Solution_> the solution type, the class with the {@link
 *     ai.greycos.solver.core.api.cotwin.solution.PlanningSolution} annotation
 */
public class MoveEvaluationOperation<Solution_> extends MoveThreadOperation<Solution_> {

  private final int stepIndex;
  private final int moveIndex;
  private final Move<Solution_> move;

  public MoveEvaluationOperation(int stepIndex, int moveIndex, Move<Solution_> move) {
    this.stepIndex = stepIndex;
    this.moveIndex = moveIndex;
    this.move = move;
  }

  public int getStepIndex() {
    return stepIndex;
  }

  public int getMoveIndex() {
    return moveIndex;
  }

  public Move<Solution_> getMove() {
    return move;
  }
}
