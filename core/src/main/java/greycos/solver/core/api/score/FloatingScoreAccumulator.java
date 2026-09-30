package greycos.solver.core.api.score;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

import greycos.solver.core.impl.score.FloatingPointMath;
import greycos.solver.core.impl.score.FloatingScoreSupport;

import org.jspecify.annotations.NullMarked;

/**
 * Reversible, order-independent sums for built-in Float and Double scores.
 *
 * <p>Each business level is accumulated exactly in binary integer units and rounded to nearest,
 * ties to even, only when {@link #extractScore()} is called. Intermediate totals may exceed the
 * floating range; extracting a non-finite result fails. All contributions must have a zero
 * structural score and the same type and dimensions as the initial zero score.
 *
 * <p>Weighted contributions are rounded once to the score's precision before accumulation. The
 * returned contribution can be passed to {@link #subtract(Score)} to undo the operation exactly.
 * Integer weights are multiplied exactly, without first converting them to float or double.
 *
 * <p>Instances are mutable and must be confined to one thread.
 */
@NullMarked
public final class FloatingScoreAccumulator<Score_ extends Score<Score_>> {

  private final Score_ zero;
  private final boolean floatPrecision;
  private final BigInteger[] totals;

  private FloatingScoreAccumulator(Score_ zero) {
    this.zero = Objects.requireNonNull(zero, "zero");
    if (!FloatingScoreSupport.isFloatingScore(zero)
        || !zero.isZero()
        || zero.structuralScore() != 0L) {
      throw new IllegalArgumentException(
          "The initial score (" + zero + ") must be a zero built-in Float or Double score.");
    }
    floatPrecision = FloatingScoreSupport.isFloatScore(zero);
    totals = new BigInteger[FloatingScoreSupport.levelsSize(zero)];
    clear();
  }

  public static <Score_ extends Score<Score_>> FloatingScoreAccumulator<Score_> create(
      Score_ zero) {
    return new FloatingScoreAccumulator<>(zero);
  }

  public void add(Score_ contribution) {
    change(contribution, false);
  }

  public void subtract(Score_ contribution) {
    change(contribution, true);
  }

  private void validate(Score_ score) {
    FloatingScoreSupport.validateCompatible(zero, Objects.requireNonNull(score, "contribution"));
    if (score.structuralScore() != 0L) {
      throw new IllegalArgumentException(
          "The floating contribution (" + score + ") must have a zero structuralScore.");
    }
  }

  private void change(Score_ contribution, boolean subtract) {
    // Type, dimensions and structural score are checked before changing any level. Built-in
    // floating scores are immutable and validate every business level on construction.
    validate(contribution);
    if (floatPrecision) {
      for (int i = 0; i < totals.length; i++) {
        var change = FloatingPointMath.units(FloatingScoreSupport.floatLevel(contribution, i));
        totals[i] = subtract ? totals[i].subtract(change) : totals[i].add(change);
      }
    } else {
      for (int i = 0; i < totals.length; i++) {
        var change = FloatingPointMath.units(FloatingScoreSupport.doubleLevel(contribution, i));
        totals[i] = subtract ? totals[i].subtract(change) : totals[i].add(change);
      }
    }
  }

  public Score_ extractScore() {
    if (floatPrecision) {
      var levels = new float[totals.length];
      for (int i = 0; i < levels.length; i++) {
        try {
          levels[i] = FloatingPointMath.fromFloatUnits(totals[i]);
        } catch (ArithmeticException failure) {
          throw extractionOverflow(i, failure);
        }
      }
      return FloatingScoreSupport.fromFloatLevels(zero, levels);
    }
    var levels = new double[totals.length];
    for (int i = 0; i < levels.length; i++) {
      try {
        levels[i] = FloatingPointMath.fromDoubleUnits(totals[i]);
      } catch (ArithmeticException failure) {
        throw extractionOverflow(i, failure);
      }
    }
    return FloatingScoreSupport.fromDoubleLevels(zero, levels);
  }

  private ArithmeticException extractionOverflow(int level, ArithmeticException cause) {
    var failure =
        new ArithmeticException(
            "The accumulated %s level (%d) exceeds its finite range: %s"
                .formatted(zero.getClass().getSimpleName(), level, cause.getMessage()));
    failure.initCause(cause);
    return failure;
  }

  public void clear() {
    Arrays.fill(totals, BigInteger.ZERO);
  }

  public Score_ addWeighted(Score_ weight, long matchWeight) {
    validate(weight);
    if (floatPrecision) {
      var levels = new float[totals.length];
      for (int i = 0; i < levels.length; i++) {
        levels[i] =
            FloatingPointMath.multiply(FloatingScoreSupport.floatLevel(weight, i), matchWeight);
      }
      return addWeightedLevels(levels);
    }
    var levels = new double[totals.length];
    for (int i = 0; i < levels.length; i++) {
      levels[i] =
          FloatingPointMath.multiply(FloatingScoreSupport.doubleLevel(weight, i), matchWeight);
    }
    return addWeightedLevels(levels);
  }

  public Score_ addWeighted(Score_ weight, float matchWeight) {
    FloatingPointMath.finite(matchWeight, "matchWeight");
    validate(weight);
    if (floatPrecision) {
      var levels = new float[totals.length];
      for (int i = 0; i < levels.length; i++) {
        levels[i] =
            FloatingPointMath.multiply(FloatingScoreSupport.floatLevel(weight, i), matchWeight);
      }
      return addWeightedLevels(levels);
    }
    var levels = new double[totals.length];
    for (int i = 0; i < levels.length; i++) {
      levels[i] =
          FloatingPointMath.multiply(FloatingScoreSupport.doubleLevel(weight, i), matchWeight);
    }
    return addWeightedLevels(levels);
  }

  public Score_ addWeighted(Score_ weight, double matchWeight) {
    FloatingPointMath.finite(matchWeight, "matchWeight");
    validate(weight);
    if (floatPrecision) {
      var levels = new float[totals.length];
      for (int i = 0; i < levels.length; i++) {
        levels[i] =
            FloatingPointMath.multiply(FloatingScoreSupport.floatLevel(weight, i), matchWeight);
      }
      return addWeightedLevels(levels);
    }
    var levels = new double[totals.length];
    for (int i = 0; i < levels.length; i++) {
      levels[i] =
          FloatingPointMath.multiply(FloatingScoreSupport.doubleLevel(weight, i), matchWeight);
    }
    return addWeightedLevels(levels);
  }

  private Score_ addWeightedLevels(float[] levels) {
    // Complete and validate the entire contribution before committing any score level.
    var contribution = FloatingScoreSupport.fromFloatLevels(zero, levels);
    for (int i = 0; i < levels.length; i++) {
      totals[i] = totals[i].add(FloatingPointMath.units(levels[i]));
    }
    return contribution;
  }

  private Score_ addWeightedLevels(double[] levels) {
    var contribution = FloatingScoreSupport.fromDoubleLevels(zero, levels);
    for (int i = 0; i < levels.length; i++) {
      totals[i] = totals[i].add(FloatingPointMath.units(levels[i]));
    }
    return contribution;
  }
}
