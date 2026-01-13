package ai.greycos.solver.quarkus.it.devui.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataStringLengthShadowEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  @ShadowVariable(
      variableListenerClass = StringLengthVariableListener.class,
      sourceEntityClass = TestdataStringLengthShadowEntity.class,
      sourceVariableName = "value")
  private Integer length;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Integer getLength() {
    return length;
  }

  public void setLength(Integer length) {
    this.length = length;
  }
}
