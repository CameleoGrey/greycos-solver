package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Iterator;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
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
  private final DestinationSelector<Solution_> destinationSelector;
  private final boolean originDependentDestinationSelection;
  private Iterator<ElementPosition> destinationIterator;

  public RandomListChangeIterator(
      ListVariableState<Solution_, Object, Object> listVariableState,
      IterableValueSelector<Solution_> valueSelector,
      DestinationSelector<Solution_> destinationSelector) {
    this(listVariableState, valueSelector, destinationSelector, false);
  }

  public RandomListChangeIterator(
      ListVariableState<Solution_, Object, Object> listVariableState,
      IterableValueSelector<Solution_> valueSelector,
      DestinationSelector<Solution_> destinationSelector,
      boolean originDependentDestinationSelection) {
    this.listVariableState = listVariableState;
    this.valueIterator = valueSelector.iterator();
    this.destinationSelector = destinationSelector;
    this.originDependentDestinationSelection =
        originDependentDestinationSelection && destinationSelector.getSize() > 0;
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
      if (originDependentDestinationSelection) {
        destinationIterator = destinationSelector.iterator();
        if (!destinationIterator.hasNext()) {
          return SelectorBasedNoChangeMove.getInstance();
        }
      } else {
        return noUpcomingSelection();
      }
    }
    var move =
        OriginalListChangeIterator.buildChangeMove(
            listVariableState, upcomingValue, destinationIterator);
    if (move == null) {
      return originDependentDestinationSelection
          ? SelectorBasedNoChangeMove.getInstance()
          : noUpcomingSelection();
    } else {
      return move;
    }
  }
}
