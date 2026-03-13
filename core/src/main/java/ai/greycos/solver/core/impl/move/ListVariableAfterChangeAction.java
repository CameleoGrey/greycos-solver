package ai.greycos.solver.core.impl.move;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

record ListVariableAfterChangeAction<Solution_, Entity_, Value_>(
    Entity_ entity,
    int fromIndex,
    int toIndex,
    ListVariableDescriptor<Solution_> variableDescriptor)
    implements ChangeAction<Solution_> {

  @Override
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    scoreDirector.beforeListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
    @SuppressWarnings("unchecked")
    var items = (List<Value_>) variableDescriptor.getValue(entity).subList(fromIndex, toIndex);
    items.clear();
  }

  @Override
  public ChangeAction<Solution_> rebase(Lookup lookup) {
    return new ListVariableAfterChangeAction<>(
        lookup.lookUpWorkingObject(entity), fromIndex, toIndex, variableDescriptor);
  }
}
