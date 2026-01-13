package ai.greycos.solver.core.testcotwin.common;

public interface TestSortableObject extends Comparable<TestSortableObject> {

  int getComparatorValue();

  @Override
  default int compareTo(TestSortableObject o) {
    return getComparatorValue() - o.getComparatorValue();
  }
}
