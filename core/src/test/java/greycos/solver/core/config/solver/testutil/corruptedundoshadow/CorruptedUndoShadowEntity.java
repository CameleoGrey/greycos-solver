package greycos.solver.core.config.solver.testutil.corruptedundoshadow;

import java.util.Objects;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class CorruptedUndoShadowEntity {
  @PlanningId String id;

  @PlanningVariable CorruptedUndoShadowValue value;

  @ShadowVariable(supplierName = "updateValueClone")
  CorruptedUndoShadowValue valueClone;

  public CorruptedUndoShadowEntity() {}

  public CorruptedUndoShadowEntity(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  @ShadowSources("value")
  public CorruptedUndoShadowValue updateValueClone() {
    if (valueClone == null || !Objects.equals("v1", value.value)) {
      return value;
    }
    return valueClone;
  }

  @Override
  public String toString() {
    return CorruptedUndoShadowEntity.class.getSimpleName();
  }
}
