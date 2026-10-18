package greycos.solver.core.testcotwin.shadow.sibling;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public abstract class AbstractTestdataDeclarativeSiblingValue extends TestdataObject {
  AbstractTestdataDeclarativeSiblingValue previous;

  protected AbstractTestdataDeclarativeSiblingValue() {
    super();
  }

  protected AbstractTestdataDeclarativeSiblingValue(String code) {
    super(code);
  }

  @PreviousElementShadowVariable(sourceVariableName = "values")
  public AbstractTestdataDeclarativeSiblingValue getPrevious() {
    return previous;
  }

  public void setPrevious(AbstractTestdataDeclarativeSiblingValue previous) {
    this.previous = previous;
  }
}
