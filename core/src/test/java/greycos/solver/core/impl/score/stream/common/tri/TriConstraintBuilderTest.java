package greycos.solver.core.impl.score.stream.common.tri;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.stream.AbstractConstraintBuilderTest;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;
import greycos.solver.core.impl.score.stream.common.ScoreImpactType;
import greycos.solver.core.testcotwin.TestdataSolution;

class TriConstraintBuilderTest extends AbstractConstraintBuilderTest {

  private static final BavetConstraintFactory<TestdataSolution> CONSTRAINT_FACTORY =
      new BavetConstraintFactory<>(
          TestdataSolution.buildSolutionDescriptor(), EnvironmentMode.FULL_ASSERT);

  @Override
  protected AbstractConstraintBuilder<SimpleScore> of(String constraintId) {
    return new TriConstraintBuilderImpl<>(
        (constraintMetadata, constraintWeight, impactType, justificationMapping) ->
            new BavetConstraint<>(
                CONSTRAINT_FACTORY,
                constraintMetadata,
                constraintWeight,
                impactType,
                justificationMapping,
                null),
        ScoreImpactType.PENALTY,
        SimpleScore.ONE);
  }
}
