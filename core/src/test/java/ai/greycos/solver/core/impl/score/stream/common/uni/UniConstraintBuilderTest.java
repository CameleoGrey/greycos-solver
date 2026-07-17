package ai.greycos.solver.core.impl.score.stream.common.uni;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.score.stream.AbstractConstraintBuilderTest;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;
import ai.greycos.solver.core.impl.score.stream.common.ScoreImpactType;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

class UniConstraintBuilderTest extends AbstractConstraintBuilderTest {

  private static final BavetConstraintFactory<TestdataSolution> CONSTRAINT_FACTORY =
      new BavetConstraintFactory<>(
          TestdataSolution.buildSolutionDescriptor(), EnvironmentMode.FULL_ASSERT);

  @Override
  protected AbstractConstraintBuilder<SimpleScore> of(String constraintId) {
    return new UniConstraintBuilderImpl<>(
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
