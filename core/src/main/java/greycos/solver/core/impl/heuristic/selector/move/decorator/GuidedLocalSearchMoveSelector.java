package greycos.solver.core.impl.heuristic.selector.move.decorator;

import java.util.Iterator;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;
import greycos.solver.core.preview.api.move.Move;

/** Captures candidate provenance before a union can prefetch another origin. */
public final class GuidedLocalSearchMoveSelector<Solution_>
    extends AbstractMoveSelector<Solution_> {

  private final MoveSelector<Solution_> child;
  private final GuidedLocalSearchSelectionContext<Solution_> context;

  public GuidedLocalSearchMoveSelector(
      MoveSelector<Solution_> child, GuidedLocalSearchSelectionContext<Solution_> context) {
    this.child = child;
    this.context = context;
    phaseLifecycleSupport.addEventListener(child);
  }

  @Override
  public SelectionCacheType getCacheType() {
    return child.getCacheType();
  }

  @Override
  public boolean isNeverEnding() {
    return child.isNeverEnding();
  }

  @Override
  public long getSize() {
    return child.getSize();
  }

  @Override
  public boolean supportsPhaseAndSolverCaching() {
    return child.supportsPhaseAndSolverCaching();
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    var iterator = child.iterator();
    return new UpcomingSelectionIterator<>() {
      private boolean precedingOriginOrdinary;

      @Override
      protected Move<Solution_> createUpcomingSelection() {
        // ORIGINAL selectors may produce several destinations for one origin. Restore this
        // leaf's preceding origin before advancing; another union child may have run meanwhile.
        context.restoreLastOrigin(precedingOriginOrdinary);
        if (!iterator.hasNext()) {
          return noUpcomingSelection();
        }
        var move = iterator.next();
        precedingOriginOrdinary = context.wasLastOriginOrdinary();
        if (move instanceof AbstractSelectorBasedMove<?> selectorMove
            && !(move instanceof SelectorBasedNoChangeMove<?>)) {
          selectorMove.setGuidedLocalSearchOrdinaryOrigin(precedingOriginOrdinary);
        }
        return move;
      }
    };
  }
}
