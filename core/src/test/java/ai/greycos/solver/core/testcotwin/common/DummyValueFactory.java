package ai.greycos.solver.core.testcotwin.common;

import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorterWeightFactory;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.list.sort.factory.TestdataListFactorySortableSolution;

public class DummyValueFactory
    implements SelectionSorterWeightFactory<TestdataListFactorySortableSolution, TestdataValue> {

  @Override
  public Comparable createSorterWeight(
      TestdataListFactorySortableSolution solution, TestdataValue selection) {
    return 0;
  }
}
