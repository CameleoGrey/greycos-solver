package greycos.solver.core.impl.score.stream.common.inliner;

import java.math.BigDecimal;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class FloatWeightedScoreImpacter<Score_ extends Score<Score_>>
    implements WeightedScoreImpacter<Score_, FloatingScoreContext<Score_>> {

  private final FloatingScoreContext<Score_> context;

  FloatWeightedScoreImpacter(FloatingScoreContext<Score_> context) {
    this.context = context;
  }

  @Override
  public ScoreImpact<Score_> impactScore(
      long matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    context.getConstraint().assertCorrectImpact(matchWeight);
    return context.changeScoreBy(matchWeight, constraintMatchSupplier);
  }

  @Override
  public ScoreImpact<Score_> impactScore(
      float matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    context.getConstraint().assertCorrectImpact(matchWeight);
    return context.changeScoreBy(matchWeight, constraintMatchSupplier);
  }

  @Override
  public ScoreImpact<Score_> impactScore(
      double matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    context.getConstraint().assertCorrectImpact(matchWeight);
    return context.changeScoreBy(matchWeight, constraintMatchSupplier);
  }

  @Override
  public ScoreImpact<Score_> impactScore(
      BigDecimal matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    throw new UnsupportedOperationException(
        "A BigDecimal match weight (%s) is not supported by a floating-point constraint (%s)."
            .formatted(matchWeight, context.getConstraint().getConstraintRef()));
  }

  @Override
  public FloatingScoreContext<Score_> getContext() {
    return context;
  }
}
