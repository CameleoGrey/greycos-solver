package greycos.solver.benchmark.impl.statistic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import greycos.solver.benchmark.impl.result.BenchmarkResult;
import greycos.solver.benchmark.impl.result.FloatingBenchmarkScoreArithmetic;
import greycos.solver.core.api.score.Score;

public class StatisticUtils {

  private StatisticUtils() {
    // This class is not instantiable
  }

  /**
   * Calculates standard deviation of {@link BenchmarkResult#getAverageScore()}s from {@code
   * averageScore}.
   *
   * @param averageScore not null
   * @return standard deviation double values
   */
  public static double[] determineStandardDeviationDoubles(
      List<? extends BenchmarkResult> benchmarkResultList, Score averageScore, int successCount) {
    if (successCount <= 0) {
      return new double[0];
    }
    if (averageScore == null) {
      throw new IllegalArgumentException("Average score (" + averageScore + ") cannot be null.");
    }
    if (FloatingBenchmarkScoreArithmetic.isFloating(averageScore)) {
      return floatingStandardDeviation(benchmarkResultList, averageScore, successCount);
    }
    // averageScore can no longer be null
    double[] differenceSquaredTotalDoubles = null;
    for (BenchmarkResult benchmarkResult : benchmarkResultList) {
      if (benchmarkResult.hasAllSuccess()) {
        Score difference = benchmarkResult.getAverageScore().subtract(averageScore);
        // Calculations done on doubles to avoid common overflow when executing with an int score >
        // 500 000
        double[] differenceDoubles = difference.toLevelDoubles();
        if (differenceSquaredTotalDoubles == null) {
          differenceSquaredTotalDoubles = new double[differenceDoubles.length];
        }
        for (int i = 0; i < differenceDoubles.length; i++) {
          differenceSquaredTotalDoubles[i] += Math.pow(differenceDoubles[i], 2.0);
        }
      }
    }

    if (differenceSquaredTotalDoubles == null) { // no successful benchmarks
      return new double[0];
    }

    double[] standardDeviationDoubles = new double[differenceSquaredTotalDoubles.length];
    for (int i = 0; i < differenceSquaredTotalDoubles.length; i++) {
      standardDeviationDoubles[i] = Math.pow(differenceSquaredTotalDoubles[i] / successCount, 0.5);
    }
    return standardDeviationDoubles;
  }

  private static double[] floatingStandardDeviation(
      List<? extends BenchmarkResult> results, Score<?> averageScore, int successCount) {
    var averageLevels = averageScore.toLevelNumbers();
    var squaredTotals = new BigDecimal[averageLevels.length];
    Arrays.fill(squaredTotals, BigDecimal.ZERO);
    boolean foundSuccess = false;
    for (var result : results) {
      if (!result.hasAllSuccess()) {
        continue;
      }
      foundSuccess = true;
      var levels = result.getAverageScore().toLevelNumbers();
      if (levels.length != averageLevels.length) {
        throw new IllegalArgumentException(
            "Benchmark score and average must have the same level count.");
      }
      for (int i = 0; i < levels.length; i++) {
        var difference =
            FloatingBenchmarkScoreArithmetic.decimal(levels[i])
                .subtract(FloatingBenchmarkScoreArithmetic.decimal(averageLevels[i]));
        squaredTotals[i] = squaredTotals[i].add(difference.multiply(difference));
      }
    }
    if (!foundSuccess) {
      return new double[0];
    }
    var deviations = new double[averageLevels.length];
    for (int i = 0; i < deviations.length; i++) {
      var variance =
          squaredTotals[i].divide(BigDecimal.valueOf(successCount), MathContext.DECIMAL128);
      deviations[i] = variance.sqrt(MathContext.DECIMAL128).doubleValue();
      if (!Double.isFinite(deviations[i])) {
        throw new ArithmeticException(
            "The benchmark standard deviation at level ("
                + i
                + ") exceeds the finite double range.");
      }
    }
    return deviations;
  }

  public static String getStandardDeviationString(double[] standardDeviationDoubles) {
    if (standardDeviationDoubles == null) {
      return null;
    }
    StringBuilder standardDeviationString = new StringBuilder(standardDeviationDoubles.length * 9);
    // Abbreviate to 2 decimals
    // We don't use a local sensitive DecimalFormat, because other Scores don't use it either
    DecimalFormatSymbols decimalFormatSymbols = new DecimalFormatSymbols(Locale.US);
    DecimalFormat exponentialFormat = new DecimalFormat("0.0#E0", decimalFormatSymbols);
    DecimalFormat decimalFormat = new DecimalFormat("0.0#", decimalFormatSymbols);
    boolean first = true;
    for (double standardDeviationDouble : standardDeviationDoubles) {
      if (first) {
        first = false;
      } else {
        standardDeviationString.append("/");
      }
      // See http://docs.oracle.com/javase/7/docs/api/java/lang/Double.html#toString%28double%29
      String abbreviated;
      if (0.001 <= standardDeviationDouble && standardDeviationDouble <= 10000000.0) {
        abbreviated = decimalFormat.format(standardDeviationDouble);
      } else {
        abbreviated = exponentialFormat.format(standardDeviationDouble);
      }
      standardDeviationString.append(abbreviated);
    }
    return standardDeviationString.toString();
  }
}
