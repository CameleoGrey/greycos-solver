package greycos.solver.core.impl.score.stream.bavet.bi;

import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.bi.AbstractBiConstraintStreamPrecomputeTest;

final class BavetBiConstraintStreamPrecomputeTest extends AbstractBiConstraintStreamPrecomputeTest {

  public BavetBiConstraintStreamPrecomputeTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
