package greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DummyValueComparator implements Comparator<TestdataValue> {

  @Override
  public int compare(TestdataValue v1, TestdataValue v2) {
    return 0;
  }
}
