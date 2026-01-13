package ai.greycos.solver.core.config.solver.testutil.corruptedundoshadow;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class CorruptedUndoShadowEntity {
  @PlanningId String id;

  @PlanningVariable CorruptedUndoShadowValue value;

  @ShadowVariable(
      sourceVariableName = "value",
      variableListenerClass = CorruptedUndoShadowVariableListener.class)
  CorruptedUndoShadowValue valueClone;

  public CorruptedUndoShadowEntity() {}

  public CorruptedUndoShadowEntity(String id) {
    this.id = id;
  }

  public CorruptedUndoShadowValue getValue() {
    return value;
  }

  public void setValue(CorruptedUndoShadowValue value) {
    this.value = value;
  }

  public CorruptedUndoShadowValue getValueClone() {
    return valueClone;
  }

  public void setValueClone(CorruptedUndoShadowValue valueClone) {
    this.valueClone = valueClone;
  }

  @Override
  public String toString() {
    return CorruptedUndoShadowEntity.class.getSimpleName();
  }
}
