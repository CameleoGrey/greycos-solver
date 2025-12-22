package ai.greycos.solver.core.impl.score.stream.bavet.quad;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.quad.AbstractQuadConstraintStreamNodeSharingTest;

final class BavetQuadConstraintStreamNodeSharingTest
    extends AbstractQuadConstraintStreamNodeSharingTest {

  public BavetQuadConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
