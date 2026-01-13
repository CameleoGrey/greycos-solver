package ai.greycos.solver.core.testcotwin.score.lavish;

import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataLavishValue extends TestdataObject {

  private TestdataLavishValueGroup valueGroup;

  public TestdataLavishValue() {}

  public TestdataLavishValue(String code, TestdataLavishValueGroup valueGroup) {
    super(code);
    this.valueGroup = valueGroup;
  }

  public TestdataLavishValueGroup getValueGroup() {
    return valueGroup;
  }

  public void setValueGroup(TestdataLavishValueGroup valueGroup) {
    this.valueGroup = valueGroup;
  }
}
