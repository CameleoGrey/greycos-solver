package ai.greycos.solver.quarkus.it.reflection.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

@PlanningEntity
public class TestdataReflectionEntity {

  @PlanningVariable(valueRangeProviderRefs = "fieldValueRange")
  public String fieldValue;

  private String methodValueField;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  @PlanningVariable(valueRangeProviderRefs = "methodValueRange")
  public String getMethodValue() {
    return methodValueField;
  }

  public void setMethodValue(String methodValueField) {
    this.methodValueField = methodValueField;
  }
}
