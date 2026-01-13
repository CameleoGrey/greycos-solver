package ai.greycos.solver.core.impl.cotwin.variable.listener.support;

import ai.greycos.solver.core.impl.cotwin.variable.BasicVariableChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.InnerBasicVariableListener;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

final class VariableChangedNotification<Solution_> extends AbstractNotification
    implements BasicVariableNotification<Solution_> {

  VariableChangedNotification(Object entity) {
    super(entity);
  }

  @Override
  public void triggerBefore(
      InnerBasicVariableListener<Solution_, Object> variableListener,
      InnerScoreDirector<Solution_, ?> scoreDirector) {
    variableListener.beforeChange(scoreDirector, new BasicVariableChangeEvent<>(entity));
  }

  @Override
  public void triggerAfter(
      InnerBasicVariableListener<Solution_, Object> variableListener,
      InnerScoreDirector<Solution_, ?> scoreDirector) {
    variableListener.afterChange(scoreDirector, new BasicVariableChangeEvent<>(entity));
  }

  @Override
  public String toString() {
    return "VariableChanged(" + entity + ")";
  }
}
