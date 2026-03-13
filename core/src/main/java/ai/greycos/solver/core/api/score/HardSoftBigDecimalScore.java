package ai.greycos.solver.core.api.score;

import static ai.greycos.solver.core.impl.score.ScoreUtil.HARD_LABEL;
import static ai.greycos.solver.core.impl.score.ScoreUtil.SOFT_LABEL;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import ai.greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * This {@link Score} is based on 2 levels of {@link BigDecimal} constraints: hard and soft. Hard
 * constraints have priority over soft constraints. Hard constraints determine feasibility.
 *
 * <p>This class is immutable.
 *
 * @see Score
 */
@NullMarked
public record HardSoftBigDecimalScore(BigDecimal hardScore, BigDecimal softScore)
    implements Score<HardSoftBigDecimalScore> {

  public static final HardSoftBigDecimalScore ZERO =
      new HardSoftBigDecimalScore(BigDecimal.ZERO, BigDecimal.ZERO);
  public static final HardSoftBigDecimalScore ONE_HARD =
      new HardSoftBigDecimalScore(BigDecimal.ONE, BigDecimal.ZERO);
  public static final HardSoftBigDecimalScore ONE_SOFT =
      new HardSoftBigDecimalScore(BigDecimal.ZERO, BigDecimal.ONE);

  public static HardSoftBigDecimalScore parseScore(String scoreString) {
    var scoreTokens =
        ScoreUtil.parseScoreTokens(
            HardSoftBigDecimalScore.class, scoreString, HARD_LABEL, SOFT_LABEL);
    var hardScore =
        ScoreUtil.parseLevelAsBigDecimal(
            HardSoftBigDecimalScore.class, scoreString, scoreTokens[0]);
    var softScore =
        ScoreUtil.parseLevelAsBigDecimal(
            HardSoftBigDecimalScore.class, scoreString, scoreTokens[1]);
    return of(hardScore, softScore);
  }

  public static HardSoftBigDecimalScore of(BigDecimal hardScore, BigDecimal softScore) {
    if (hardScore.signum() == 0) {
      if (softScore.signum() == 0) {
        return ZERO;
      } else if (Objects.equals(softScore, BigDecimal.ONE)) {
        return ONE_SOFT;
      }
    } else if (Objects.equals(hardScore, BigDecimal.ONE) && softScore.signum() == 0) {
      return ONE_HARD;
    }
    return new HardSoftBigDecimalScore(hardScore, softScore);
  }

  public static HardSoftBigDecimalScore ofHard(BigDecimal hardScore) {
    if (hardScore.signum() == 0) {
      return ZERO;
    } else if (Objects.equals(hardScore, BigDecimal.ONE)) {
      return ONE_HARD;
    }
    return new HardSoftBigDecimalScore(hardScore, BigDecimal.ZERO);
  }

  public static HardSoftBigDecimalScore ofSoft(BigDecimal softScore) {
    if (softScore.signum() == 0) {
      return ZERO;
    } else if (Objects.equals(softScore, BigDecimal.ONE)) {
      return ONE_SOFT;
    }
    return new HardSoftBigDecimalScore(BigDecimal.ZERO, softScore);
  }

  @Override
  public boolean isFeasible() {
    return hardScore.signum() >= 0;
  }

  @Override
  public HardSoftBigDecimalScore add(HardSoftBigDecimalScore addend) {
    return of(hardScore.add(addend.hardScore()), softScore.add(addend.softScore()));
  }

  @Override
  public HardSoftBigDecimalScore subtract(HardSoftBigDecimalScore subtrahend) {
    return of(
        hardScore.subtract(subtrahend.hardScore()), softScore.subtract(subtrahend.softScore()));
  }

  @Override
  public HardSoftBigDecimalScore multiply(double multiplicand) {
    var multiplicandBigDecimal = BigDecimal.valueOf(multiplicand);
    return of(
        hardScore.multiply(multiplicandBigDecimal).setScale(hardScore.scale(), RoundingMode.FLOOR),
        softScore.multiply(multiplicandBigDecimal).setScale(softScore.scale(), RoundingMode.FLOOR));
  }

  @Override
  public HardSoftBigDecimalScore divide(double divisor) {
    var divisorBigDecimal = BigDecimal.valueOf(divisor);
    return of(
        hardScore.divide(divisorBigDecimal, hardScore.scale(), RoundingMode.FLOOR),
        softScore.divide(divisorBigDecimal, softScore.scale(), RoundingMode.FLOOR));
  }

  @Override
  public HardSoftBigDecimalScore power(double exponent) {
    var exponentBigDecimal = BigDecimal.valueOf(exponent);
    return of(
        hardScore
            .pow(exponentBigDecimal.intValue())
            .setScale(hardScore.scale(), RoundingMode.FLOOR),
        softScore
            .pow(exponentBigDecimal.intValue())
            .setScale(softScore.scale(), RoundingMode.FLOOR));
  }

  @Override
  public HardSoftBigDecimalScore abs() {
    return of(hardScore.abs(), softScore.abs());
  }

  @Override
  public HardSoftBigDecimalScore zero() {
    return ZERO;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {hardScore, softScore};
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof HardSoftBigDecimalScore other) {
      return hardScore.stripTrailingZeros().equals(other.hardScore().stripTrailingZeros())
          && softScore.stripTrailingZeros().equals(other.softScore().stripTrailingZeros());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(hardScore.stripTrailingZeros(), softScore.stripTrailingZeros());
  }

  @Override
  public int compareTo(HardSoftBigDecimalScore other) {
    var hardScoreComparison = hardScore.compareTo(other.hardScore());
    if (hardScoreComparison != 0) {
      return hardScoreComparison;
    } else {
      return softScore.compareTo(other.softScore());
    }
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildShortString(
        this, n -> ((BigDecimal) n).compareTo(BigDecimal.ZERO) != 0, HARD_LABEL, SOFT_LABEL);
  }

  @Override
  public String toString() {
    return hardScore + HARD_LABEL + "/" + softScore + SOFT_LABEL;
  }
}
