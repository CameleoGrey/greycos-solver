package greycos.solver.core.impl.localsearch.decider.acceptor.greatdeluge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;

/** Integral water bounds retain the ordinary score path until an intermediate would overflow. */
final class IntegralWaterLevel {

  private static final BigDecimal MINIMUM = BigDecimal.valueOf(Long.MIN_VALUE);
  private static final BigDecimal MAXIMUM = BigDecimal.valueOf(Long.MAX_VALUE);

  private final Score<?> prototype;
  private final long[] startingLevels;
  private final long[] increments;
  private final long[] currentLevels;
  private final BigDecimal[] extendedLevels;
  private final byte[] range;
  private boolean widened;

  static IntegralWaterLevel create(Score<?> start, Score<?> increment) {
    if (!(start instanceof SimpleScore
        || start instanceof HardSoftScore
        || start instanceof HardMediumSoftScore
        || start instanceof BendableScore)) {
      return null;
    }
    return new IntegralWaterLevel(start, increment == null ? null : increment.toLevelNumbers());
  }

  private IntegralWaterLevel(Score<?> prototype, Number[] incrementLevels) {
    this.prototype = prototype;
    var levels = prototype.toLevelNumbers();
    startingLevels = new long[levels.length];
    currentLevels = new long[levels.length];
    increments = incrementLevels == null ? null : new long[levels.length];
    extendedLevels = new BigDecimal[levels.length];
    range = new byte[levels.length];
    for (int i = 0; i < levels.length; i++) {
      startingLevels[i] = levels[i].longValue();
      currentLevels[i] = startingLevels[i];
      if (increments != null) {
        increments[i] = incrementLevels[i].longValue();
      }
    }
  }

  boolean isWidened() {
    return widened;
  }

  /** Builds the finite bound once, without repeating the arithmetic that checked its range. */
  Score<?> toScore() {
    return switch (prototype) {
      case SimpleScore ignored -> SimpleScore.of(currentLevels[0]);
      case HardSoftScore ignored -> HardSoftScore.of(currentLevels[0], currentLevels[1]);
      case HardMediumSoftScore ignored ->
          HardMediumSoftScore.of(currentLevels[0], currentLevels[1], currentLevels[2]);
      case BendableScore bendable ->
          BendableScore.of(
              Arrays.copyOfRange(currentLevels, 0, bendable.hardLevelsSize()),
              Arrays.copyOfRange(currentLevels, bendable.hardLevelsSize(), currentLevels.length));
      default -> throw new IllegalStateException("Unsupported integral water score: " + prototype);
    };
  }

  void increment() {
    for (int i = 0; i < currentLevels.length; i++) {
      if (extendedLevels[i] != null) {
        setExtended(i, extendedLevels[i].add(BigDecimal.valueOf(increments[i])));
      } else {
        try {
          currentLevels[i] = Math.addExact(currentLevels[i], increments[i]);
        } catch (ArithmeticException overflow) {
          setExtended(
              i, BigDecimal.valueOf(currentLevels[i]).add(BigDecimal.valueOf(increments[i])));
        }
      }
    }
  }

  void applyRatio(double ratio, BigDecimal extendedRatio) {
    for (int i = 0; i < currentLevels.length; i++) {
      long start = startingLevels[i];
      if (extendedRatio == null && start != Long.MIN_VALUE) {
        double product = Math.abs(start) * ratio;
        // The upper bound is exclusive: (double) Long.MAX_VALUE is already 2^63.
        if (Double.isFinite(product) && product < 0x1.0p63) {
          try {
            currentLevels[i] = Math.addExact(start, (long) Math.floor(product));
            extendedLevels[i] = null;
            range[i] = 0;
            continue;
          } catch (ArithmeticException overflow) {
            // Widen before applying either the multiplication or addition to a public score.
          }
        }
      }
      var exactStart = BigDecimal.valueOf(start);
      var exactRatio = extendedRatio == null ? FloatingScoreSupport.exact(ratio) : extendedRatio;
      setExtended(
          i, exactStart.add(exactStart.abs().multiply(exactRatio).setScale(0, RoundingMode.FLOOR)));
    }
  }

  private void setExtended(int index, BigDecimal value) {
    widened = true;
    extendedLevels[index] = value;
    if (value.compareTo(MAXIMUM) > 0) {
      range[index] = 1;
    } else if (value.compareTo(MINIMUM) < 0) {
      range[index] = -1;
    } else {
      range[index] = 0;
      currentLevels[index] = value.longValueExact();
    }
  }

  int compare(Score<?> score) {
    var levels = score.toLevelNumbers();
    for (int i = 0; i < currentLevels.length; i++) {
      int comparison =
          range[i] == 0 ? Long.compare(levels[i].longValue(), currentLevels[i]) : -range[i];
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }
}
