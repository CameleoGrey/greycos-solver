package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Collections;
import java.util.Iterator;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.KnownExhaustionIterator;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptContext;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.preview.api.cotwin.metamodel.UnassignedElement;
import greycos.solver.core.preview.api.move.Move;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class OriginalListSwapIterator<Solution_>
    extends UpcomingSelectionIterator<Move<Solution_>> {

  private final ListVariableState<Solution_, Object, Object> listVariableState;
  private final Iterator<Object> leftValueIterator;
  private final IterableValueSelector<Solution_> rightValueSelector;
  private Iterator<Object> rightValueIterator;
  private Object upcomingLeftValue;

  public OriginalListSwapIterator(
      ListVariableState<Solution_, Object, Object> listVariableState,
      IterableValueSelector<Solution_> leftValueSelector,
      IterableValueSelector<Solution_> rightValueSelector) {
    this.listVariableState = listVariableState;
    this.leftValueIterator = leftValueSelector.iterator();
    this.rightValueSelector = rightValueSelector;
    this.rightValueIterator = Collections.emptyIterator();
  }

  @Override
  public boolean isKnownExhausted() {
    return super.isKnownExhausted()
        || (!upcomingCreated
            && KnownExhaustionIterator.isExhausted(leftValueIterator)
            && KnownExhaustionIterator.isExhausted(rightValueIterator));
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    boolean selectedWithoutProposal = false;
    while (!rightValueIterator.hasNext()) {
      if (selectedWithoutProposal) {
        SelectionAttemptContext.failedSelection();
      }
      if (!leftValueIterator.hasNext()) {
        return noUpcomingSelection();
      }
      upcomingLeftValue = leftValueIterator.next();
      rightValueIterator = rightValueSelector.iterator();
      selectedWithoutProposal = true;
    }

    var upcomingRightValue = rightValueIterator.next();
    return buildSwapMove(listVariableState, upcomingLeftValue, upcomingRightValue);
  }

  static <Solution_> Move<Solution_> buildSwapMove(
      ListVariableState<Solution_, Object, Object> listVariableState,
      Object upcomingLeftValue,
      Object upcomingRightValue) {
    if (upcomingLeftValue == upcomingRightValue) {
      return SelectorBasedNoChangeMove.getInstance();
    }
    var listVariableDescriptor = listVariableState.getSourceVariableDescriptor();
    var upcomingLeft = listVariableState.getElementPosition(upcomingLeftValue);
    var upcomingRight = listVariableState.getElementPosition(upcomingRightValue);
    var leftUnassigned = upcomingLeft instanceof UnassignedElement;
    var rightUnassigned = upcomingRight instanceof UnassignedElement;
    if (leftUnassigned && rightUnassigned) { // No need to swap two unassigned elements.
      return SelectorBasedNoChangeMove.getInstance();
    } else if (leftUnassigned) { // Unassign right, put left where right used to be.
      var rightDestination = upcomingRight.ensureAssigned();
      var unassignMove =
          new SelectorBasedListUnassignMove<>(
              listVariableDescriptor, rightDestination.entity(), rightDestination.index());
      var assignMove =
          new SelectorBasedListAssignMove<>(
              listVariableDescriptor,
              upcomingLeftValue,
              rightDestination.entity(),
              rightDestination.index());
      return SelectorBasedCompositeMove.buildMove(unassignMove, assignMove);
    } else if (rightUnassigned) { // Unassign left, put right where left used to be.
      var leftDestination = upcomingLeft.ensureAssigned();
      var unassignMove =
          new SelectorBasedListUnassignMove<>(
              listVariableDescriptor, leftDestination.entity(), leftDestination.index());
      var assignMove =
          new SelectorBasedListAssignMove<>(
              listVariableDescriptor,
              upcomingRightValue,
              leftDestination.entity(),
              leftDestination.index());
      return SelectorBasedCompositeMove.buildMove(unassignMove, assignMove);
    } else {
      var leftDestination = upcomingLeft.ensureAssigned();
      var rightDestination = upcomingRight.ensureAssigned();
      return new SelectorBasedListSwapMove<>(
          listVariableDescriptor,
          leftDestination.entity(),
          leftDestination.index(),
          rightDestination.entity(),
          rightDestination.index());
    }
  }
}
