package ai.greycos.solver.core.impl.score.stream.common.inliner;

import java.util.Collections;
import java.util.Map;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraint;
import ai.greycos.solver.core.testconstraint.TestConstraint;
import ai.greycos.solver.core.testconstraint.TestConstraintFactory;

public abstract class AbstractScoreInlinerTest<Solution_, Score_ extends Score<Score_>> {

  protected final ConstraintMatchPolicy constraintMatchPolicy = ConstraintMatchPolicy.ENABLED;
  private final TestConstraintFactory<Solution_, Score_> constraintFactory =
      new TestConstraintFactory<>(buildSolutionDescriptor());

  protected abstract SolutionDescriptor<Solution_> buildSolutionDescriptor();

  protected TestConstraint<Solution_, Score_> buildConstraint(Score_ constraintWeight) {
    return new TestConstraint<>(constraintFactory, "Test Constraint", constraintWeight);
  }

  protected WeightedScoreImpacter<Score_, ?> buildScoreImpacter(Score_ constraintWeight) {
    AbstractConstraint<?, ?, ?> constraint = buildConstraint(constraintWeight);
    Map<Constraint, Score_> constraintWeightMap =
        Collections.singletonMap(constraint, constraintWeight);
    AbstractScoreInliner<Score_> scoreInliner =
        buildScoreInliner(constraintWeightMap, constraintMatchPolicy);
    return scoreInliner.buildWeightedScoreImpacter(constraint);
  }

  protected abstract AbstractScoreInliner<Score_> buildScoreInliner(
      Map<Constraint, Score_> constraintWeightMap, ConstraintMatchPolicy constraintMatchPolicy);
}
