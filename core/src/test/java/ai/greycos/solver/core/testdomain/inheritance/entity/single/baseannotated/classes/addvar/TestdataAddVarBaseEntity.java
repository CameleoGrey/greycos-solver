package ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.classes.addvar;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.lookup.PlanningId;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;

@PlanningEntity
public class TestdataAddVarBaseEntity {

  @PlanningId private Long id;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  public TestdataAddVarBaseEntity() {}

  public TestdataAddVarBaseEntity(long id) {
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
