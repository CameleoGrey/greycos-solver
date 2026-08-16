package greycos.solver.core.testcotwin;

import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.testutil.CodeAssertable;

public class TestdataObject implements CodeAssertable {

  @PlanningId protected String code;

  public TestdataObject() {}

  public TestdataObject(String code) {
    this.code = code;
  }

  @Override
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public String toString() {
    return code;
  }
}
