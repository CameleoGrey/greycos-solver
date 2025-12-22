package ai.greycos.solver.benchmark.quarkus.testdomain.normal.domain;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class TestdataQuarkusEntity {

  private String value;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
