package ai.greycos.solver.spring.boot.autoconfigure.dummy.normal.noSolution;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class DummyTestdataSpringEntity {

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
