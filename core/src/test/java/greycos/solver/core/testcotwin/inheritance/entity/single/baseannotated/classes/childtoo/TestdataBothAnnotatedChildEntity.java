package greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.childtoo;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataBothAnnotatedChildEntity extends TestdataEntity {

  @PlanningVariable(valueRangeProviderRefs = "subValueRange")
  private TestdataValue subValue;

  public TestdataBothAnnotatedChildEntity() {}

  public TestdataBothAnnotatedChildEntity(String code) {
    super(code);
  }

  public TestdataValue getSubValue() {
    return subValue;
  }

  public void setSubValue(TestdataValue subValue) {
    this.subValue = subValue;
  }
}
