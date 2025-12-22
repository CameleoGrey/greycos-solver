package ai.greycos.solver.core.impl.score.stream.bavet.uni;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamNodeSharingTest;

final class BavetUniConstraintStreamNodeSharingTest
    extends AbstractUniConstraintStreamNodeSharingTest {

  public BavetUniConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
