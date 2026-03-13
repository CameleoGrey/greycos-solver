package ai.greycos.solver.core.impl.move;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

record ListVariableBeforeChangeAction<Solution_, Entity_, Value_>(
    Entity_ entity,
    List<Value_> oldValue,
    int fromIndex,
    int toIndex,
    ListVariableDescriptor<Solution_> variableDescriptor)
    implements ChangeAction<Solution_> {

  @Override
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    variableDescriptor.getValue(entity).addAll(fromIndex, oldValue);
    scoreDirector.afterListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
  }

  @Override
  public ChangeAction<Solution_> rebase(Lookup lookup) {
    var rebasedValueList = oldValue().stream().map(lookup::lookUpWorkingObject).toList();
    return new ListVariableBeforeChangeAction<>(
        lookup.lookUpWorkingObject(entity),
        rebasedValueList,
        fromIndex,
        toIndex,
        variableDescriptor);
  }
}
