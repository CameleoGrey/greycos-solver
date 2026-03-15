package ai.greycos.solver.quarkus.testcotwin.gizmo;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class PrivateNoArgsConstructorEntity {
  @PlanningId final String id;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String value;

  private PrivateNoArgsConstructorEntity() {
    id = null;
  }

  public PrivateNoArgsConstructorEntity(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
