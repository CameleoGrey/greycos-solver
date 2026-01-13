package ai.greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataObjectSortableDescendingComparator implements Comparator<TestdataObject> {

  @Override
  public int compare(TestdataObject o1, TestdataObject o2) {
    // Descending order
    return extractCode(o2.getCode()) - extractCode(o1.getCode());
  }

  public static int extractCode(String code) {
    var idx = code.lastIndexOf(" ");
    return Integer.parseInt(code.substring(idx + 1));
  }
}
