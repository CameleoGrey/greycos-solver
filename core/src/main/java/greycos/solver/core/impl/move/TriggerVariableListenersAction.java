package greycos.solver.core.impl.move;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

public final class TriggerVariableListenersAction<Solution_> implements ChangeAction<Solution_> {
  @Override
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    scoreDirector.triggerVariableListeners();
  }

  @Override
  public TriggerVariableListenersAction<Solution_> rebase(Lookup lookup) {
    return this;
  }
}
