package greycos.solver.core.impl.score.stream.bavet.bi;

import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.bi.AbstractBiConstraintStreamNodeSharingTest;

final class BavetBiConstraintStreamNodeSharingTest
    extends AbstractBiConstraintStreamNodeSharingTest {

  public BavetBiConstraintStreamNodeSharingTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
