package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 2 finite float business levels. Signed zero is
 * canonicalized to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record HardSoftFloatScore(long structuralScore, float hardScore, float softScore)
    implements Score<HardSoftFloatScore> {

  public static final HardSoftFloatScore ZERO = new HardSoftFloatScore(0.0f, 0.0f);
  public static final HardSoftFloatScore ONE_HARD = new HardSoftFloatScore(1.0f, 0.0f);
  public static final HardSoftFloatScore ONE_SOFT = new HardSoftFloatScore(0.0f, 1.0f);

  public HardSoftFloatScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScore = FloatingPointMath.finite(hardScore, "HardSoftFloatScore.hardScore");
    softScore = FloatingPointMath.finite(softScore, "HardSoftFloatScore.softScore");
  }

  public HardSoftFloatScore(float hardScore, float softScore) {
    this(0L, hardScore, softScore);
  }

  public static HardSoftFloatScore of(float hardScore, float softScore) {
    return new HardSoftFloatScore(hardScore, softScore);
  }

  public static HardSoftFloatScore ofHard(float hardScore) {
    return of(hardScore, 0.0f);
  }

  public static HardSoftFloatScore ofSoft(float softScore) {
    return of(0.0f, softScore);
  }

  public static HardSoftFloatScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseScoreTokens(HardSoftFloatScore.class, scoreString, "hard", "soft");
    int offset = tokens.length == 2 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(HardSoftFloatScore.class, scoreString, tokens[0]);
    return new HardSoftFloatScore(
        structural,
        FloatingPointMath.parseFloat(HardSoftFloatScore.class, scoreString, tokens[offset + 0]),
        FloatingPointMath.parseFloat(HardSoftFloatScore.class, scoreString, tokens[offset + 1]));
  }

  @Override
  public HardSoftFloatScore add(HardSoftFloatScore addend) {
    return of(
        FloatingPointMath.add(hardScore, addend.hardScore),
        FloatingPointMath.add(softScore, addend.softScore));
  }

  @Override
  public HardSoftFloatScore subtract(HardSoftFloatScore subtrahend) {
    return of(
        FloatingPointMath.subtract(hardScore, subtrahend.hardScore),
        FloatingPointMath.subtract(softScore, subtrahend.softScore));
  }

  @Override
  public HardSoftFloatScore multiply(double multiplicand) {
    return of(
        FloatingPointMath.multiply(hardScore, multiplicand),
        FloatingPointMath.multiply(softScore, multiplicand));
  }

  @Override
  public HardSoftFloatScore divide(double divisor) {
    return of(
        FloatingPointMath.divide(hardScore, divisor), FloatingPointMath.divide(softScore, divisor));
  }

  @Override
  public HardSoftFloatScore power(double exponent) {
    return of(
        FloatingPointMath.power(hardScore, exponent), FloatingPointMath.power(softScore, exponent));
  }

  @Override
  public HardSoftFloatScore abs() {
    return of(Math.abs(hardScore), Math.abs(softScore));
  }

  @Override
  public HardSoftFloatScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return structuralScore >= 0L && hardScore >= 0.0f;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {hardScore, softScore};
  }

  @Override
  public int compareTo(HardSoftFloatScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(hardScore, other.hardScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(softScore, other.softScore);
    return comparison;
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildShortString(this, number -> number.doubleValue() != 0.0, "hard", "soft");
  }

  @Override
  public String toString() {
    return (structuralScore < 0L ? structuralScore + "structural/" : "")
        + hardScore
        + "hard"
        + "/"
        + softScore
        + "soft";
  }
}
