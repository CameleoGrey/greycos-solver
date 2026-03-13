package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListUnassignMove<Solution_> extends ListUnassignMove<Solution_> {

  public SelectorBasedListUnassignMove(
      ListVariableDescriptor<Solution_> variableDescriptor, Object sourceEntity, int sourceIndex) {
    super(variableDescriptor, sourceEntity, sourceIndex);
  }
}
