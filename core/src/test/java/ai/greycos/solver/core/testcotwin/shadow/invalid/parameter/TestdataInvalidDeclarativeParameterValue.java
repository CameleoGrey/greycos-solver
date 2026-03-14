package ai.greycos.solver.core.testcotwin.shadow.invalid.parameter;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariablesInconsistent;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInvalidDeclarativeParameterValue extends TestdataObject {
  TestdataInvalidDeclarativeParameterValue fact;

  List<TestdataInvalidDeclarativeParameterValue> group;

  @PreviousElementShadowVariable(sourceVariableName = "values")
  TestdataInvalidDeclarativeParameterValue previous;

  @ShadowVariable(supplierName = "shadowInvalidParameter")
  TestdataInvalidDeclarativeParameterValue invalidParameter;

  @ShadowVariablesInconsistent boolean inconsistent;

  public TestdataInvalidDeclarativeParameterValue() {}

  public TestdataInvalidDeclarativeParameterValue(String code) {
    super(code);
  }

  public TestdataInvalidDeclarativeParameterValue getFact() {
    return fact;
  }

  public void setFact(TestdataInvalidDeclarativeParameterValue fact) {
    this.fact = fact;
  }

  public List<TestdataInvalidDeclarativeParameterValue> getGroup() {
    return group;
  }

  public void setGroup(List<TestdataInvalidDeclarativeParameterValue> group) {
    this.group = group;
  }

  public TestdataInvalidDeclarativeParameterValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataInvalidDeclarativeParameterValue previous) {
    this.previous = previous;
  }

  public TestdataInvalidDeclarativeParameterValue getInvalidParameter() {
    return invalidParameter;
  }

  public void setInvalidParameter(TestdataInvalidDeclarativeParameterValue invalidParameter) {
    this.invalidParameter = invalidParameter;
  }

  public boolean isInconsistent() {
    return inconsistent;
  }

  public void setInconsistent(boolean inconsistent) {
    this.inconsistent = inconsistent;
  }

  @ShadowSources("previous")
  public TestdataInvalidDeclarativeParameterValue shadowInvalidParameter(Integer badParam) {
    return null;
  }
}
