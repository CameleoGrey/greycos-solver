package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class SelectorBasedListAssignMove<Solution_> extends ListAssignMove<Solution_> {

  public SelectorBasedListAssignMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      @Nullable Object planningValue,
      Object destinationEntity,
      int destinationIndex) {
    super(variableDescriptor, planningValue, destinationEntity, destinationIndex);
  }
}
