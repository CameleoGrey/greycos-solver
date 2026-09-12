package greycos.solver.core.impl.alns;

import java.math.BigDecimal;
import java.math.BigInteger;

import greycos.solver.core.api.score.Score;

/** Exact arithmetic for operator ranking. Score levels are never collapsed into a scalar. */
public final class AlnsScoreMath {
  private AlnsScoreMath() {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static int compare(Score<?> left, Score<?> right) {
    return ((Score) left).compareTo(right);
  }

  public static BigDecimal decimal(Number value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    if (value instanceof Long
        || value instanceof Integer
        || value instanceof Short
        || value instanceof Byte) {
      return BigDecimal.valueOf(value.longValue());
    }
    return new BigDecimal(value.toString());
  }

  public static BigDecimal[] difference(Score<?> left, Score<?> right) {
    var leftLevels = left.toLevelNumbers();
    var rightLevels = right.toLevelNumbers();
    if (leftLevels.length != rightLevels.length) {
      throw new IllegalArgumentException("ALNS score levels must have the same length.");
    }
    var result = new BigDecimal[leftLevels.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = decimal(leftLevels[i]).subtract(decimal(rightLevels[i]));
    }
    return result;
  }

  public static int compare(BigDecimal[] left, BigDecimal[] right) {
    for (int i = 0; i < left.length; i++) {
      int comparison = left[i].compareTo(right[i]);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }
}
