package greycos.solver.core.testconstraint;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.AbstractConstraint;
import greycos.solver.core.impl.score.stream.common.DefaultConstraintMetadata;
import greycos.solver.core.impl.score.stream.common.ScoreImpactType;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TestConstraint<Solution_, Score_ extends Score<Score_>>
    extends AbstractConstraint<
        Solution_, TestConstraint<Solution_, Score_>, TestConstraintFactory<Solution_, Score_>> {

  public TestConstraint(
      TestConstraintFactory<Solution_, Score_> constraintFactory,
      String constraintId,
      Score_ constraintWeight) {
    super(
        constraintFactory,
        new DefaultConstraintMetadata(constraintId),
        constraintWeight,
        ScoreImpactType.REWARD,
        null);
  }
}
