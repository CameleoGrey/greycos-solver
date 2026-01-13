package ai.greycos.solver.core.testcotwin.inheritance.entity.single.basenot.classes;

import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

public class TestdataBaseNotAnnotatedBaseEntity {

  @PlanningId private Long id;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  public TestdataBaseNotAnnotatedBaseEntity() {}

  public TestdataBaseNotAnnotatedBaseEntity(long id) {
    this.id = id;
  }

  public Long getId() {
    return id;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
