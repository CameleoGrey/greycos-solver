package greycos.solver.core.impl.exhaustivesearch;

import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.exhaustivesearch.decider.JointVariableExhaustiveSearchDecider.DecisionFilter;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.preview.api.move.Move;

/**
 * Filters each leaf before Cartesian composition, so completed coordinates become empty children.
 */
final class JointCoordinateMoveSelector<Solution_> extends AbstractMoveSelector<Solution_> {

  private final MoveSelector<Solution_> child;
  private final GenuineVariableDescriptor<Solution_> descriptor;
  private final DecisionFilter<Solution_> filter;

  JointCoordinateMoveSelector(
      MoveSelector<Solution_> child,
      GenuineVariableDescriptor<Solution_> descriptor,
      DecisionFilter<Solution_> filter) {
    if (child.isNeverEnding()) {
      throw new IllegalArgumentException(
          ("The joint exhaustiveSearch coordinate (%s) has a never-ending child move selector (%s). "
                  + "Use finite ORIGINAL, SORTED or SHUFFLED selection at every level; selectedCountLimit cannot make an endless inner selection exhaustive.")
              .formatted(descriptor, child));
    }
    this.child = child;
    this.descriptor = descriptor;
    this.filter = filter;
    phaseLifecycleSupport.addEventListener(child);
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
  public Iterator<Move<Solution_>> iterator() {
    var iterator = child.iterator();
    return new UpcomingSelectionIterator<>() {
      private boolean triedUnassignment;

      @Override
      protected Move<Solution_> createUpcomingSelection() {
        while (iterator.hasNext()) {
          var move = iterator.next();
          if (move instanceof SelectorBasedNoChangeMove<Solution_>) {
            continue;
          }
          var selected = filter.select(descriptor, move);
          if (selected != null) {
            return selected;
          }
        }
        if (!triedUnassignment) {
          triedUnassignment = true;
          var skip = filter.select(descriptor, SelectorBasedNoChangeMove.getInstance());
          if (skip != null) {
            return skip;
          }
        }
        return noUpcomingSelection();
      }
    };
  }
}
