package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.interfaces.replacevar;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataReplaceVarInterfaceChildEntity
    implements TestdataReplaceVarInterfaceBaseEntity {

  private Long id;
  private String value;

  public TestdataReplaceVarInterfaceChildEntity() {}

  public TestdataReplaceVarInterfaceChildEntity(long id) {
    this.id = id;
  }

  @Override
  public void setId(Long id) {
    this.id = id;
  }

  @Override
  public Long getId() {
    return id;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  @Override
  public String getValue() {
    return value;
  }

  @Override
  public void setValue(String value) {
    this.value = value;
  }
}
