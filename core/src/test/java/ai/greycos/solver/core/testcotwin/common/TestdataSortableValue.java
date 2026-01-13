package ai.greycos.solver.core.testcotwin.common;

import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataSortableValue extends TestdataObject implements TestSortableObject {

  private int strength;

  public TestdataSortableValue() {}

  public TestdataSortableValue(String code, int strength) {
    super(code);
    this.strength = strength;
  }

  @Override
  public int getComparatorValue() {
    return strength;
  }
}
