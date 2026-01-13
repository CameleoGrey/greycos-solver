package ai.greycos.solver.core.testcotwin.mixed.singleentity;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMixedValue extends TestdataObject {

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataMixedEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataMixedValue previousElement;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataMixedValue nextElement;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  @ShadowVariable(
      variableListenerClass = TestdataMixedVariableListener.class,
      sourceVariableName = "index")
  private Integer shadowVariableListenerValue;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCascadingShadowValue")
  private Integer cascadingShadowVariableValue;

  @ShadowVariable(supplierName = "updateDeclarativeShadowValue")
  private Integer declarativeShadowVariableValue;

  public TestdataMixedValue() {
    // Required for cloner
  }

  public TestdataMixedValue(String code) {
    super(code);
  }

  public TestdataMixedEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataMixedEntity entity) {
    this.entity = entity;
  }

  public TestdataMixedValue getPreviousElement() {
    return previousElement;
  }

  public void setPreviousElement(TestdataMixedValue previousElement) {
    this.previousElement = previousElement;
  }

  public TestdataMixedValue getNextElement() {
    return nextElement;
  }

  public void setNextElement(TestdataMixedValue nextElement) {
    this.nextElement = nextElement;
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
  }

  public Integer getShadowVariableListenerValue() {
    return shadowVariableListenerValue;
  }

  public void setShadowVariableListenerValue(Integer shadowVariableListenerValue) {
    this.shadowVariableListenerValue = shadowVariableListenerValue;
  }

  public Integer getCascadingShadowVariableValue() {
    return cascadingShadowVariableValue;
  }

  public void setCascadingShadowVariableValue(Integer cascadingShadowVariableValue) {
    this.cascadingShadowVariableValue = cascadingShadowVariableValue;
  }

  public Integer getDeclarativeShadowVariableValue() {
    return declarativeShadowVariableValue;
  }

  public void setDeclarativeShadowVariableValue(Integer declarativeShadowVariableValue) {
    this.declarativeShadowVariableValue = declarativeShadowVariableValue;
  }

  public void updateCascadingShadowValue() {
    if (index != null) {
      this.cascadingShadowVariableValue = index + 1;
    } else {
      this.cascadingShadowVariableValue = null;
    }
  }

  @ShadowSources("index")
  public Integer updateDeclarativeShadowValue() {
    if (index != null) {
      return index + 2;
    }
    return null;
  }
}
