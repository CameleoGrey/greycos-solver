package greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import greycos.solver.core.api.cotwin.common.ComparatorFactory;
import greycos.solver.core.testcotwin.TestdataObject;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class TestdataObjectSortableDescendingComparatorFactory
    implements ComparatorFactory<Object, TestdataObject> {

  @Override
  public Comparator<TestdataObject> createComparator(Object solution) {
    return new TestdataObjectSortableDescendingComparator();
  }
}
