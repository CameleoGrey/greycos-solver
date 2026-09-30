package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 1 finite double business level. Signed zero is
 * canonicalized to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record SimpleDoubleScore(long structuralScore, double score)
    implements Score<SimpleDoubleScore> {

  public static final SimpleDoubleScore ZERO = new SimpleDoubleScore(0.0);
  public static final SimpleDoubleScore ONE = new SimpleDoubleScore(1.0);

  public SimpleDoubleScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    score = FloatingPointMath.finite(score, "SimpleDoubleScore.score");
  }

  public SimpleDoubleScore(double score) {
    this(0L, score);
  }

  public static SimpleDoubleScore of(double score) {
    return new SimpleDoubleScore(score);
  }

  public static SimpleDoubleScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseScoreTokens(SimpleDoubleScore.class, scoreString, "");
    int offset = tokens.length == 1 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(SimpleDoubleScore.class, scoreString, tokens[0]);
    return new SimpleDoubleScore(
        structural,
        FloatingPointMath.parseDouble(SimpleDoubleScore.class, scoreString, tokens[offset + 0]));
  }

  @Override
  public SimpleDoubleScore add(SimpleDoubleScore addend) {
    return of(FloatingPointMath.add(score, addend.score));
  }

  @Override
  public SimpleDoubleScore subtract(SimpleDoubleScore subtrahend) {
    return of(FloatingPointMath.subtract(score, subtrahend.score));
  }

  @Override
  public SimpleDoubleScore multiply(double multiplicand) {
    return of(FloatingPointMath.multiply(score, multiplicand));
  }

  @Override
  public SimpleDoubleScore divide(double divisor) {
    return of(FloatingPointMath.divide(score, divisor));
  }

  @Override
  public SimpleDoubleScore power(double exponent) {
    return of(FloatingPointMath.power(score, exponent));
  }

  @Override
  public SimpleDoubleScore abs() {
    return of(Math.abs(score));
  }

  @Override
  public SimpleDoubleScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return structuralScore >= 0L;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {score};
  }

  @Override
  public int compareTo(SimpleDoubleScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(score, other.score);
    return comparison;
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildShortString(this, number -> number.doubleValue() != 0.0, "");
  }

  @Override
  public String toString() {
    return (structuralScore < 0L ? structuralScore + "structural/" : "") + score + "";
  }
}
