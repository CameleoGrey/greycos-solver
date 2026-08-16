package greycos.solver.quarkus.jackson.it.testdata;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataJacksonPlanningEntity {

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
