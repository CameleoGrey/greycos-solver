package ai.greycos.solver.core.impl.heuristic.move;

import java.util.Iterator;

import ai.greycos.solver.core.preview.api.move.Move;

record NewIteratorAdapter<Solution_>(Iterator<Move<Solution_>> moveIterator)
    implements Iterator<ai.greycos.solver.core.impl.heuristic.move.Move<Solution_>> {

  @Override
  public boolean hasNext() {
    return moveIterator.hasNext();
  }

  @Override
  public ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> next() {
    return MoveAdapters.toLegacyMove(moveIterator.next());
  }
}
