package greycos.solver.core.impl.score.stream.bavet.quad;

import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.quad.AbstractQuadConstraintStreamNodeSharingTest;

final class BavetQuadConstraintStreamNodeSharingTest
    extends AbstractQuadConstraintStreamNodeSharingTest {

  public BavetQuadConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
