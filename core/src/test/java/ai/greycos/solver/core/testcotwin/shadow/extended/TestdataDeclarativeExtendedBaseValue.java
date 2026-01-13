package ai.greycos.solver.core.testcotwin.shadow.extended;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataDeclarativeExtendedBaseValue extends TestdataObject {
  TestdataDeclarativeExtendedBaseValue previous;

  public TestdataDeclarativeExtendedBaseValue() {
    super();
  }

  public TestdataDeclarativeExtendedBaseValue(String code) {
    super(code);
  }

  @PreviousElementShadowVariable(sourceVariableName = "values")
  public TestdataDeclarativeExtendedBaseValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataDeclarativeExtendedBaseValue previous) {
    this.previous = previous;
  }
}
