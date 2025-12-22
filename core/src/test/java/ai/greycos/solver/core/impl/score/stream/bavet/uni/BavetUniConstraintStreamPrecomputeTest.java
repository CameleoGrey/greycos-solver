package ai.greycos.solver.core.impl.score.stream.bavet.uni;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamPrecomputeTest;

final class BavetUniConstraintStreamPrecomputeTest
    extends AbstractUniConstraintStreamPrecomputeTest {

  public BavetUniConstraintStreamPrecomputeTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
