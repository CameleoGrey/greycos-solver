package ai.greycos.solver.core.api.score;

import java.math.BigDecimal;
import java.math.RoundingMode;

import ai.greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * This {@link Score} is based on 1 level of {@link BigDecimal} constraints.
 *
 * <p>This class is immutable.
 *
 * @see Score
 */
@NullMarked
public record SimpleBigDecimalScore(BigDecimal score) implements Score<SimpleBigDecimalScore> {

  public static final SimpleBigDecimalScore ZERO = new SimpleBigDecimalScore(BigDecimal.ZERO);
  public static final SimpleBigDecimalScore ONE = new SimpleBigDecimalScore(BigDecimal.ONE);

  public static SimpleBigDecimalScore parseScore(String scoreString) {
    var scoreTokens = ScoreUtil.parseScoreTokens(SimpleBigDecimalScore.class, scoreString, "");
    var score =
        ScoreUtil.parseLevelAsBigDecimal(SimpleBigDecimalScore.class, scoreString, scoreTokens[0]);
    return of(score);
  }

  public static SimpleBigDecimalScore of(BigDecimal score) {
    if (score.signum() == 0) {
      return ZERO;
    } else if (score.equals(BigDecimal.ONE)) {
      return ONE;
    } else {
      return new SimpleBigDecimalScore(score);
    }
  }

  @Override
  public SimpleBigDecimalScore add(SimpleBigDecimalScore addend) {
    return of(score.add(addend.score()));
  }

  @Override
  public SimpleBigDecimalScore subtract(SimpleBigDecimalScore subtrahend) {
    return of(score.subtract(subtrahend.score()));
  }

  @Override
  public SimpleBigDecimalScore multiply(double multiplicand) {
    var multiplicandBigDecimal = BigDecimal.valueOf(multiplicand);
    return of(score.multiply(multiplicandBigDecimal).setScale(score.scale(), RoundingMode.FLOOR));
  }

  @Override
  public SimpleBigDecimalScore divide(double divisor) {
    var divisorBigDecimal = BigDecimal.valueOf(divisor);
    return of(score.divide(divisorBigDecimal, score.scale(), RoundingMode.FLOOR));
  }

  @Override
  public SimpleBigDecimalScore power(double exponent) {
    var exponentBigDecimal = BigDecimal.valueOf(exponent);
    return of(score.pow(exponentBigDecimal.intValue()).setScale(score.scale(), RoundingMode.FLOOR));
  }

  @Override
  public SimpleBigDecimalScore abs() {
    return of(score.abs());
  }

  @Override
  public SimpleBigDecimalScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return true;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {score};
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SimpleBigDecimalScore other) {
      return score.stripTrailingZeros().equals(other.score().stripTrailingZeros());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return score.stripTrailingZeros().hashCode();
  }

  @Override
  public int compareTo(SimpleBigDecimalScore other) {
    return score.compareTo(other.score());
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildShortString(
        this, n -> ((BigDecimal) n).compareTo(BigDecimal.ZERO) != 0, "");
  }

  @Override
  public String toString() {
    return score.toString();
  }
}
