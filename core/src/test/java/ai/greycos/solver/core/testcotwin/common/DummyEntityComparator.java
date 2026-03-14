package ai.greycos.solver.core.testcotwin.common;

import java.util.Comparator;

import ai.greycos.solver.core.testcotwin.TestdataEntity;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class DummyEntityComparator implements Comparator<TestdataEntity> {

  @Override
  public int compare(TestdataEntity testdataEntity, TestdataEntity testdataEntity2) {
    return 0;
  }
}
