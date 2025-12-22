package ai.greycos.solver.core.testdomain.inheritance.entity.single.basenot.classes;

import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

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
