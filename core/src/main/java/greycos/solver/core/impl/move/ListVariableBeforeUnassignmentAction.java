package greycos.solver.core.impl.move;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

record ListVariableBeforeUnassignmentAction<Solution_>(
    Object element, ListVariableDescriptor<Solution_> variableDescriptor)
    implements ChangeAction<Solution_> {

  @Override
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    scoreDirector.afterListVariableElementAssigned(variableDescriptor, element);
  }

  @Override
  public ChangeAction<Solution_> rebase(Lookup lookup) {
    return new ListVariableBeforeUnassignmentAction<>(
        lookup.lookUpWorkingObject(element), variableDescriptor);
  }
}
