package ai.greycos.solver.core.testcotwin.comparable;

import java.util.Comparator;

import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataCodeComparator implements Comparator<TestdataObject> {

  private static final Comparator<TestdataObject> COMPARATOR =
      Comparator.comparing(TestdataObject::getCode);

  @Override
  public int compare(TestdataObject a, TestdataObject b) {
    return COMPARATOR.compare(a, b);
  }
}
