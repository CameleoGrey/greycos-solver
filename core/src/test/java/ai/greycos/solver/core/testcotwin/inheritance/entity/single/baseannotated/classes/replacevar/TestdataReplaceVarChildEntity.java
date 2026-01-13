package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.replacevar;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataReplaceVarChildEntity extends TestdataReplaceVarBaseEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  public TestdataReplaceVarChildEntity() {}

  public TestdataReplaceVarChildEntity(long id) {
    super(id);
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public void setValue(String value) {
    this.value = value;
  }
}
