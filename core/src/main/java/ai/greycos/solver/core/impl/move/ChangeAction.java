package ai.greycos.solver.core.impl.move;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

public sealed interface ChangeAction<Solution_>
    permits ListVariableAfterAssignmentAction,
        ListVariableAfterChangeAction,
        ListVariableAfterUnassignmentAction,
        ListVariableBeforeAssignmentAction,
        ListVariableBeforeChangeAction,
        ListVariableBeforeUnassignmentAction,
        TriggerVariableListenersAction,
        VariableChangeAction {

  void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector);

  ChangeAction<Solution_> rebase(Lookup lookup);
}
