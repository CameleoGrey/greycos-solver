package ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.classes.childnot;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class TestdataChildNotAnnotatedBaseEntity {

  @PlanningId private Long id;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  public TestdataChildNotAnnotatedBaseEntity() {}

  public TestdataChildNotAnnotatedBaseEntity(long id) {
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
