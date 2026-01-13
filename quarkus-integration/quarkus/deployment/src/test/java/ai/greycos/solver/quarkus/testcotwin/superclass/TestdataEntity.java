package ai.greycos.solver.quarkus.testcotwin.superclass;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataEntity extends TestdataAbstractIdentifiable {

  private String value;

  public TestdataEntity() {}

  public TestdataEntity(long id) {
    super(id);
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
