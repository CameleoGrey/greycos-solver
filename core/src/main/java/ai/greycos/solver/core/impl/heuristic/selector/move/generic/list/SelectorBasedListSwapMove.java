package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListSwapMove<Solution_> extends ListSwapMove<Solution_> {

  public SelectorBasedListSwapMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object leftEntity,
      int leftIndex,
      Object rightEntity,
      int rightIndex) {
    super(variableDescriptor, leftEntity, leftIndex, rightEntity, rightIndex);
  }
}
