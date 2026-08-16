package greycos.solver.core.impl.score.stream.bavet.quad;

import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.quad.AbstractQuadConstraintStreamPrecomputeTest;

final class BavetQuadConstraintStreamPrecomputeTest
    extends AbstractQuadConstraintStreamPrecomputeTest {

  public BavetQuadConstraintStreamPrecomputeTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
