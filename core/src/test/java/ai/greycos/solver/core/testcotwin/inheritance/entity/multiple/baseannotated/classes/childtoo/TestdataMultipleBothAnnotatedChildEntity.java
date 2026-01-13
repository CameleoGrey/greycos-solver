package ai.greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.childtoo;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataMultipleBothAnnotatedChildEntity
    extends TestdataMultipleBothAnnotatedSecondChildEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange2")
  private String value2;

  public TestdataMultipleBothAnnotatedChildEntity() {}

  public TestdataMultipleBothAnnotatedChildEntity(long id) {
    super(id);
  }

  public String getValue2() {
    return value2;
  }

  public void setValue2(String value2) {
    this.value2 = value2;
  }
}
