package ai.greycos.solver.core.impl.move;

import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.preview.api.move.Rebaser;

public final class TriggerVariableListenersAction<Solution_> implements ChangeAction<Solution_> {
  @Override
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    scoreDirector.triggerVariableListeners();
  }

  @Override
  public TriggerVariableListenersAction<Solution_> rebase(Rebaser rebaser) {
    return this;
  }
}
