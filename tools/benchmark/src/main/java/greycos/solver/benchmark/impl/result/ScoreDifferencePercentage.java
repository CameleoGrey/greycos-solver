package greycos.solver.benchmark.impl.result;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import greycos.solver.core.api.score.Score;

public record ScoreDifferencePercentage(double[] percentageLevels) {

  public static <Score_ extends Score<Score_>>
      ScoreDifferencePercentage calculateScoreDifferencePercentage(
          Score_ baseScore, Score_ valueScore) {
    if (FloatingBenchmarkScoreArithmetic.isFloating(baseScore)
        || FloatingBenchmarkScoreArithmetic.isFloating(valueScore)) {
      var baseLevels = baseScore.toLevelNumbers();
      var valueLevels = valueScore.toLevelNumbers();
      if (baseLevels.length != valueLevels.length) {
        throw new IllegalStateException(
            "The score percentage inputs must have the same level count.");
      }
      var percentages = new double[baseLevels.length];
      for (int i = 0; i < percentages.length; i++) {
        var base = FloatingBenchmarkScoreArithmetic.decimal(baseLevels[i]);
        var difference = FloatingBenchmarkScoreArithmetic.decimal(valueLevels[i]).subtract(base);
        percentages[i] =
            base.signum() == 0
                ? (difference.signum() == 0 ? 0.0 : difference.signum() * Double.POSITIVE_INFINITY)
                : difference.divide(base.abs(), MathContext.DECIMAL128).doubleValue();
      }
      return new ScoreDifferencePercentage(percentages);
    }
    double[] baseLevels = baseScore.toLevelDoubles();
    double[] valueLevels = valueScore.toLevelDoubles();
    if (baseLevels.length != valueLevels.length) {
      throw new IllegalStateException(
          "The baseScore ("
              + baseScore
              + ")'s levelsLength ("
              + baseLevels.length
              + ") is different from the valueScore ("
              + valueScore
              + ")'s levelsLength ("
              + valueLevels.length
              + ").");
    }
    double[] percentageLevels = new double[baseLevels.length];
    for (int i = 0; i < baseLevels.length; i++) {
      percentageLevels[i] = calculateDifferencePercentage(baseLevels[i], valueLevels[i]);
    }
    return new ScoreDifferencePercentage(percentageLevels);
  }

  /**
   * Averages native-floating report percentages without overflowing an intermediate finite sum.
   * Undefined/unbounded percentage levels retain the existing IEEE infinity/NaN conventions.
   */
  static ScoreDifferencePercentage averageFloating(List<ScoreDifferencePercentage> values) {
    int levels = values.getFirst().percentageLevels.length;
    var average = new double[levels];
    for (int i = 0; i < levels; i++) {
      var total = BigDecimal.ZERO;
      double nonFiniteTotal = 0.0;
      boolean hasNonFinite = false;
      for (var value : values) {
        if (value.percentageLevels.length != levels) {
          throw new IllegalStateException("The percentage inputs must have the same level count.");
        }
        double level = value.percentageLevels[i];
        if (Double.isFinite(level)) {
          total = total.add(new BigDecimal(level));
        } else {
          nonFiniteTotal += level;
          hasNonFinite = true;
        }
      }
      average[i] =
          hasNonFinite
              ? nonFiniteTotal
              : total
                  .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL128)
                  .doubleValue();
    }
    return new ScoreDifferencePercentage(average);
  }

  public static double calculateDifferencePercentage(double base, double value) {
    double difference = value - base;
    if (base < 0.0) {
      return difference / -base;
    } else if (base == 0.0) {
      if (difference == 0.0) {
        return 0.0;
      } else {
        // will return Infinity or -Infinity
        return difference / base;
      }
    } else {
      return difference / base;
    }
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  public ScoreDifferencePercentage add(ScoreDifferencePercentage addend) {
    if (percentageLevels.length != addend.percentageLevels().length) {
      throw new IllegalStateException(
          "The addend ("
              + addend
              + ")'s levelsLength ("
              + addend.percentageLevels().length
              + ") is different from the base ("
              + this
              + ")'s levelsLength ("
              + percentageLevels.length
              + ").");
    }
    double[] newPercentageLevels = new double[percentageLevels.length];
    for (int i = 0; i < percentageLevels.length; i++) {
      newPercentageLevels[i] = percentageLevels[i] + addend.percentageLevels[i];
    }
    return new ScoreDifferencePercentage(newPercentageLevels);
  }

  public ScoreDifferencePercentage subtract(ScoreDifferencePercentage subtrahend) {
    if (percentageLevels.length != subtrahend.percentageLevels().length) {
      throw new IllegalStateException(
          "The subtrahend ("
              + subtrahend
              + ")'s levelsLength ("
              + subtrahend.percentageLevels().length
              + ") is different from the base ("
              + this
              + ")'s levelsLength ("
              + percentageLevels.length
              + ").");
    }
    double[] newPercentageLevels = new double[percentageLevels.length];
    for (int i = 0; i < percentageLevels.length; i++) {
      newPercentageLevels[i] = percentageLevels[i] - subtrahend.percentageLevels[i];
    }
    return new ScoreDifferencePercentage(newPercentageLevels);
  }

  public ScoreDifferencePercentage multiply(double multiplicand) {
    double[] newPercentageLevels = new double[percentageLevels.length];
    for (int i = 0; i < percentageLevels.length; i++) {
      newPercentageLevels[i] = percentageLevels[i] * multiplicand;
    }
    return new ScoreDifferencePercentage(newPercentageLevels);
  }

  public ScoreDifferencePercentage divide(double divisor) {
    double[] newPercentageLevels = new double[percentageLevels.length];
    for (int i = 0; i < percentageLevels.length; i++) {
      newPercentageLevels[i] = percentageLevels[i] / divisor;
    }
    return new ScoreDifferencePercentage(newPercentageLevels);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ScoreDifferencePercentage that = (ScoreDifferencePercentage) o;
    return Arrays.equals(percentageLevels, that.percentageLevels);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(percentageLevels);
  }

  @Override
  public String toString() {
    return toString(Locale.US);
  }

  public String toString(Locale locale) {
    StringBuilder s = new StringBuilder(percentageLevels.length * 8);
    DecimalFormat decimalFormat =
        new DecimalFormat("0.00%", DecimalFormatSymbols.getInstance(locale));
    for (int i = 0; i < percentageLevels.length; i++) {
      if (i > 0) {
        s.append("/");
      }
      s.append(decimalFormat.format(percentageLevels[i]));
    }
    return s.toString();
  }
}
