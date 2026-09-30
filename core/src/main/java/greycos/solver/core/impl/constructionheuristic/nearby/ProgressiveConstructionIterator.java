package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.Iterator;

import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.preview.api.move.Move;

/**
 * A placement cursor whose current batch is finite. The decider must consume all score feedback
 * before explicitly opening the next batch; {@link #hasNext()} never opens a batch.
 */
public interface ProgressiveConstructionIterator<Solution_>
    extends Iterator<Move<Solution_>>, AutoCloseable {

  void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope);

  /** Returns true if another batch was opened, after the previous batch was consumed. */
  boolean advanceBatch();

  /** Returns the number of geographic prefix expansions in this placement. */
  default long getWideningCount() {
    return 0;
  }

  /** Releases scratch data for this placement, including when evaluation ends early. */
  @Override
  default void close() {}
}
