package greycos.solver.core.api.score;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable lexicographic score with 2 finite double business levels. Signed zero is
 * canonicalized to positive zero. Arithmetic rounds to nearest, ties to even; use {@link
 * FloatingScoreAccumulator} for order-independent, reversible sums.
 */
@NullMarked
public record HardSoftDoubleScore(long structuralScore, double hardScore, double softScore)
    implements Score<HardSoftDoubleScore> {

  public static final HardSoftDoubleScore ZERO = new HardSoftDoubleScore(0.0, 0.0);
  public static final HardSoftDoubleScore ONE_HARD = new HardSoftDoubleScore(1.0, 0.0);
  public static final HardSoftDoubleScore ONE_SOFT = new HardSoftDoubleScore(0.0, 1.0);

  public HardSoftDoubleScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScore = FloatingPointMath.finite(hardScore, "HardSoftDoubleScore.hardScore");
    softScore = FloatingPointMath.finite(softScore, "HardSoftDoubleScore.softScore");
  }

  public HardSoftDoubleScore(double hardScore, double softScore) {
    this(0L, hardScore, softScore);
  }

  public static HardSoftDoubleScore of(double hardScore, double softScore) {
    return new HardSoftDoubleScore(hardScore, softScore);
  }

  public static HardSoftDoubleScore ofHard(double hardScore) {
    return of(hardScore, 0.0);
  }

  public static HardSoftDoubleScore ofSoft(double softScore) {
    return of(0.0, softScore);
  }

  public static HardSoftDoubleScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseScoreTokens(HardSoftDoubleScore.class, scoreString, "hard", "soft");
    int offset = tokens.length == 2 ? 0 : 1;
    long structural =
        offset == 0
            ? 0L
            : ScoreUtil.parseLevelAsLong(HardSoftDoubleScore.class, scoreString, tokens[0]);
    return new HardSoftDoubleScore(
        structural,
        FloatingPointMath.parseDouble(HardSoftDoubleScore.class, scoreString, tokens[offset + 0]),
        FloatingPointMath.parseDouble(HardSoftDoubleScore.class, scoreString, tokens[offset + 1]));
  }

  @Override
  public HardSoftDoubleScore add(HardSoftDoubleScore addend) {
    return of(
        FloatingPointMath.add(hardScore, addend.hardScore),
        FloatingPointMath.add(softScore, addend.softScore));
  }

  @Override
  public HardSoftDoubleScore subtract(HardSoftDoubleScore subtrahend) {
    return of(
        FloatingPointMath.subtract(hardScore, subtrahend.hardScore),
        FloatingPointMath.subtract(softScore, subtrahend.softScore));
  }

  @Override
  public HardSoftDoubleScore multiply(double multiplicand) {
    return of(
        FloatingPointMath.multiply(hardScore, multiplicand),
        FloatingPointMath.multiply(softScore, multiplicand));
  }

  @Override
  public HardSoftDoubleScore divide(double divisor) {
    return of(
        FloatingPointMath.divide(hardScore, divisor), FloatingPointMath.divide(softScore, divisor));
  }

  @Override
  public HardSoftDoubleScore power(double exponent) {
    return of(
        FloatingPointMath.power(hardScore, exponent), FloatingPointMath.power(softScore, exponent));
  }

  @Override
  public HardSoftDoubleScore abs() {
    return of(Math.abs(hardScore), Math.abs(softScore));
  }

  @Override
  public HardSoftDoubleScore zero() {
    return ZERO;
  }

  @Override
  public boolean isFeasible() {
    return structuralScore >= 0L && hardScore >= 0.0;
  }

  @Override
  public Number[] toLevelNumbers() {
    return new Number[] {hardScore, softScore};
  }

  @Override
  public int compareTo(HardSoftDoubleScore other) {
    int comparison = Long.compare(structuralScore, other.structuralScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(hardScore, other.hardScore);
    if (comparison != 0) {
      return comparison;
    }
    comparison = Double.compare(softScore, other.softScore);
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
