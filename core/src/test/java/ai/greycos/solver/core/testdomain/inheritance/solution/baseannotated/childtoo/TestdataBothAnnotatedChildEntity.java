package ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.childtoo;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
