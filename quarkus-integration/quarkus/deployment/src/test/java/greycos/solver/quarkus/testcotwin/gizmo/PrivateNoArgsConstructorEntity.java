package greycos.solver.quarkus.testcotwin.gizmo;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class PrivateNoArgsConstructorEntity {
  @PlanningId private final String id;

  private String rawState;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  String value;

  private PrivateNoArgsConstructorEntity() {
    id = null;
    rawState = "No-argument constructor";
  }

  public PrivateNoArgsConstructorEntity(String id) {
    this.id = id;
    rawState = "Raw state (" + id + ")";
  }

  public String getId() {
    return id;
  }

  public String testReadRawState() {
    return rawState;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
