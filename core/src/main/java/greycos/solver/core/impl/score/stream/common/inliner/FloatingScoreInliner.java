package greycos.solver.core.impl.score.stream.common.inliner;

import java.util.Map;

import greycos.solver.core.api.score.FloatingScoreAccumulator;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.common.AbstractConstraint;

import org.jspecify.annotations.NullMarked;

/**
 * Shares exact, reversible accumulation across the float and double score families. Each match's
 * product is rounded once to the score's precision; extracting the sum never changes stored state.
 */
@NullMarked
final class FloatingScoreInliner<Score_ extends Score<Score_>>
    extends AbstractScoreInliner<Score_> {

  final FloatingScoreAccumulator<Score_> accumulator;
  private final Class<?> numericType;
  private final Class<?> scoreClass;

  FloatingScoreInliner(
      Map<Constraint, Score_> constraintWeightMap,
      ConstraintMatchPolicy constraintMatchPolicy,
      Score_ zeroScore,
      Class<?> numericType) {
    super(constraintWeightMap, constraintMatchPolicy);
    if (numericType != float.class && numericType != double.class) {
      throw new IllegalArgumentException(
          "The numericType (%s) must be float or double.".formatted(numericType));
    }
    this.accumulator = FloatingScoreAccumulator.create(zeroScore);
    this.numericType = numericType;
    this.scoreClass = zeroScore.getClass();
  }

  @Override
  public WeightedScoreImpacter<Score_, ?> buildWeightedScoreImpacter(
      AbstractConstraint<?, ?, ?> constraint) {
    var constraintWeight = constraintWeightMap.get(constraint);
    if (constraintWeight == null) {
      throw new IllegalArgumentException(
          "The constraint (%s) has no weight in this score inliner."
              .formatted(constraint.getConstraintRef()));
    }
    var context = new FloatingScoreContext<>(this, constraint, constraintWeight);
    return numericType == float.class
        ? new FloatWeightedScoreImpacter<>(context)
        : new DoubleWeightedScoreImpacter<>(context);
  }

  @Override
  public Score_ extractScore() {
    return accumulator.extractScore();
  }

  @Override
  public String toString() {
    return scoreClass.getSimpleName() + " inliner";
  }
}
