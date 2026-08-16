package greycos.solver.core.impl.score.stream.bavet.uni;

import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamNodeSharingTest;

final class BavetUniConstraintStreamNodeSharingTest
    extends AbstractUniConstraintStreamNodeSharingTest {

  public BavetUniConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
