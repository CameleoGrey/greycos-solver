package ai.greycos.solver.core.testcotwin.shadow.method_variables;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeMethodVariablesBaseValue extends TestdataObject {
  TestdataDeclarativeMethodVariablesBaseValue previous;

  public TestdataDeclarativeMethodVariablesBaseValue() {
    super();
  }

  public TestdataDeclarativeMethodVariablesBaseValue(String code) {
    super(code);
  }

  @PreviousElementShadowVariable(sourceVariableName = "values")
  public TestdataDeclarativeMethodVariablesBaseValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataDeclarativeMethodVariablesBaseValue previous) {
    this.previous = previous;
  }
}
