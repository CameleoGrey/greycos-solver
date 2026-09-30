package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.IBendableScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.director.InnerScore;

/** Compares guided scores without changing or rounding the business score. */
public final class GuidedLocalSearchScoreComparator {

  private static final Set<Class<?>> SUPPORTED_SCORE_TYPES =
      Set.of(
          SimpleScore.class,
          HardSoftScore.class,
          HardMediumSoftScore.class,
          BendableScore.class,
          SimpleBigDecimalScore.class,
          HardSoftBigDecimalScore.class,
          HardMediumSoftBigDecimalScore.class,
          BendableBigDecimalScore.class,
          SimpleFloatScore.class,
          HardSoftFloatScore.class,
          HardMediumSoftFloatScore.class,
          BendableFloatScore.class,
          SimpleDoubleScore.class,
          HardSoftDoubleScore.class,
          HardMediumSoftDoubleScore.class,
          BendableDoubleScore.class);

  public static void requireSupportedScoreType(Class<?> scoreClass) {
    if (!SUPPORTED_SCORE_TYPES.contains(scoreClass)) {
      throw new IllegalArgumentException(
          "Guided Local Search does not support custom score class ("
              + scoreClass.getName()
              + "). Use a built-in integral, BigDecimal, Float or Double score family.");
    }
  }

  private final int targetScoreLevelIndex;
  private final GuidedLocalSearchNumber numerator;
  private final GuidedLocalSearchNumber denominator;

  public GuidedLocalSearchScoreComparator(int targetScoreLevelIndex, BigDecimal penaltyFactor) {
    if (targetScoreLevelIndex < 0) {
      throw new IllegalArgumentException(
          "The GLS targetScoreLevelIndex (%d) must be nonnegative."
              .formatted(targetScoreLevelIndex));
    }
    if (Objects.requireNonNull(penaltyFactor).signum() <= 0) {
      throw new IllegalArgumentException(
          "The GLS penaltyFactor (%s) must be positive.".formatted(penaltyFactor));
    }
    this.targetScoreLevelIndex = targetScoreLevelIndex;
    var normalized = penaltyFactor.stripTrailingZeros();
    var unscaled = normalized.unscaledValue();
    var divisor = BigInteger.TEN.pow(Math.max(normalized.scale(), 0));
    if (normalized.scale() < 0) {
      unscaled = unscaled.multiply(BigInteger.TEN.pow(-normalized.scale()));
    }
    var commonFactor = unscaled.gcd(divisor);
    numerator = GuidedLocalSearchNumber.of(unscaled.divide(commonFactor));
    denominator = GuidedLocalSearchNumber.of(divisor.divide(commonFactor));
  }

  public int compareProtectedPrefix(InnerScore<?> left, InnerScore<?> right) {
    validateScores(left.raw(), right.raw());
    return compareProtectedPrefixUnchecked(left, right);
  }

  private int compareProtectedPrefixUnchecked(InnerScore<?> left, InnerScore<?> right) {
    int comparison = Long.compare(left.raw().structuralScore(), right.raw().structuralScore());
    if (comparison != 0) {
      return comparison;
    }
    comparison = Integer.compare(right.unassignedCount(), left.unassignedCount());
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < targetScoreLevelIndex; i++) {
      comparison = compareLevel(left.raw(), right.raw(), i);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  public int compare(
      InnerScore<?> left,
      GuidedLocalSearchNumber leftPenalty,
      InnerScore<?> right,
      GuidedLocalSearchNumber rightPenalty) {
    return compare(left, leftPenalty, right, rightPenalty, GuidedLocalSearchNumber.ONE);
  }

  /** Penalty arguments are exact numerators sharing {@code penaltyDenominator}. */
  public int compare(
      InnerScore<?> left,
      GuidedLocalSearchNumber leftPenalty,
      InnerScore<?> right,
      GuidedLocalSearchNumber rightPenalty,
      GuidedLocalSearchNumber penaltyDenominator) {
    if (penaltyDenominator.signum() <= 0) {
      throw new IllegalArgumentException(
          "The GLS penalty denominator (" + penaltyDenominator + ") must be positive.");
    }
    validateScores(left.raw(), right.raw());
    if (leftPenalty.equals(rightPenalty)) {
      return compareOriginal(left, right);
    }
    int comparison = compareProtectedPrefixUnchecked(left, right);
    if (comparison != 0) {
      return comparison;
    }
    var combinedDenominator = denominator.multiply(penaltyDenominator);
    if (isIntegral(left.raw())) {
      comparison =
          GuidedLocalSearchNumber.compareAdjusted(
              integralLevel(left.raw(), targetScoreLevelIndex),
              integralLevel(right.raw(), targetScoreLevelIndex),
              leftPenalty,
              rightPenalty,
              numerator,
              combinedDenominator);
    } else {
      var scoreDifference =
          GuidedLocalSearchNumber.of(
              decimalLevel(left.raw(), targetScoreLevelIndex)
                  .subtract(decimalLevel(right.raw(), targetScoreLevelIndex)));
      comparison =
          scoreDifference
              .multiply(combinedDenominator)
              .subtract(leftPenalty.subtract(rightPenalty).multiply(numerator))
              .signum();
    }
    if (comparison != 0) {
      return comparison;
    }
    for (int i = targetScoreLevelIndex + 1; i < levelsSize(left.raw()); i++) {
      comparison = compareLevel(left.raw(), right.raw(), i);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareOriginal(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }

  private static boolean isIntegral(Score<?> score) {
    return score instanceof SimpleScore
        || score instanceof HardSoftScore
        || score instanceof HardMediumSoftScore
        || score instanceof BendableScore;
  }

  private static int compareLevel(Score<?> left, Score<?> right, int index) {
    return isIntegral(left)
        ? Long.compare(integralLevel(left, index), integralLevel(right, index))
        : decimalLevel(left, index).compareTo(decimalLevel(right, index));
  }

  private static long integralLevel(Score<?> score, int index) {
    return switch (score) {
      case SimpleScore value -> value.score();
      case HardSoftScore value -> index == 0 ? value.hardScore() : value.softScore();
      case HardMediumSoftScore value ->
          switch (index) {
            case 0 -> value.hardScore();
            case 1 -> value.mediumScore();
            default -> value.softScore();
          };
      case BendableScore value -> value.hardOrSoftScore(index);
      default ->
          throw new IllegalArgumentException("Unsupported integral GLS score: " + score.getClass());
    };
  }

  private static BigDecimal decimalLevel(Score<?> score, int index) {
    return switch (score) {
      case SimpleBigDecimalScore value -> value.score();
      case HardSoftBigDecimalScore value -> index == 0 ? value.hardScore() : value.softScore();
      case HardMediumSoftBigDecimalScore value ->
          switch (index) {
            case 0 -> value.hardScore();
            case 1 -> value.mediumScore();
            default -> value.softScore();
          };
      case BendableBigDecimalScore value -> value.hardOrSoftScore(index);
      default -> GuidedLocalSearchNumber.of(score.toLevelNumbers()[index]).toBigDecimal();
    };
  }

  private static int levelsSize(Score<?> score) {
    return switch (score) {
      case SimpleScore ignored -> 1;
      case SimpleBigDecimalScore ignored -> 1;
      case HardSoftScore ignored -> 2;
      case HardSoftBigDecimalScore ignored -> 2;
      case HardMediumSoftScore ignored -> 3;
      case HardMediumSoftBigDecimalScore ignored -> 3;
      case BendableScore value -> value.levelsSize();
      case BendableBigDecimalScore value -> value.levelsSize();
      default -> score.toLevelNumbers().length;
    };
  }

  private void validateScores(Score<?> left, Score<?> right) {
    requireSupportedScoreType(left.getClass());
    if (left.getClass() != right.getClass()
        || levelsSize(left) != levelsSize(right)
        || targetScoreLevelIndex >= levelsSize(left)) {
      throw new IllegalArgumentException(
          "The GLS targetScoreLevelIndex (%d) is incompatible with scores (%s, %s)."
              .formatted(targetScoreLevelIndex, left, right));
    }
    if (left instanceof IBendableScore<?> l
        && right instanceof IBendableScore<?> r
        && l.hardLevelsSize() != r.hardLevelsSize()) {
      throw new IllegalArgumentException("GLS requires matching bendable score dimensions.");
    }
  }
}
