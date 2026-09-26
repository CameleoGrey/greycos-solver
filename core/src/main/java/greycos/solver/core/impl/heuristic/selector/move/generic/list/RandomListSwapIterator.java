package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.impl.heuristic.selector.move.generic.list.OriginalListSwapIterator.buildSwapMove;

import java.util.Iterator;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.preview.api.move.Move;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class RandomListSwapIterator<Solution_> extends UpcomingSelectionIterator<Move<Solution_>> {

  private final ListVariableState<Solution_, Object, Object> listVariableState;
  private final Iterator<Object> leftValueIterator;
  private final Iterator<Object> rightValueIterator;

  public RandomListSwapIterator(
      ListVariableState<Solution_, Object, Object> listVariableState,
      IterableValueSelector<Solution_> leftValueSelector,
      IterableValueSelector<Solution_> rightValueSelector) {
    this.listVariableState = listVariableState;
    this.leftValueIterator = leftValueSelector.iterator();
    this.rightValueIterator = rightValueSelector.iterator();
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    if (!leftValueIterator.hasNext()) {
      return noUpcomingSelection();
    }
    var upcomingLeftValue = leftValueIterator.next();
    // The right iterator may depend on a selected value from the left iterator
    if (!rightValueIterator.hasNext()) {
      return noUpcomingSelection();
    }
    var upcomingRightValue = rightValueIterator.next();
    return buildSwapMove(listVariableState, upcomingLeftValue, upcomingRightValue);
  }
}
