package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Iterator;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.list.DestinationSelector;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.list.SubListSelector;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;
import greycos.solver.core.preview.api.move.Move;

class RandomSubListChangeMoveIterator<Solution_>
    extends UpcomingSelectionIterator<Move<Solution_>> {

  private final Iterator<SubList> subListIterator;
  private final DestinationSelector<Solution_> destinationSelector;
  private final boolean originDependentDestinationSelection;
  private Iterator<ElementPosition> destinationIterator;
  private final ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final RandomGenerator workingRandom;
  private final boolean selectReversingMoveToo;

  RandomSubListChangeMoveIterator(
      SubListSelector<Solution_> subListSelector,
      DestinationSelector<Solution_> destinationSelector,
      RandomGenerator workingRandom,
      boolean selectReversingMoveToo) {
    this(subListSelector, destinationSelector, workingRandom, selectReversingMoveToo, false);
  }

  RandomSubListChangeMoveIterator(
      SubListSelector<Solution_> subListSelector,
      DestinationSelector<Solution_> destinationSelector,
      RandomGenerator workingRandom,
      boolean selectReversingMoveToo,
      boolean originDependentDestinationSelection) {
    this.subListIterator = subListSelector.iterator();
    this.destinationSelector = destinationSelector;
    this.originDependentDestinationSelection =
        originDependentDestinationSelection && destinationSelector.getSize() > 0;
    this.destinationIterator = destinationSelector.iterator();
    this.listVariableDescriptor = subListSelector.getVariableDescriptor();
    this.workingRandom = workingRandom;
    this.selectReversingMoveToo = selectReversingMoveToo;
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    if (!subListIterator.hasNext()) {
      return noUpcomingSelection();
    }
    // The inner node may need the outer iterator to select the next value first
    var subList = subListIterator.next();
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
    var destination = findUnpinnedDestination(destinationIterator, listVariableDescriptor);
    if (destination == null) {
      return originDependentDestinationSelection
          ? SelectorBasedNoChangeMove.getInstance()
          : noUpcomingSelection();
    } else if (destination instanceof PositionInList destinationElement) {
      var reversing = selectReversingMoveToo && workingRandom.nextBoolean();
      return new SelectorBasedSubListChangeMove<>(
          listVariableDescriptor,
          subList,
          destinationElement.entity(),
          destinationElement.index(),
          reversing);
    } else {
      // TODO add SubListAssignMove
      return new SelectorBasedSubListUnassignMove<>(listVariableDescriptor, subList);
    }
  }
}
