package ai.greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar;

import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataUnassignedMixedOtherValue extends TestdataObject {

  private boolean blocked = false;

  public TestdataUnassignedMixedOtherValue() {
    // Required for cloner
  }

  public TestdataUnassignedMixedOtherValue(String code) {
    super(code);
  }

  public boolean isBlocked() {
    return blocked;
  }

  public void setBlocked(boolean blocked) {
    this.blocked = blocked;
  }
}
