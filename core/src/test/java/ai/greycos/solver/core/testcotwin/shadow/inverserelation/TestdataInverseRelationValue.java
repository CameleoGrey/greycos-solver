package ai.greycos.solver.core.testcotwin.shadow.inverserelation;

import java.util.ArrayList;
import java.util.Collection;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInverseRelationValue extends TestdataObject {

  private Collection<TestdataInverseRelationEntity> entities = new ArrayList<>();

  public TestdataInverseRelationValue() {}

  public TestdataInverseRelationValue(String code) {
    super(code);
  }

  @InverseRelationShadowVariable(sourceVariableName = "value")
  public Collection<TestdataInverseRelationEntity> getEntities() {
    return entities;
  }

  public void setEntities(Collection<TestdataInverseRelationEntity> entities) {
    this.entities = entities;
  }
}
