package ai.greycos.solver.quarkus.testdomain.invalid.inverserelation;

import java.util.List;

import ai.greycos.solver.core.api.domain.variable.InverseRelationShadowVariable;

public class TestdataInvalidInverseRelationValue {
  @InverseRelationShadowVariable(sourceVariableName = "value")
  private List<TestdataInvalidInverseRelationEntity> entityList;

  public List<TestdataInvalidInverseRelationEntity> getEntityList() {
    return entityList;
  }
}
