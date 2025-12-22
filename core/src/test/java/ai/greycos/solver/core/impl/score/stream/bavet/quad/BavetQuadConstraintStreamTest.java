package ai.greycos.solver.core.impl.score.stream.bavet.quad;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.quad.AbstractQuadConstraintStreamTest;

final class BavetQuadConstraintStreamTest extends AbstractQuadConstraintStreamTest {

  public BavetQuadConstraintStreamTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
