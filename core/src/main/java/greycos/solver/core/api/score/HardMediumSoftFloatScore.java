package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 3 finite float business levels. Signed zero is
 * canonicalized to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record HardMediumSoftFloatScore(
    long structuralScore, float hardScore, float mediumScore, float softScore)
    implements Score<HardMediumSoftFloatScore> {

  public static final HardMediumSoftFloatScore ZERO =
      new HardMediumSoftFloatScore(0.0f, 0.0f, 0.0f);
  public static final HardMediumSoftFloatScore ONE_HARD =
      new HardMediumSoftFloatScore(1.0f, 0.0f, 0.0f);
  public static final HardMediumSoftFloatScore ONE_MEDIUM =
      new HardMediumSoftFloatScore(0.0f, 1.0f, 0.0f);
  public static final HardMediumSoftFloatScore ONE_SOFT =
      new HardMediumSoftFloatScore(0.0f, 0.0f, 1.0f);

  public HardMediumSoftFloatScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScore = FloatingPointMath.finite(hardScore, "HardMediumSoftFloatScore.hardScore");
    mediumScore = FloatingPointMath.finite(mediumScore, "HardMediumSoftFloatScore.mediumScore");
    softScore = FloatingPointMath.finite(softScore, "HardMediumSoftFloatScore.softScore");
  }

  public HardMediumSoftFloatScore(float hardScore, float mediumScore, float softScore) {
    this(0L, hardScore, mediumScore, softScore);
  }

  public static HardMediumSoftFloatScore of(float hardScore, float mediumScore, float softScore) {
    return new HardMediumSoftFloatScore(hardScore, mediumScore, softScore);
  }

  public static HardMediumSoftFloatScore ofHard(float hardScore) {
    return of(hardScore, 0.0f, 0.0f);
  }

  public static HardMediumSoftFloatScore ofMedium(float mediumScore) {
    return of(0.0f, mediumScore, 0.0f);
  }

  public static HardMediumSoftFloatScore ofSoft(float softScore) {
    return of(0.0f, 0.0f, softScore);
  }

  public static HardMediumSoftFloatScore parseScore(String scoreString) {
    var tokens =
        ScoreUtil.parseScoreTokens(
            HardMediumSoftFloatScore.class, scoreString, "hard", "medium", "soft");
    int offset = tokens.length == 3 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(HardMediumSoftFloatScore.class, scoreString, tokens[0]);
    return new HardMediumSoftFloatScore(
        structural,
        FloatingPointMath.parseFloat(
            HardMediumSoftFloatScore.class, scoreString, tokens[offset + 0]),
        FloatingPointMath.parseFloat(
            HardMediumSoftFloatScore.class, scoreString, tokens[offset + 1]),
        FloatingPointMath.parseFloat(
            HardMediumSoftFloatScore.class, scoreString, tokens[offset + 2]));
  }

  @Override
  public HardMediumSoftFloatScore add(HardMediumSoftFloatScore addend) {
    return of(
        FloatingPointMath.add(hardScore, addend.hardScore),
        FloatingPointMath.add(mediumScore, addend.mediumScore),
        FloatingPointMath.add(softScore, addend.softScore));
  }

  @Override
  public HardMediumSoftFloatScore subtract(HardMediumSoftFloatScore subtrahend) {
    return of(
        FloatingPointMath.subtract(hardScore, subtrahend.hardScore),
        FloatingPointMath.subtract(mediumScore, subtrahend.mediumScore),
        FloatingPointMath.subtract(softScore, subtrahend.softScore));
  }

  @Override
  public HardMediumSoftFloatScore multiply(double multiplicand) {
    return of(
        FloatingPointMath.multiply(hardScore, multiplicand),
        FloatingPointMath.multiply(mediumScore, multiplicand),
        FloatingPointMath.multiply(softScore, multiplicand));
  }

  @Override
  public HardMediumSoftFloatScore divide(double divisor) {
    return of(
        FloatingPointMath.divide(hardScore, divisor),
        FloatingPointMath.divide(mediumScore, divisor),
        FloatingPointMath.divide(softScore, divisor));
  }

  @Override
  public HardMediumSoftFloatScore power(double exponent) {
    return of(
        FloatingPointMath.power(hardScore, exponent),
        FloatingPointMath.power(mediumScore, exponent),
        FloatingPointMath.power(softScore, exponent));
  }

  @Override
  public HardMediumSoftFloatScore abs() {
    return of(Math.abs(hardScore), Math.abs(mediumScore), Math.abs(softScore));
  }

  @Override
  public HardMediumSoftFloatScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return structuralScore >= 0L && hardScore >= 0.0f;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {hardScore, mediumScore, softScore};
  }

  @Override
  public int compareTo(HardMediumSoftFloatScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(hardScore, other.hardScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(mediumScore, other.mediumScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Float.compare(softScore, other.softScore);
    return comparison;
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildShortString(
        this, number -> number.doubleValue() != 0.0, "hard", "medium", "soft");
  }

  @Override
  public String toString() {
    return (structuralScore < 0L ? structuralScore + "structural/" : "")
        + hardScore
        + "hard"
        + "/"
        + mediumScore
        + "medium"
        + "/"
        + softScore
        + "soft";
  }
}
