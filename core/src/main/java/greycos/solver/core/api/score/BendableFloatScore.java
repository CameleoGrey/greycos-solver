package greycos.solver.core.api.score;

import java.util.Arrays;
import java.util.Objects;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreUtil;

import org.jspecify.annotations.NullMarked;

/** Immutable lexicographic finite float score with configurable hard and soft levels. */
@NullMarked
public record BendableFloatScore(long structuralScore, float[] hardScores, float[] softScores)
    implements IBendableScore<BendableFloatScore> {

  public BendableFloatScore {
    FloatingScoreSupport.validateStructuralScore(structuralScore);
    hardScores = Objects.requireNonNull(hardScores, "hardScores").clone();
    softScores = Objects.requireNonNull(softScores, "softScores").clone();
    validateDimensions(hardScores.length, softScores.length);
    for (int i = 0; i < hardScores.length; i++) {
      hardScores[i] =
          FloatingPointMath.finite(hardScores[i], "BendableFloatScore.hardScores[" + i + "]");
    }
    for (int i = 0; i < softScores.length; i++) {
      softScores[i] =
          FloatingPointMath.finite(softScores[i], "BendableFloatScore.softScores[" + i + "]");
    }
  }

  public BendableFloatScore(float[] hardScores, float[] softScores) {
    this(0L, hardScores, softScores);
  }

  @Override
  public float[] hardScores() {
    return hardScores.clone();
  }

  @Override
  public float[] softScores() {
    return softScores.clone();
  }

  public static BendableFloatScore of(float[] hardScores, float[] softScores) {
    return new BendableFloatScore(hardScores, softScores);
  }

  public static BendableFloatScore zero(int hardLevelsSize, int softLevelsSize) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    return of(new float[hardLevelsSize], new float[softLevelsSize]);
  }

  public static BendableFloatScore ofHard(
      int hardLevelsSize, int softLevelsSize, int hardLevel, float hardScore) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    var hard = new float[hardLevelsSize];
    hard[hardLevel] = hardScore;
    return of(hard, new float[softLevelsSize]);
  }

  public static BendableFloatScore ofSoft(
      int hardLevelsSize, int softLevelsSize, int softLevel, float softScore) {
    validateDimensions(hardLevelsSize, softLevelsSize);
    var soft = new float[softLevelsSize];
    soft[softLevel] = softScore;
    return of(new float[hardLevelsSize], soft);
  }

  public static BendableFloatScore parseScore(String scoreString) {
    var tokens = ScoreUtil.parseBendableScoreTokens(BendableFloatScore.class, scoreString);
    long structural =
        tokens[0] == null
            ? 0L
            : ScoreUtil.parseLevelAsLong(BendableFloatScore.class, scoreString, tokens[0][0]);
    var hard = new float[tokens[1].length];
    var soft = new float[tokens[2].length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.parseFloat(BendableFloatScore.class, scoreString, tokens[1][i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.parseFloat(BendableFloatScore.class, scoreString, tokens[2][i]);
    }
    return new BendableFloatScore(structural, hard, soft);
  }

  @Override
  public int hardLevelsSize() {
    return hardScores.length;
  }

  @Override
  public int softLevelsSize() {
    return softScores.length;
  }

  public float hardScore(int index) {
    return hardScores[index];
  }

  public float softScore(int index) {
    return softScores[index];
  }

  public float hardOrSoftScore(int index) {
    return index < hardScores.length ? hardScores[index] : softScores[index - hardScores.length];
  }

  @Override
  public boolean isFeasible() {
    if (structuralScore < 0L) {
      return false;
    }
    for (float value : hardScores) {
      if (value < 0.0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public BendableFloatScore add(BendableFloatScore addend) {
    validateCompatible(addend);
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.add(hardScores[i], addend.hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.add(softScores[i], addend.softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore subtract(BendableFloatScore subtrahend) {
    validateCompatible(subtrahend);
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.subtract(hardScores[i], subtrahend.hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.subtract(softScores[i], subtrahend.softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore multiply(double multiplicand) {
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.multiply(hardScores[i], multiplicand);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.multiply(softScores[i], multiplicand);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore divide(double divisor) {
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.divide(hardScores[i], divisor);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.divide(softScores[i], divisor);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore power(double exponent) {
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = FloatingPointMath.power(hardScores[i], exponent);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = FloatingPointMath.power(softScores[i], exponent);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore abs() {
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = Math.abs(hardScores[i]);
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = Math.abs(softScores[i]);
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore negate() {
    var hard = new float[hardScores.length];
    var soft = new float[softScores.length];
    for (int i = 0; i < hard.length; i++) {
      hard[i] = -hardScores[i];
    }
    for (int i = 0; i < soft.length; i++) {
      soft[i] = -softScores[i];
    }
    return of(hard, soft);
  }

  @Override
  public BendableFloatScore zero() {
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
  public int compareTo(BendableFloatScore other) {
    validateCompatible(other);
    int comparison = Long.compare(structuralScore, other.structuralScore);
    for (int i = 0; comparison == 0 && i < levelsSize(); i++) {
      comparison = Float.compare(hardOrSoftScore(i), other.hardOrSoftScore(i));
    }
    return comparison;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof BendableFloatScore score
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

  private void validateCompatible(BendableFloatScore other) {
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
