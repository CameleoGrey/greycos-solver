package greycos.solver.core.impl.heuristic.selector.move.factory;

import java.util.Iterator;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.preview.api.move.Move;

/**
 * A simple interface to generate a {@link List} of custom {@link Move}s.
 *
 * <p>For a more powerful version, see {@link MoveIteratorFactory}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public interface MoveListFactory<Solution_> {

  /**
   * When it is called depends on the configured {@link SelectionCacheType}.
   *
   * <p>It can never support {@link SelectionCacheType#JUST_IN_TIME}, because it returns a {@link
   * List}, not an {@link Iterator}.
   *
   * @param solution never null, the {@link PlanningSolution} of which the {@link Move}s need to be
   *     generated
   * @return never null
   */
  List<? extends Move<Solution_>> createMoveList(Solution_ solution);
}
