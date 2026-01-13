package ai.greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import ai.greycos.solver.core.testcotwin.TestdataValue;

public class DummyValueComparator implements Comparator<TestdataValue> {

  @Override
  public int compare(TestdataValue v1, TestdataValue v2) {
    return 0;
  }
}
