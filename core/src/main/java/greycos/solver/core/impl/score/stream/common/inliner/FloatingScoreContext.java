package greycos.solver.core.impl.score.stream.common.inliner;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.AbstractConstraint;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class FloatingScoreContext<Score_ extends Score<Score_>>
    extends ScoreContext<Score_, FloatingScoreInliner<Score_>> {

  FloatingScoreContext(
      FloatingScoreInliner<Score_> inliner,
      AbstractConstraint<?, ?, ?> constraint,
      Score_ constraintWeight) {
    super(inliner, constraint, constraintWeight);
  }

  ScoreImpact<Score_> changeScoreBy(
      long matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    validateSupplier(constraintMatchSupplier);
    var contribution = inliner.accumulator.addWeighted(constraintWeight, matchWeight);
    return possiblyAddConstraintMatch(
        new Impact<>(
            inliner,
            contribution,
            inliner.constraintMatchPolicy.isEnabled() ? Long.valueOf(matchWeight) : null),
        constraintMatchSupplier);
  }

  ScoreImpact<Score_> changeScoreBy(
      float matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    validateSupplier(constraintMatchSupplier);
    var contribution = inliner.accumulator.addWeighted(constraintWeight, matchWeight);
    return possiblyAddConstraintMatch(
        new Impact<>(
            inliner,
            contribution,
            inliner.constraintMatchPolicy.isEnabled() ? Float.valueOf(matchWeight) : null),
        constraintMatchSupplier);
  }

  ScoreImpact<Score_> changeScoreBy(
      double matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    validateSupplier(constraintMatchSupplier);
    var contribution = inliner.accumulator.addWeighted(constraintWeight, matchWeight);
    return possiblyAddConstraintMatch(
        new Impact<>(
            inliner,
            contribution,
            inliner.constraintMatchPolicy.isEnabled() ? Double.valueOf(matchWeight) : null),
        constraintMatchSupplier);
  }

  private void validateSupplier(@Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    if (inliner.constraintMatchPolicy.isEnabled()) {
      Objects.requireNonNull(
          constraintMatchSupplier, "The constraintMatchSupplier must not be null.");
    }
  }

  @NullMarked
  private record Impact<Score_ extends Score<Score_>>(
      FloatingScoreInliner<Score_> inliner, Score_ contribution, @Nullable Number matchWeight)
      implements ScoreImpact<Score_> {

    @Override
    public void undo() {
      inliner.accumulator.subtract(contribution);
    }

    @Override
    public Score_ toScore() {
      return contribution;
    }
  }
}
