package greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.mixed;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

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
