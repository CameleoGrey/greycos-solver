package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 1 finite float business level. Signed zero is canonicalized
 * to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record SimpleFloatScore(long structuralScore, float score)
    implements Score<SimpleFloatScore> {

  public static final SimpleFloatScore ZERO = new SimpleFloatScore(0.0f);
  public static final SimpleFloatScore ONE = new SimpleFloatScore(1.0f);

  public SimpleFloatScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    score = FloatingPointMath.finite(score, "SimpleFloatScore.score");
  }

  public SimpleFloatScore(float score) {
    this(0L, score);
  }

  public static SimpleFloatScore of(float score) {
    return new SimpleFloatScore(score);
  }

  public static SimpleFloatScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseScoreTokens(SimpleFloatScore.class, scoreString, "");
    int offset = tokens.length == 1 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(SimpleFloatScore.class, scoreString, tokens[0]);
    return new SimpleFloatScore(
        structural,
        FloatingPointMath.parseFloat(SimpleFloatScore.class, scoreString, tokens[offset + 0]));
  }

  @Override
  public SimpleFloatScore add(SimpleFloatScore addend) {
    return of(FloatingPointMath.add(score, addend.score));
  }

  @Override
  public SimpleFloatScore subtract(SimpleFloatScore subtrahend) {
    return of(FloatingPointMath.subtract(score, subtrahend.score));
  }

  @Override
  public SimpleFloatScore multiply(double multiplicand) {
    return of(FloatingPointMath.multiply(score, multiplicand));
  }

  @Override
  public SimpleFloatScore divide(double divisor) {
    return of(FloatingPointMath.divide(score, divisor));
  }

  @Override
  public SimpleFloatScore power(double exponent) {
    return of(FloatingPointMath.power(score, exponent));
  }

  @Override
  public SimpleFloatScore abs() {
    return of(Math.abs(score));
  }

  @Override
  public SimpleFloatScore zero() {
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
  public int compareTo(SimpleFloatScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(score, other.score);
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
