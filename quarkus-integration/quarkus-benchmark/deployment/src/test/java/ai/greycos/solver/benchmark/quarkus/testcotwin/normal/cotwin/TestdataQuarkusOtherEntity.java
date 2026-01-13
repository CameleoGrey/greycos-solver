package ai.greycos.solver.benchmark.quarkus.testcotwin.normal.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataQuarkusOtherEntity {

  private String value;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
