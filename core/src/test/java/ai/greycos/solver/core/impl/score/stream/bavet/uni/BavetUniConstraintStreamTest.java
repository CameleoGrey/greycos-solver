package ai.greycos.solver.core.impl.score.stream.bavet.uni;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamTest;

final class BavetUniConstraintStreamTest extends AbstractUniConstraintStreamTest {

  public BavetUniConstraintStreamTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
