package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtooabstract;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataBothAnnotatedAbstractBaseEntity {

  @PlanningId private Long id;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  public TestdataBothAnnotatedAbstractBaseEntity() {}

  public TestdataBothAnnotatedAbstractBaseEntity(long id) {
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
