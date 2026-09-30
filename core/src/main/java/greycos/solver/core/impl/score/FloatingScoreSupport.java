package greycos.solver.core.impl.score;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.IBendableScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;

/** Shared type and level access for the built-in floating score families. */
public final class FloatingScoreSupport {
  private FloatingScoreSupport() {}

  public static void validateStructuralScore(long structuralScore) {
    if (structuralScore > 0L) {
      throw new IllegalArgumentException(
          "The structuralScore (" + structuralScore + ") must be <= 0.");
    }
  }

  public static boolean isFloatingScore(Score<?> score) {
    return isFloatingScoreClass(score.getClass());
  }

  public static boolean isFloatingScoreClass(Class<?> scoreClass) {
    return scoreClass == SimpleFloatScore.class
        || scoreClass == HardSoftFloatScore.class
        || scoreClass == HardMediumSoftFloatScore.class
        || scoreClass == BendableFloatScore.class
        || scoreClass == SimpleDoubleScore.class
        || scoreClass == HardSoftDoubleScore.class
        || scoreClass == HardMediumSoftDoubleScore.class
        || scoreClass == BendableDoubleScore.class;
  }

  public static boolean isFloatScore(Score<?> score) {
    return score instanceof SimpleFloatScore
        || score instanceof HardSoftFloatScore
        || score instanceof HardMediumSoftFloatScore
        || score instanceof BendableFloatScore;
  }

  public static int levelsSize(Score<?> score) {
    return switch (score) {
      case SimpleFloatScore value -> 1;
      case HardSoftFloatScore value -> 2;
      case HardMediumSoftFloatScore value -> 3;
      case BendableFloatScore value -> value.levelsSize();
      case SimpleDoubleScore value -> 1;
      case HardSoftDoubleScore value -> 2;
      case HardMediumSoftDoubleScore value -> 3;
      case BendableDoubleScore value -> value.levelsSize();
      default -> throw unsupported(score);
    };
  }

  public static Number level(Score<?> score, int index) {
    Objects.checkIndex(index, levelsSize(score));
    return switch (score) {
      case SimpleFloatScore value -> Float.valueOf(value.score());
      case HardSoftFloatScore value ->
          Float.valueOf(index == 0 ? value.hardScore() : value.softScore());
      case HardMediumSoftFloatScore value ->
          Float.valueOf(
              index == 0
                  ? value.hardScore()
                  : index == 1 ? value.mediumScore() : value.softScore());
      case BendableFloatScore value -> Float.valueOf(value.hardOrSoftScore(index));
      case SimpleDoubleScore value -> Double.valueOf(value.score());
      case HardSoftDoubleScore value ->
          Double.valueOf(index == 0 ? value.hardScore() : value.softScore());
      case HardMediumSoftDoubleScore value ->
          Double.valueOf(
              index == 0
                  ? value.hardScore()
                  : index == 1 ? value.mediumScore() : value.softScore());
      case BendableDoubleScore value -> Double.valueOf(value.hardOrSoftScore(index));
      default -> throw unsupported(score);
    };
  }

  /** Reads a Float score level without allocating a boxed number. */
  public static float floatLevel(Score<?> score, int index) {
    Objects.checkIndex(index, levelsSize(score));
    return switch (score) {
      case SimpleFloatScore value -> value.score();
      case HardSoftFloatScore value -> index == 0 ? value.hardScore() : value.softScore();
      case HardMediumSoftFloatScore value ->
          index == 0 ? value.hardScore() : index == 1 ? value.mediumScore() : value.softScore();
      case BendableFloatScore value -> value.hardOrSoftScore(index);
      default ->
          throw new IllegalArgumentException(
              "The score type (%s) is not a built-in Float score."
                  .formatted(score.getClass().getName()));
    };
  }

  /** Reads a Double score level without allocating a boxed number. */
  public static double doubleLevel(Score<?> score, int index) {
    Objects.checkIndex(index, levelsSize(score));
    return switch (score) {
      case SimpleDoubleScore value -> value.score();
      case HardSoftDoubleScore value -> index == 0 ? value.hardScore() : value.softScore();
      case HardMediumSoftDoubleScore value ->
          index == 0 ? value.hardScore() : index == 1 ? value.mediumScore() : value.softScore();
      case BendableDoubleScore value -> value.hardOrSoftScore(index);
      default ->
          throw new IllegalArgumentException(
              "The score type (%s) is not a built-in Double score."
                  .formatted(score.getClass().getName()));
    };
  }

  /** Constructs a Float score without boxing its levels. */
  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> Score_ fromFloatLevels(
      Score_ prototype, float[] levels) {
    if (levels.length != levelsSize(prototype)) {
      throw new IllegalArgumentException(
          "The floating score level count (%d) must equal %d."
              .formatted(levels.length, levelsSize(prototype)));
    }
    return (Score_)
        switch (prototype) {
          case SimpleFloatScore value -> new SimpleFloatScore(value.structuralScore(), levels[0]);
          case HardSoftFloatScore value ->
              new HardSoftFloatScore(value.structuralScore(), levels[0], levels[1]);
          case HardMediumSoftFloatScore value ->
              new HardMediumSoftFloatScore(
                  value.structuralScore(), levels[0], levels[1], levels[2]);
          case BendableFloatScore value ->
              new BendableFloatScore(
                  value.structuralScore(),
                  Arrays.copyOfRange(levels, 0, value.hardLevelsSize()),
                  Arrays.copyOfRange(levels, value.hardLevelsSize(), levels.length));
          default ->
              throw new IllegalArgumentException(
                  "The score type (%s) is not a built-in Float score."
                      .formatted(prototype.getClass().getName()));
        };
  }

  /** Constructs a Double score without boxing its levels. */
  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> Score_ fromDoubleLevels(
      Score_ prototype, double[] levels) {
    if (levels.length != levelsSize(prototype)) {
      throw new IllegalArgumentException(
          "The floating score level count (%d) must equal %d."
              .formatted(levels.length, levelsSize(prototype)));
    }
    return (Score_)
        switch (prototype) {
          case SimpleDoubleScore value -> new SimpleDoubleScore(value.structuralScore(), levels[0]);
          case HardSoftDoubleScore value ->
              new HardSoftDoubleScore(value.structuralScore(), levels[0], levels[1]);
          case HardMediumSoftDoubleScore value ->
              new HardMediumSoftDoubleScore(
                  value.structuralScore(), levels[0], levels[1], levels[2]);
          case BendableDoubleScore value ->
              new BendableDoubleScore(
                  value.structuralScore(),
                  Arrays.copyOfRange(levels, 0, value.hardLevelsSize()),
                  Arrays.copyOfRange(levels, value.hardLevelsSize(), levels.length));
          default ->
              throw new IllegalArgumentException(
                  "The score type (%s) is not a built-in Double score."
                      .formatted(prototype.getClass().getName()));
        };
  }

  public static void validateCompatible(Score<?> prototype, Score<?> score) {
    if (prototype.getClass() != score.getClass()
        || prototype instanceof IBendableScore<?> left
            && score instanceof IBendableScore<?> right
            && (left.hardLevelsSize() != right.hardLevelsSize()
                || left.softLevelsSize() != right.softLevelsSize())) {
      throw new IllegalArgumentException(
          "The floating scores ("
              + prototype
              + ", "
              + score
              + ") must have the same type and level dimensions.");
    }
  }

  /** Constructs a score with the prototype's type, dimensions and structural score. */
  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> Score_ fromLevels(
      Score_ prototype, Number[] levels) {
    if (levels.length != levelsSize(prototype)) {
      throw new IllegalArgumentException(
          "The floating score level count ("
              + levels.length
              + ") must equal "
              + levelsSize(prototype)
              + ".");
    }
    return (Score_)
        switch (prototype) {
          case SimpleFloatScore value ->
              new SimpleFloatScore(value.structuralScore(), FloatingPointMath.toFloat(levels[0]));
          case HardSoftFloatScore value ->
              new HardSoftFloatScore(
                  value.structuralScore(),
                  FloatingPointMath.toFloat(levels[0]),
                  FloatingPointMath.toFloat(levels[1]));
          case HardMediumSoftFloatScore value ->
              new HardMediumSoftFloatScore(
                  value.structuralScore(),
                  FloatingPointMath.toFloat(levels[0]),
                  FloatingPointMath.toFloat(levels[1]),
                  FloatingPointMath.toFloat(levels[2]));
          case BendableFloatScore value ->
              new BendableFloatScore(
                  value.structuralScore(),
                  toFloatArray(levels, 0, value.hardLevelsSize()),
                  toFloatArray(levels, value.hardLevelsSize(), levels.length));
          case SimpleDoubleScore value ->
              new SimpleDoubleScore(value.structuralScore(), FloatingPointMath.toDouble(levels[0]));
          case HardSoftDoubleScore value ->
              new HardSoftDoubleScore(
                  value.structuralScore(),
                  FloatingPointMath.toDouble(levels[0]),
                  FloatingPointMath.toDouble(levels[1]));
          case HardMediumSoftDoubleScore value ->
              new HardMediumSoftDoubleScore(
                  value.structuralScore(),
                  FloatingPointMath.toDouble(levels[0]),
                  FloatingPointMath.toDouble(levels[1]),
                  FloatingPointMath.toDouble(levels[2]));
          case BendableDoubleScore value ->
              new BendableDoubleScore(
                  value.structuralScore(),
                  toDoubleArray(levels, 0, value.hardLevelsSize()),
                  toDoubleArray(levels, value.hardLevelsSize(), levels.length));
          default -> throw unsupported(prototype);
        };
  }

  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> Score_ withStructuralScore(
      Score_ score, long structuralScore) {
    return (Score_)
        switch (score) {
          case SimpleFloatScore value -> new SimpleFloatScore(structuralScore, value.score());
          case HardSoftFloatScore value ->
              new HardSoftFloatScore(structuralScore, value.hardScore(), value.softScore());
          case HardMediumSoftFloatScore value ->
              new HardMediumSoftFloatScore(
                  structuralScore, value.hardScore(), value.mediumScore(), value.softScore());
          case BendableFloatScore value ->
              new BendableFloatScore(structuralScore, value.hardScores(), value.softScores());
          case SimpleDoubleScore value -> new SimpleDoubleScore(structuralScore, value.score());
          case HardSoftDoubleScore value ->
              new HardSoftDoubleScore(structuralScore, value.hardScore(), value.softScore());
          case HardMediumSoftDoubleScore value ->
              new HardMediumSoftDoubleScore(
                  structuralScore, value.hardScore(), value.mediumScore(), value.softScore());
          case BendableDoubleScore value ->
              new BendableDoubleScore(structuralScore, value.hardScores(), value.softScores());
          default -> throw unsupported(score);
        };
  }

  public static BigDecimal exact(Number value) {
    return FloatingPointMath.exact(value);
  }

  private static float[] toFloatArray(Number[] levels, int start, int end) {
    var values = new float[end - start];
    for (int i = start; i < end; i++) {
      values[i - start] = FloatingPointMath.toFloat(levels[i]);
    }
    return values;
  }

  private static double[] toDoubleArray(Number[] levels, int start, int end) {
    var values = new double[end - start];
    for (int i = start; i < end; i++) {
      values[i - start] = FloatingPointMath.toDouble(levels[i]);
    }
    return values;
  }

  private static IllegalArgumentException unsupported(Score<?> score) {
    return new IllegalArgumentException(
        "The score type ("
            + score.getClass().getName()
            + ") is not a built-in Float or Double score.");
  }
}
