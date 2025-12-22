package ai.greycos.solver.core.impl.score.stream.bavet.bi;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.bi.AbstractBiConstraintStreamNodeSharingTest;

final class BavetBiConstraintStreamNodeSharingTest
    extends AbstractBiConstraintStreamNodeSharingTest {

  public BavetBiConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
