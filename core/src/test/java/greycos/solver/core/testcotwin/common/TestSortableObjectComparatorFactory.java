package greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import greycos.solver.core.api.cotwin.common.ComparatorFactory;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class TestSortableObjectComparatorFactory
    implements ComparatorFactory<Object, TestSortableObject> {

  @Override
  public Comparator<TestSortableObject> createComparator(Object solution) {
    return new TestSortableObjectComparator();
  }
}
