package ai.greycos.solver.core.impl.score.stream.common.tri;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.score.stream.AbstractConstraintBuilderTest;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;
import ai.greycos.solver.core.impl.score.stream.common.ScoreImpactType;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

class TriConstraintBuilderTest extends AbstractConstraintBuilderTest {

  private static final BavetConstraintFactory<TestdataSolution> CONSTRAINT_FACTORY =
      new BavetConstraintFactory<>(
          TestdataSolution.buildSolutionDescriptor(), EnvironmentMode.FULL_ASSERT);

  @Override
  protected AbstractConstraintBuilder<SimpleScore> of(
      String constraintName, String constraintGroup) {
    return new TriConstraintBuilderImpl<>(
        (constraintPackage,
            constraintName1,
            constraintDescription,
            constraintGroup1,
            constraintWeight,
            impactType,
            objectSimpleScoreObjectBiFunction,
            objectCollectionFunction) ->
            new BavetConstraint<>(
                CONSTRAINT_FACTORY,
                ConstraintRef.of(constraintName1),
                constraintDescription,
                constraintGroup1,
                constraintWeight,
                impactType,
                objectSimpleScoreObjectBiFunction,
                objectCollectionFunction,
                null),
        ScoreImpactType.PENALTY,
        SimpleScore.ONE);
  }
}
