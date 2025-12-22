package ai.greycos.solver.core.testdomain.shadow.extended;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
