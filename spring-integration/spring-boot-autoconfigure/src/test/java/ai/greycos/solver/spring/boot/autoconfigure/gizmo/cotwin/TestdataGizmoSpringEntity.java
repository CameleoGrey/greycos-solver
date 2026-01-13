package ai.greycos.solver.spring.boot.autoconfigure.gizmo.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataGizmoSpringEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public String value;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
