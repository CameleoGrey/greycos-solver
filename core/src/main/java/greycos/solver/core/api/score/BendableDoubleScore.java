package greycos.solver.core.api.score;

import java.util.Arrays;
import java.util.Objects;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/** Immutable lexicographic finite double score with configurable hard and soft levels. */
@NullMarked
public record BendableDoubleScore(long structuralScore, double[] hardScores, double[] softScores)
    implements IBendableScore<BendableDoubleScore> {

  public BendableDoubleScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScores = Objects.requireNonNull(hardScores, "hardScores").clone();
    softScores = Objects.requireNonNull(softScores, "softScores").clone();
    validateDimensions(hardScores.length, softScores.length);
    for (int i = 0; i < hardScores.length; i++) {
      hardScores[i] =
          FloatingPointMath.finite(hardScores[i], "BendableDoubleScore.hardScores[" + i + "]");
    }
    for (int i = 0; i < softScores.length; i++) {
      softScores[i] =
          FloatingPointMath.finite(softScores[i], "BendableDoubleScore.softScores[" + i + "]");
    }
  }

  public BendableDoubleScore(double[] hardScores, double[] softScores) {
    this(0L, hardScores, softScores);
  }

  @Override
  public double[] hardScores() {
    return hardScores.clone();
  }

  @Override
  public double[] softScores() {
    return softScores.clone();
  }

  public static BendableDoubleScore of(double[] hardScores, double[] softScores) {
    return new BendableDoubleScore(hardScores, softScores);
  }

  public static BendableDoubleScore zero(int hardLevelsSize, int softLevelsSize) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    return of(new double[hardLevelsSize], new double[softLevelsSize]);
  }

  public static BendableDoubleScore ofHard(
      int hardLevelsSize, int softLevelsSize, int hardLevel, double hardScore) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    var hard = new double[hardLevelsSize];
    hard[hardLevel] = hardScore;
    return of(hard, new double[softLevelsSize]);
  }

  public static BendableDoubleScore ofSoft(
      int hardLevelsSize, int softLevelsSize, int softLevel, double softScore) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    var soft = new double[softLevelsSize];
    soft[softLevel] = softScore;
    return of(new double[hardLevelsSize], soft);
  }

  public static BendableDoubleScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseBendableScoreTokens(BendableDoubleScore.class, scoreString);
    long structural =
        tokens[0] == null
            ? 0L
            : ScoreUtil.parseLevelAsLong(BendableDoubleScore.class, scoreString, tokens[0][0]);
    var hard = new double[tokens[1].length];
    var soft = new double[tokens[2].length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.parseDouble(BendableDoubleScore.class, scoreString, tokens[1][i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.parseDouble(BendableDoubleScore.class, scoreString, tokens[2][i]);
    }
    return new BendableDoubleScore(structural, hard, soft);
  }

  @Override
  public int hardLevelsSize() {
    return hardScores.length;
  }

  @Override
  public int softLevelsSize() {
    return softScores.length;
  }

  public double hardScore(int index) {
    return hardScores[index];
  }

  public double softScore(int index) {
    return softScores[index];
  }

  public double hardOrSoftScore(int index) {
    return index < hardScores.length ? hardScores[index] : softScores[index - hardScores.length];
  }

  @Override
  public boolean isFeasible() {
    if (structuralScore < 0L) {
      return false;
    }
    for (double value : hardScores) {
      if (value < 0.0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public BendableDoubleScore add(BendableDoubleScore addend) {
    validateCompatible(addend);
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.add(hardScores[i], addend.hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.add(softScores[i], addend.softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore subtract(BendableDoubleScore subtrahend) {
    validateCompatible(subtrahend);
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.subtract(hardScores[i], subtrahend.hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.subtract(softScores[i], subtrahend.softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore multiply(double multiplicand) {
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.multiply(hardScores[i], multiplicand);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.multiply(softScores[i], multiplicand);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore divide(double divisor) {
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.divide(hardScores[i], divisor);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.divide(softScores[i], divisor);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore power(double exponent) {
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.power(hardScores[i], exponent);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.power(softScores[i], exponent);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore abs() {
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = Math.abs(hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = Math.abs(softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore negate() {
    var hard = new double[hardScores.length];
    var soft = new double[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = -hardScores[i];
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = -softScores[i];
    }
    return of(hard, soft);
  }

  @Override
  public BendableDoubleScore zero() {
    return zero(hardScores.length, softScores.length);
  }

  @Override
  public Number[] toLevelNumbers() {
    var levels = new Number[levelsSize()];
    for (int i = 0; i < levels.length; i++) {
      levels[i] = hardOrSoftScore(i);
    }
    return levels;
  }

  @Override
  public int compareTo(BendableDoubleScore other) {
    validateCompatible(other);
    int comparison = Long.compare(structuralScore, other.structuralScore);
    for (int i = 0; comparison == 0 && i < levelsSize(); i++) {
      comparison = Double.compare(hardOrSoftScore(i), other.hardOrSoftScore(i));
    }
    return comparison;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BendableDoubleScore score
        && structuralScore == score.structuralScore
        && Arrays.equals(hardScores, score.hardScores)
        && Arrays.equals(softScores, score.softScores);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Long.hashCode(structuralScore) + Arrays.hashCode(hardScores))
        + Arrays.hashCode(softScores);
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildBendableShortString(this, number -> number.doubleValue() != 0.0);
  }

  @Override
  public String toString() {
    var text = new StringBuilder();
    if (structuralScore < 0L) {
      text.append(structuralScore).append("structural/");
    }
    text.append('[');
    for (int i = 0; i < hardScores.length; i++) {
      if (i > 0) {
        text.append('/');
      }
      text.append(hardScores[i]);
    }
    text.append("]hard/[");
    for (int i = 0; i < softScores.length; i++) {
      if (i > 0) {
        text.append('/');
      }
      text.append(softScores[i]);
    }
    return text.append("]soft").toString();
  }

  private void validateCompatible(BendableDoubleScore other) {
    if (hardLevelsSize() != other.hardLevelsSize() || softLevelsSize() != other.softLevelsSize()) {
      throw new IllegalArgumentException(
          "The bendable scores (" + this + ", " + other + ") have incompatible dimensions.");
    }
  }

  private static void validateDimensions(int hard, int soft) {
    if (hard < 0 || soft < 0 || (long) hard + soft < 1L) {
      throw new IllegalArgumentException(
          "The hardLevelsSize ("
              + hard
              + ") and softLevelsSize ("
              + soft
              + ") must be nonnegative with at least one total level.");
    }
  }
}
