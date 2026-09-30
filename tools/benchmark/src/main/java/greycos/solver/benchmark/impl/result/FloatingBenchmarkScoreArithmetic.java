package greycos.solver.benchmark.impl.result;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.score.FloatingPointMath;

/**
 * Benchmark-only arithmetic for native floating scores. Derived totals and differences use the
 * corresponding BigDecimal score layout; business averages retain the original native type and
 * round once, to nearest with ties to even. Other score types keep their existing arithmetic.
 */
public final class FloatingBenchmarkScoreArithmetic {

  private static final BigDecimal FLOAT_UNITS_PER_SCORE =
      new BigDecimal(BigInteger.ONE.shiftLeft(149));
  private static final BigDecimal DOUBLE_UNITS_PER_SCORE =
      new BigDecimal(BigInteger.ONE.shiftLeft(1074));

  private FloatingBenchmarkScoreArithmetic() {}

  public static boolean isFloating(Score<?> score) {
    return score instanceof SimpleFloatScore
        || score instanceof SimpleDoubleScore
        || score instanceof HardSoftFloatScore
        || score instanceof HardSoftDoubleScore
        || score instanceof HardMediumSoftFloatScore
        || score instanceof HardMediumSoftDoubleScore
        || score instanceof BendableFloatScore
        || score instanceof BendableDoubleScore;
  }

  /** Exact represented numeric value, including the full binary value of float/double inputs. */
  public static BigDecimal decimal(Number value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Float || value instanceof Double) {
      return new BigDecimal(value.doubleValue());
    }
    if (value instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    return BigDecimal.valueOf(value.longValue());
  }

  private static BigDecimal[] decimalLevels(Score<?> score) {
    return Arrays.stream(score.toLevelNumbers())
        .map(FloatingBenchmarkScoreArithmetic::decimal)
        .toArray(BigDecimal[]::new);
  }

  private static Score<?> decimalScore(Score<?> prototype, long structural, BigDecimal[] levels) {
    return switch (prototype) {
      case SimpleFloatScore ignored -> new SimpleBigDecimalScore(structural, levels[0]);
      case SimpleDoubleScore ignored -> new SimpleBigDecimalScore(structural, levels[0]);
      case HardSoftFloatScore ignored ->
          new HardSoftBigDecimalScore(structural, levels[0], levels[1]);
      case HardSoftDoubleScore ignored ->
          new HardSoftBigDecimalScore(structural, levels[0], levels[1]);
      case HardMediumSoftFloatScore ignored ->
          new HardMediumSoftBigDecimalScore(structural, levels[0], levels[1], levels[2]);
      case HardMediumSoftDoubleScore ignored ->
          new HardMediumSoftBigDecimalScore(structural, levels[0], levels[1], levels[2]);
      case BendableFloatScore bendable ->
          new BendableBigDecimalScore(
              structural,
              Arrays.copyOfRange(levels, 0, bendable.hardLevelsSize()),
              Arrays.copyOfRange(levels, bendable.hardLevelsSize(), levels.length));
      case BendableDoubleScore bendable ->
          new BendableBigDecimalScore(
              structural,
              Arrays.copyOfRange(levels, 0, bendable.hardLevelsSize()),
              Arrays.copyOfRange(levels, bendable.hardLevelsSize(), levels.length));
      default ->
          throw new IllegalArgumentException(
              "The benchmark score (" + prototype + ") is not a native floating score.");
    };
  }

  public static Score<?> widen(Score<?> score) {
    return isFloating(score)
        ? decimalScore(score, score.structuralScore(), decimalLevels(score))
        : score;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Score add(Score total, Score contribution) {
    if (total == null) {
      return widen(contribution);
    }
    return ((Score) widen(total)).add(widen(contribution));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Score difference(Score value, Score base) {
    return ((Score) widen(value)).subtract(widen(base));
  }

  @SuppressWarnings("rawtypes")
  public static Score average(Score total, int count, Score<?> prototype) {
    if (!isFloating(prototype)) {
      return total.divide(count);
    }
    var decimals = decimalLevels(total);
    boolean floatPrecision =
        prototype instanceof SimpleFloatScore
            || prototype instanceof HardSoftFloatScore
            || prototype instanceof HardMediumSoftFloatScore
            || prototype instanceof BendableFloatScore;
    if (floatPrecision) {
      var levels = new float[decimals.length];
      for (int i = 0; i < levels.length; i++) {
        var units = decimals[i].multiply(FLOAT_UNITS_PER_SCORE).toBigIntegerExact();
        levels[i] = FloatingPointMath.averageFloatUnits(units, count);
      }
      // Score.divide() has always discarded structural state; preserve that report convention.
      return switch (prototype) {
        case SimpleFloatScore ignored -> SimpleFloatScore.of(levels[0]);
        case HardSoftFloatScore ignored -> HardSoftFloatScore.of(levels[0], levels[1]);
        case HardMediumSoftFloatScore ignored ->
            HardMediumSoftFloatScore.of(levels[0], levels[1], levels[2]);
        case BendableFloatScore bendable ->
            BendableFloatScore.of(
                Arrays.copyOfRange(levels, 0, bendable.hardLevelsSize()),
                Arrays.copyOfRange(levels, bendable.hardLevelsSize(), levels.length));
        default -> throw new IllegalStateException("Unsupported float score (" + prototype + ").");
      };
    }
    var levels = new double[decimals.length];
    for (int i = 0; i < levels.length; i++) {
      var units = decimals[i].multiply(DOUBLE_UNITS_PER_SCORE).toBigIntegerExact();
      levels[i] = FloatingPointMath.averageDoubleUnits(units, count);
    }
    return switch (prototype) {
      case SimpleDoubleScore ignored -> SimpleDoubleScore.of(levels[0]);
      case HardSoftDoubleScore ignored -> HardSoftDoubleScore.of(levels[0], levels[1]);
      case HardMediumSoftDoubleScore ignored ->
          HardMediumSoftDoubleScore.of(levels[0], levels[1], levels[2]);
      case BendableDoubleScore bendable ->
          BendableDoubleScore.of(
              Arrays.copyOfRange(levels, 0, bendable.hardLevelsSize()),
              Arrays.copyOfRange(levels, bendable.hardLevelsSize(), levels.length));
      default -> throw new IllegalStateException("Unsupported double score (" + prototype + ").");
    };
  }

  /** A derived difference may exceed the native range even after averaging. */
  @SuppressWarnings("rawtypes")
  public static Score averageDifference(Score total, int count, Score<?> prototype) {
    if (!isFloating(prototype)) {
      return total.divide(count);
    }
    var levels = decimalLevels(total);
    for (int i = 0; i < levels.length; i++) {
      levels[i] = levels[i].divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
    }
    return decimalScore(prototype, 0L, levels);
  }
}
