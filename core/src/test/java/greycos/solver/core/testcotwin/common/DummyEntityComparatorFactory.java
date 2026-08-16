package greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import greycos.solver.core.api.cotwin.common.ComparatorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DummyEntityComparatorFactory
    implements ComparatorFactory<TestdataSolution, TestdataEntity> {

  @Override
  public Comparator<TestdataEntity> createComparator(TestdataSolution solution) {
    return new DummyEntityComparator();
  }
}
