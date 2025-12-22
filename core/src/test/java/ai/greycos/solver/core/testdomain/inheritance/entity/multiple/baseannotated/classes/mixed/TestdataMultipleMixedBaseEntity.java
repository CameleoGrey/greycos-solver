package ai.greycos.solver.core.testdomain.inheritance.entity.multiple.baseannotated.classes.mixed;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class TestdataMultipleMixedBaseEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange2")
  private String value2;

  public String getValue2() {
    return value2;
  }

  public void setValue2(String value2) {
    this.value2 = value2;
  }
}
