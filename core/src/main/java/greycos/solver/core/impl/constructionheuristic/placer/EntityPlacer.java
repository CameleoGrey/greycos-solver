package greycos.solver.core.impl.constructionheuristic.placer;

import java.util.List;

import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;

public interface EntityPlacer<Solution_>
    extends Iterable<Placement<Solution_>>, PhaseLifecycleListener<Solution_> {

  EntityPlacer<Solution_> rebuildWithFilter(SelectionFilter<Solution_, Object> filter);

  EntityPlacer<Solution_> copy();

  default List<MoveSelector<Solution_>> getCandidateMoveSelectors() {
    return List.of();
  }
}
