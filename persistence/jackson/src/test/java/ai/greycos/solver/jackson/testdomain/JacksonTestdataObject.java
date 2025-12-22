package ai.greycos.solver.jackson.testdomain;

import ai.greycos.solver.core.testutil.CodeAssertable;

public abstract class JacksonTestdataObject implements CodeAssertable {

  protected String code;

  public JacksonTestdataObject() {}

  public JacksonTestdataObject(String code) {
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
