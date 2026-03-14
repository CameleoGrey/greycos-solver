package ai.greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import ai.greycos.solver.core.api.cotwin.common.ComparatorFactory;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;

public class DummyValueComparatorFactory
    implements ComparatorFactory<TestdataSolution, TestdataValue> {

  @Override
  public Comparator<TestdataValue> createComparator(TestdataSolution solution) {
    return new DummyValueComparator();
  }
}
