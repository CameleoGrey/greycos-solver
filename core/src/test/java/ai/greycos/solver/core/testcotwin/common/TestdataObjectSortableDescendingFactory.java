package ai.greycos.solver.core.testcotwin.common;

import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorterWeightFactory;
import ai.greycos.solver.core.testcotwin.TestdataObject;

public class TestdataObjectSortableDescendingFactory
    implements SelectionSorterWeightFactory<Object, TestdataObject> {

  @Override
  public Comparable createSorterWeight(Object solution, TestdataObject selection) {
    // Descending order
    return -extractCode(selection.getCode());
  }

  public static int extractCode(String code) {
    var idx = code.lastIndexOf(" ");
    return Integer.parseInt(code.substring(idx + 1));
  }
}
