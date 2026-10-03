package greycos.solver.core.impl.score;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.FloatingScoreAccumulator;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleScore;

/** Arithmetic used outside score calculation, where differences need not fit in a score level. */
public final class ScoreArithmetic {

  private ScoreArithmetic() {}

  public static <Score_ extends Score<Score_>> Score_ sum(Score_ zero, Stream<Score_> scores) {
    if (!FloatingScoreSupport.isFloatingScore(zero)) {
      return scores.reduce(zero, Score::add);
    }
    var accumulator = FloatingScoreAccumulator.create(zero);
    scores.forEachOrdered(accumulator::add);
    return accumulator.extractScore();
  }

  /** Preserve established arithmetic for existing types; widen native floating differences. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Number[] difference(Score<?> left, Score<?> right) {
    if (!FloatingScoreSupport.isFloatingScore(left)) {
      return ((Score) left).subtract(right).toLevelNumbers();
    }
    FloatingScoreSupport.validateCompatible(left, right);
    var leftLevels = left.toLevelNumbers();
    var rightLevels = right.toLevelNumbers();
    var result = new BigDecimal[leftLevels.length];
    for (int i = 0; i < result.length; i++) {
      result[i] =
          FloatingScoreSupport.exact(leftLevels[i])
              .subtract(FloatingScoreSupport.exact(rightLevels[i]));
    }
    return result;
  }

  public static double ratio(Number numerator, Number denominator) {
    if (denominator.doubleValue() == 0.0) {
      return numerator.doubleValue() / denominator.doubleValue();
    }
    if (numerator instanceof BigDecimal || denominator instanceof BigDecimal) {
      return FloatingScoreSupport.exact(numerator)
          .divide(FloatingScoreSupport.exact(denominator), MathContext.DECIMAL128)
          .doubleValue();
    }
    return numerator.doubleValue() / denominator.doubleValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static boolean differenceAtLeast(Score<?> left, Score<?> right, Score<?> threshold) {
    if (!FloatingScoreSupport.isFloatingScore(left)) {
      return ((Score) left).subtract(right).compareTo(threshold) >= 0;
    }
    FloatingScoreSupport.validateCompatible(left, right);
    FloatingScoreSupport.validateCompatible(left, threshold);
    var structuralDifference =
        BigInteger.valueOf(left.structuralScore())
            .subtract(BigInteger.valueOf(right.structuralScore()));
    var structuralComparison =
        structuralDifference.compareTo(BigInteger.valueOf(threshold.structuralScore()));
    if (structuralComparison != 0) {
      return structuralComparison > 0;
    }
    var differences = difference(left, right);
    var thresholds = threshold.toLevelNumbers();
    for (int i = 0; i < differences.length; i++) {
      int comparison =
          FloatingScoreSupport.exact(differences[i])
              .compareTo(FloatingScoreSupport.exact(thresholds[i]));
      if (comparison != 0) {
        return comparison > 0;
      }
    }
    return true;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static boolean isFeasibleDifference(Score<?> left, Score<?> right, int feasibleLevels) {
    if (!(FloatingScoreSupport.isFloatingScore(left)
        || left instanceof SimpleScore
        || left instanceof HardSoftScore
        || left instanceof HardMediumSoftScore
        || left instanceof BendableScore
        || left instanceof SimpleBigDecimalScore
        || left instanceof HardSoftBigDecimalScore
        || left instanceof HardMediumSoftBigDecimalScore
        || left instanceof BendableBigDecimalScore)) {
      // A custom score owns its arithmetic and feasibility contract.
      return ((Score) left).subtract(right).isFeasible();
    }
    if (left.structuralScore() < right.structuralScore()) {
      return false;
    }
    FloatingScoreSupport.validateCompatible(left, right);
    var leftLevels = left.toLevelNumbers();
    var rightLevels = right.toLevelNumbers();
    for (int i = 0; i < feasibleLevels; i++) {
      var leftLevel = leftLevels[i];
      var rightLevel = rightLevels[i];
      // Integral hard differences may overflow even when both scores are representable.
      // Keep the established componentwise feasibility test, without constructing a difference.
      int comparison =
          leftLevel instanceof Long && rightLevel instanceof Long
              ? Long.compare(leftLevel.longValue(), rightLevel.longValue())
              : FloatingScoreSupport.exact(leftLevel)
                  .compareTo(FloatingScoreSupport.exact(rightLevel));
      if (comparison < 0) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static String differenceString(Score<?> left, Score<?> right) {
    return FloatingScoreSupport.isFloatingScore(left)
        ? Arrays.toString(difference(left, right))
        : ((Score) left).subtract(right).toShortString();
  }
}
