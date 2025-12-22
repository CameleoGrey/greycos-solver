package ai.greycos.solver.core.testdomain.common;

import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorterWeightFactory;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testdomain.list.sort.factory.TestdataListFactorySortableSolution;

public class DummyValueFactory
    implements SelectionSorterWeightFactory<TestdataListFactorySortableSolution, TestdataValue> {

  @Override
  public Comparable createSorterWeight(
      TestdataListFactorySortableSolution solution, TestdataValue selection) {
    return 0;
  }
}
