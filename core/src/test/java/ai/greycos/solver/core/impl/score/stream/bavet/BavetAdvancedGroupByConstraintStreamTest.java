package ai.greycos.solver.core.impl.score.stream.bavet;

import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.common.AbstractAdvancedGroupByConstraintStreamTest;

final class BavetAdvancedGroupByConstraintStreamTest
    extends AbstractAdvancedGroupByConstraintStreamTest {

  public BavetAdvancedGroupByConstraintStreamTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }
}
