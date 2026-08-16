package greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataAddVarChildEntity extends TestdataAddVarBaseEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange2")
  private String value2;

  public TestdataAddVarChildEntity() {}

  public TestdataAddVarChildEntity(long id) {
    super(id);
  }

  public String getValue2() {
    return value2;
  }

  public void setValue2(String value2) {
    this.value2 = value2;
  }
}
