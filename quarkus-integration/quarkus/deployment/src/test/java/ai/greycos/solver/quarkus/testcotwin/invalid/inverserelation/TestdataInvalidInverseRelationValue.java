package ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;

public class TestdataInvalidInverseRelationValue {
  @InverseRelationShadowVariable(sourceVariableName = "value")
  private List<TestdataInvalidInverseRelationEntity> entityList;

  public List<TestdataInvalidInverseRelationEntity> getEntityList() {
    return entityList;
  }
}
