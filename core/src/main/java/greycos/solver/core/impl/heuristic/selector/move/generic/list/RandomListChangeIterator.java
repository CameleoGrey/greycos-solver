package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Iterator;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.move.Move;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class RandomListChangeIterator<Solution_>
    extends UpcomingSelectionIterator<Move<Solution_>> {

  private final ListVariableState<Solution_, Object, Object> listVariableState;
  private final Iterator<Object> valueIterator;
  private final Iterator<ElementPosition> destinationIterator;

  public RandomListChangeIterator(
      ListVariableState<Solution_, Object, Object> listVariableState,
      IterableValueSelector<Solution_> valueSelector,
      DestinationSelector<Solution_> destinationSelector) {
    this.listVariableState = listVariableState;
    this.valueIterator = valueSelector.iterator();
    this.destinationIterator = destinationSelector.iterator();
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    if (!valueIterator.hasNext()) {
      return noUpcomingSelection();
    }
    // The destination may depend on selecting the value before checking if it has a next value
    var upcomingValue = valueIterator.next();
    if (!destinationIterator.hasNext()) {
      return noUpcomingSelection();
    }
    var move =
        OriginalListChangeIterator.buildChangeMove(
            listVariableState, upcomingValue, destinationIterator);
    if (move == null) {
      return noUpcomingSelection();
    } else {
      return move;
    }
  }
}
