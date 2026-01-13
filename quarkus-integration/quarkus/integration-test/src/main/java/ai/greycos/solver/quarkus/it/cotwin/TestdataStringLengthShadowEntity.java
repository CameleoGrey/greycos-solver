package ai.greycos.solver.quarkus.it.cotwin;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataStringLengthShadowEntity implements TestdataStringLengthShadowEntityInterface {

  private String value;

  private List<String> valueList;

  @ShadowVariable(
      variableListenerClass = StringLengthVariableListener.class,
      sourceEntityClass = TestdataStringLengthShadowEntity.class,
      sourceVariableName = "value")
  private Integer length;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public void setValue(String value) {
    this.value = value;
  }

  public Integer getLength() {
    return length;
  }

  public void setLength(Integer length) {
    this.length = length;
  }

  @Override
  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }
}
