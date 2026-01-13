package ai.greycos.solver.quarkus.jsonb.it.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class ITestdataPlanningEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

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
