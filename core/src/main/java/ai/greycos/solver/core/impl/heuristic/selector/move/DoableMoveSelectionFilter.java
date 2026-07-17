package ai.greycos.solver.core.impl.heuristic.selector.move;

import ai.greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.preview.api.move.Move;

final class DoableMoveSelectionFilter<Solution_>
    implements SelectionFilter<Solution_, Move<Solution_>> {

  static final SelectionFilter INSTANCE = new DoableMoveSelectionFilter<>();

  @Override
  public boolean accept(ScoreDirector<Solution_> scoreDirector, Move<Solution_> move) {
    if (move instanceof AbstractSelectorBasedMove<Solution_> selectorBasedMove) {
      return selectorBasedMove.isMoveDoable(scoreDirector);
    }
    return true;
  }

  private DoableMoveSelectionFilter() {}

  @Override
  public String toString() {
    return "Doable moves only";
  }
}
