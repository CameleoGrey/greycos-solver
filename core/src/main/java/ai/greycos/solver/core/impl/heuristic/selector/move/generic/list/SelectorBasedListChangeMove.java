package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListChangeMove<Solution_> extends ListChangeMove<Solution_> {

  public SelectorBasedListChangeMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object sourceEntity,
      int sourceIndex,
      Object destinationEntity,
      int destinationIndex) {
    super(variableDescriptor, sourceEntity, sourceIndex, destinationEntity, destinationIndex);
  }
}
