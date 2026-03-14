package ai.greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import ai.greycos.solver.core.api.cotwin.common.ComparatorFactory;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DummyEntityComparatorFactory
    implements ComparatorFactory<TestdataSolution, TestdataEntity> {

  @Override
  public Comparator<TestdataEntity> createComparator(TestdataSolution solution) {
    return new DummyEntityComparator();
  }
}
