package greycos.solver.core.impl.score.stream.common.inliner;

import java.math.BigDecimal;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * There are several valid ways how an impacter could be called from a constraint stream:
 *
 * <ul>
 *   <li>{@code .penalize(..., (int) 1)}
 *   <li>{@code .penalizeLong(..., (int) 1)}
 *   <li>{@code .penalizeLong(..., (long) 1)}
 *   <li>{@code .penalizeBigDecimal(..., (int) 1)}
 *   <li>{@code .penalizeBigDecimal(..., (long) 1)}
 *   <li>{@code .penalizeBigDecimal(..., BigDecimal.ONE)}
 *   <li>Plus reward variants of the above.
 * </ul>
 *
 * An implementation of this interface can throw an {@link UnsupportedOperationException} for the
 * method types it doesn't support. The CS API guarantees no types are mixed. For example, a {@link
 * BigDecimal} parameter method won't be called on an instance built with an {@link
 * LongImpactFunction}.
 */
@NullMarked
public sealed interface WeightedScoreImpacter<
        Score_ extends Score<Score_>, Context_ extends ScoreContext<Score_, ?>>
    permits BigDecimalWeightedScoreImpacter,
        LongWeightedScoreImpacter,
        FloatWeightedScoreImpacter,
        DoubleWeightedScoreImpacter {

  static <Score_ extends Score<Score_>, Context_ extends ScoreContext<Score_, ?>>
      WeightedScoreImpacter<Score_, Context_> of(
          Context_ context, LongImpactFunction<Score_, Context_> impactFunction) {
    return new LongWeightedScoreImpacter<>(impactFunction, context);
  }

  static <Score_ extends Score<Score_>, Context_ extends ScoreContext<Score_, ?>>
      WeightedScoreImpacter<Score_, Context_> of(
          Context_ context, BigDecimalImpactFunction<Score_, Context_> impactFunction) {
    return new BigDecimalWeightedScoreImpacter<>(impactFunction, context);
  }

  /**
   * @param matchWeight never null
   * @param constraintMatchSupplier ignored unless constraint match enabled
   * @return never null
   */
  ScoreImpact<Score_> impactScore(
      long matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier);

  /**
   * Applies a float match weight without converting it to an integral or decimal value.
   *
   * @param constraintMatchSupplier ignored unless constraint match enabled
   */
  default ScoreImpact<Score_> impactScore(
      float matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    throw new UnsupportedOperationException(
        "A float match weight (%s) requires a FloatScore or DoubleScore constraint weight; constraint (%s) uses (%s)."
            .formatted(
                matchWeight,
                getContext().getConstraint().getConstraintRef(),
                getContext().getConstraintWeight().getClass().getSimpleName()));
  }

  /**
   * Applies a double match weight without converting it to an integral or decimal value.
   *
   * @param constraintMatchSupplier ignored unless constraint match enabled
   */
  default ScoreImpact<Score_> impactScore(
      double matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier) {
    throw new UnsupportedOperationException(
        "A double match weight (%s) requires a FloatScore or DoubleScore constraint weight; constraint (%s) uses (%s)."
            .formatted(
                matchWeight,
                getContext().getConstraint().getConstraintRef(),
                getContext().getConstraintWeight().getClass().getSimpleName()));
  }

  /**
   * @param matchWeight never null
   * @param constraintMatchSupplier ignored unless constraint match enabled
   * @return never null
   */
  ScoreImpact<Score_> impactScore(
      BigDecimal matchWeight, @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier);

  Context_ getContext();

  @NullMarked
  @FunctionalInterface
  interface LongImpactFunction<
      Score_ extends Score<Score_>, Context_ extends ScoreContext<Score_, ?>> {

    ScoreImpact<Score_> impact(
        Context_ context,
        long matchWeight,
        @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier);
  }

  @NullMarked
  @FunctionalInterface
  interface BigDecimalImpactFunction<
      Score_ extends Score<Score_>, Context_ extends ScoreContext<Score_, ?>> {

    ScoreImpact<Score_> impact(
        Context_ context,
        BigDecimal matchWeight,
        @Nullable ConstraintMatchSupplier<Score_> constraintMatchSupplier);
  }
}
