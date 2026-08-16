package greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import greycos.solver.core.api.cotwin.common.ComparatorFactory;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.sort.factory.TestdataListFactorySortableSolution;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DummyValueComparatorFactory
    implements ComparatorFactory<TestdataListFactorySortableSolution, TestdataValue> {

  @Override
  public Comparator<TestdataValue> createComparator(TestdataListFactorySortableSolution solution) {
    return new DummyValueComparator();
  }
}
