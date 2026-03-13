package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedSubListUnassignMove<Solution_> extends SubListUnassignMove<Solution_> {

  public SelectorBasedSubListUnassignMove(
      ListVariableDescriptor<Solution_> variableDescriptor, SubList subList) {
    super(variableDescriptor, subList);
  }
}
