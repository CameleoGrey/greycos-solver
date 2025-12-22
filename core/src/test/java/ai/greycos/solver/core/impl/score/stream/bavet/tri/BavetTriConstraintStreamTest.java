package ai.greycos.solver.core.impl.score.stream.bavet.tri;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.tri.AbstractTriConstraintStreamTest;

final class BavetTriConstraintStreamTest extends AbstractTriConstraintStreamTest {

  public BavetTriConstraintStreamTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
