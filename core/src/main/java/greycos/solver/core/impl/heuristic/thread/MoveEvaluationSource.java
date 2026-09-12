package greycos.solver.core.impl.heuristic.thread;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.preview.api.move.Move;

/**
 * An immutable, indexed source of framework-owned candidates against one working-state baseline.
 * The coordinator publishes ranges; each worker rebases the source before resolving its moves.
 * Neither method may invoke application callbacks or mutate the enclosing working state.
 */
public interface MoveEvaluationSource<Solution_> {

  int size();

  Move<Solution_> move(int index);

  /**
   * Returns a worker-confined source whose moves already refer to that worker's working objects.
   */
  MoveEvaluationSource<Solution_> rebase(Lookup lookup);
}
