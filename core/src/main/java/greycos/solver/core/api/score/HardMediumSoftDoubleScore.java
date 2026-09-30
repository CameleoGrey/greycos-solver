package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 3 finite double business levels. Signed zero is
 * canonicalized to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record HardMediumSoftDoubleScore(
    long structuralScore, double hardScore, double mediumScore, double softScore)
    implements Score<HardMediumSoftDoubleScore> {

  public static final HardMediumSoftDoubleScore ZERO = new HardMediumSoftDoubleScore(0.0, 0.0, 0.0);
  public static final HardMediumSoftDoubleScore ONE_HARD =
      new HardMediumSoftDoubleScore(1.0, 0.0, 0.0);
  public static final HardMediumSoftDoubleScore ONE_MEDIUM =
      new HardMediumSoftDoubleScore(0.0, 1.0, 0.0);
  public static final HardMediumSoftDoubleScore ONE_SOFT =
      new HardMediumSoftDoubleScore(0.0, 0.0, 1.0);

  public HardMediumSoftDoubleScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScore = FloatingPointMath.finite(hardScore, "HardMediumSoftDoubleScore.hardScore");
    mediumScore = FloatingPointMath.finite(mediumScore, "HardMediumSoftDoubleScore.mediumScore");
    softScore = FloatingPointMath.finite(softScore, "HardMediumSoftDoubleScore.softScore");
  }

  public HardMediumSoftDoubleScore(double hardScore, double mediumScore, double softScore) {
    this(0L, hardScore, mediumScore, softScore);
  }

  public static HardMediumSoftDoubleScore of(
      double hardScore, double mediumScore, double softScore) {
    return new HardMediumSoftDoubleScore(hardScore, mediumScore, softScore);
  }

  public static HardMediumSoftDoubleScore ofHard(double hardScore) {
    return of(hardScore, 0.0, 0.0);
  }

  public static HardMediumSoftDoubleScore ofMedium(double mediumScore) {
    return of(0.0, mediumScore, 0.0);
  }

  public static HardMediumSoftDoubleScore ofSoft(double softScore) {
    return of(0.0, 0.0, softScore);
  }

  public static HardMediumSoftDoubleScore parseScore(String scoreString) {
    var tokens =
        ScoreUtil.parseScoreTokens(
            HardMediumSoftDoubleScore.class, scoreString, "hard", "medium", "soft");
    int offset = tokens.length == 3 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(HardMediumSoftDoubleScore.class, scoreString, tokens[0]);
    return new HardMediumSoftDoubleScore(
        structural,
        FloatingPointMath.parseDouble(
            HardMediumSoftDoubleScore.class, scoreString, tokens[offset + 0]),
        FloatingPointMath.parseDouble(
            HardMediumSoftDoubleScore.class, scoreString, tokens[offset + 1]),
        FloatingPointMath.parseDouble(
            HardMediumSoftDoubleScore.class, scoreString, tokens[offset + 2]));
  }

  @Override
  public HardMediumSoftDoubleScore add(HardMediumSoftDoubleScore addend) {
    return of(
        FloatingPointMath.add(hardScore, addend.hardScore),
        FloatingPointMath.add(mediumScore, addend.mediumScore),
        FloatingPointMath.add(softScore, addend.softScore));
  }

  @Override
  public HardMediumSoftDoubleScore subtract(HardMediumSoftDoubleScore subtrahend) {
    return of(
        FloatingPointMath.subtract(hardScore, subtrahend.hardScore),
        FloatingPointMath.subtract(mediumScore, subtrahend.mediumScore),
        FloatingPointMath.subtract(softScore, subtrahend.softScore));
  }

  @Override
  public HardMediumSoftDoubleScore multiply(double multiplicand) {
    return of(
        FloatingPointMath.multiply(hardScore, multiplicand),
        FloatingPointMath.multiply(mediumScore, multiplicand),
        FloatingPointMath.multiply(softScore, multiplicand));
  }

  @Override
  public HardMediumSoftDoubleScore divide(double divisor) {
    return of(
        FloatingPointMath.divide(hardScore, divisor),
        FloatingPointMath.divide(mediumScore, divisor),
        FloatingPointMath.divide(softScore, divisor));
  }

  @Override
  public HardMediumSoftDoubleScore power(double exponent) {
    return of(
        FloatingPointMath.power(hardScore, exponent),
        FloatingPointMath.power(mediumScore, exponent),
        FloatingPointMath.power(softScore, exponent));
  }

  @Override
  public HardMediumSoftDoubleScore abs() {
    return of(Math.abs(hardScore), Math.abs(mediumScore), Math.abs(softScore));
  }

  @Override
  public HardMediumSoftDoubleScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return structuralScore >= 0L && hardScore >= 0.0;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {hardScore, mediumScore, softScore};
  }

  @Override
  public int compareTo(HardMediumSoftDoubleScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(hardScore, other.hardScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(mediumScore, other.mediumScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(softScore, other.softScore);
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
