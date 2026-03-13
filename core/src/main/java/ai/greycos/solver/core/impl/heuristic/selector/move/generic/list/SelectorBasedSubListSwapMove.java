package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedSubListSwapMove<Solution_> extends SubListSwapMove<Solution_> {

  public SelectorBasedSubListSwapMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object leftEntity,
      int leftFromIndex,
      int leftToIndex,
      Object rightEntity,
      int rightFromIndex,
      int rightToIndex,
      boolean reversing) {
    super(
        variableDescriptor,
        leftEntity,
        leftFromIndex,
        leftToIndex,
        rightEntity,
        rightFromIndex,
        rightToIndex,
        reversing);
  }

  public SelectorBasedSubListSwapMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      SubList leftSubList,
      SubList rightSubList,
      boolean reversing) {
    super(variableDescriptor, leftSubList, rightSubList, reversing);
  }
}
