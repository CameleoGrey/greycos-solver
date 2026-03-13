package ai.greycos.solver.core.api.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.stream.Stream;

import ai.greycos.solver.core.impl.score.ScoreUtil;
import ai.greycos.solver.core.impl.score.definition.BendableScoreDefinition;

import org.jspecify.annotations.NullMarked;

/**
 * This {@link Score} is based on n levels of {@link BigDecimal} constraints. The number of levels
 * is bendable at configuration time.
 *
 * <p>This class is immutable.
 *
 * <p>The {@link #hardLevelsSize()} and {@link #softLevelsSize()} must be the same as in the {@link
 * BendableScoreDefinition} used.
 *
 * @see Score
 */
@NullMarked
public record BendableBigDecimalScore(BigDecimal[] hardScores, BigDecimal[] softScores)
    implements IBendableScore<BendableBigDecimalScore> {

  public static BendableBigDecimalScore parseScore(String scoreString) {
    var scoreTokens =
        ScoreUtil.parseBendableScoreTokens(BendableBigDecimalScore.class, scoreString);
    var hardScores = new BigDecimal[scoreTokens[0].length];
    for (var i = 0; i < hardScores.length; i++) {
      hardScores[i] =
          ScoreUtil.parseLevelAsBigDecimal(
              BendableBigDecimalScore.class, scoreString, scoreTokens[0][i]);
    }
    var softScores = new BigDecimal[scoreTokens[1].length];
    for (var i = 0; i < softScores.length; i++) {
      softScores[i] =
          ScoreUtil.parseLevelAsBigDecimal(
              BendableBigDecimalScore.class, scoreString, scoreTokens[1][i]);
    }
    return of(hardScores, softScores);
  }

  public static BendableBigDecimalScore of(BigDecimal[] hardScores, BigDecimal[] softScores) {
    return new BendableBigDecimalScore(hardScores, softScores);
  }

  public static BendableBigDecimalScore zero(int hardLevelsSize, int softLevelsSize) {
    var hardScores = new BigDecimal[hardLevelsSize];
    Arrays.fill(hardScores, BigDecimal.ZERO);
    var softScores = new BigDecimal[softLevelsSize];
    Arrays.fill(softScores, BigDecimal.ZERO);
    return new BendableBigDecimalScore(hardScores, softScores);
  }

  public static BendableBigDecimalScore ofHard(
      int hardLevelsSize, int softLevelsSize, int hardLevel, BigDecimal hardScore) {
    var hardScores = new BigDecimal[hardLevelsSize];
    Arrays.fill(hardScores, BigDecimal.ZERO);
    var softScores = new BigDecimal[softLevelsSize];
    Arrays.fill(softScores, BigDecimal.ZERO);
    hardScores[hardLevel] = hardScore;
    return new BendableBigDecimalScore(hardScores, softScores);
  }

  public static BendableBigDecimalScore ofSoft(
      int hardLevelsSize, int softLevelsSize, int softLevel, BigDecimal softScore) {
    var hardScores = new BigDecimal[hardLevelsSize];
    Arrays.fill(hardScores, BigDecimal.ZERO);
    var softScores = new BigDecimal[softLevelsSize];
    Arrays.fill(softScores, BigDecimal.ZERO);
    softScores[softLevel] = softScore;
    return new BendableBigDecimalScore(hardScores, softScores);
  }

  @Override
  public int hardLevelsSize() {
    return hardScores.length;
  }

  public BigDecimal hardScore(int index) {
    return hardScores[index];
  }

  @Override
  public int softLevelsSize() {
    return softScores.length;
  }

  public BigDecimal softScore(int index) {
    return softScores[index];
  }

  public BigDecimal hardOrSoftScore(int index) {
    if (index < hardScores.length) {
      return hardScores[index];
    } else {
      return softScores[index - hardScores.length];
    }
  }

  @Override
  public boolean isFeasible() {
    for (var hardScore : hardScores) {
      if (hardScore.compareTo(BigDecimal.ZERO) < 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public BendableBigDecimalScore add(BendableBigDecimalScore addend) {
    validateCompatible(addend);
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    for (var i = 0; i < newHardScores.length; i++) {
      newHardScores[i] = hardScores[i].add(addend.hardScore(i));
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      newSoftScores[i] = softScores[i].add(addend.softScore(i));
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore subtract(BendableBigDecimalScore subtrahend) {
    validateCompatible(subtrahend);
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    for (var i = 0; i < newHardScores.length; i++) {
      newHardScores[i] = hardScores[i].subtract(subtrahend.hardScore(i));
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      newSoftScores[i] = softScores[i].subtract(subtrahend.softScore(i));
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore multiply(double multiplicand) {
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    var bigDecimalMultiplicand = BigDecimal.valueOf(multiplicand);
    for (var i = 0; i < newHardScores.length; i++) {
      newHardScores[i] =
          hardScores[i]
              .multiply(bigDecimalMultiplicand)
              .setScale(hardScores[i].scale(), RoundingMode.FLOOR);
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      newSoftScores[i] =
          softScores[i]
              .multiply(bigDecimalMultiplicand)
              .setScale(softScores[i].scale(), RoundingMode.FLOOR);
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore divide(double divisor) {
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    var bigDecimalDivisor = BigDecimal.valueOf(divisor);
    for (var i = 0; i < newHardScores.length; i++) {
      var hardScore = hardScores[i];
      newHardScores[i] = hardScore.divide(bigDecimalDivisor, hardScore.scale(), RoundingMode.FLOOR);
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      var softScore = softScores[i];
      newSoftScores[i] = softScore.divide(bigDecimalDivisor, softScore.scale(), RoundingMode.FLOOR);
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore power(double exponent) {
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    var actualExponent = BigDecimal.valueOf(exponent);
    for (var i = 0; i < newHardScores.length; i++) {
      var hardScore = hardScores[i];
      newHardScores[i] =
          hardScore.pow(actualExponent.intValue()).setScale(hardScore.scale(), RoundingMode.FLOOR);
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      var softScore = softScores[i];
      newSoftScores[i] =
          softScore.pow(actualExponent.intValue()).setScale(softScore.scale(), RoundingMode.FLOOR);
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore negate() {
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    for (var i = 0; i < newHardScores.length; i++) {
      newHardScores[i] = hardScores[i].negate();
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      newSoftScores[i] = softScores[i].negate();
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore abs() {
    var newHardScores = new BigDecimal[hardScores.length];
    var newSoftScores = new BigDecimal[softScores.length];
    for (var i = 0; i < newHardScores.length; i++) {
      newHardScores[i] = hardScores[i].abs();
    }
    for (var i = 0; i < newSoftScores.length; i++) {
      newSoftScores[i] = softScores[i].abs();
    }
    return new BendableBigDecimalScore(newHardScores, newSoftScores);
  }

  @Override
  public BendableBigDecimalScore zero() {
    return BendableBigDecimalScore.zero(hardLevelsSize(), softLevelsSize());
  }

  @Override
  public Number[] toLevelNumbers() {
    var levelNumbers = new Number[hardScores.length + softScores.length];
    System.arraycopy(hardScores, 0, levelNumbers, 0, hardScores.length);
    System.arraycopy(softScores, 0, levelNumbers, hardScores.length, softScores.length);
    return levelNumbers;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof BendableBigDecimalScore other) {
      if (hardLevelsSize() != other.hardLevelsSize()
          || softLevelsSize() != other.softLevelsSize()) {
        return false;
      }
      for (var i = 0; i < hardScores.length; i++) {
        if (!hardScores[i].stripTrailingZeros().equals(other.hardScore(i).stripTrailingZeros())) {
          return false;
        }
      }
      for (var i = 0; i < softScores.length; i++) {
        if (!softScores[i].stripTrailingZeros().equals(other.softScore(i).stripTrailingZeros())) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    var scoreHashCodes =
        Stream.concat(Arrays.stream(hardScores), Arrays.stream(softScores))
            .map(BigDecimal::stripTrailingZeros)
            .mapToInt(BigDecimal::hashCode)
            .toArray();
    return Arrays.hashCode(scoreHashCodes);
  }

  @Override
  public int compareTo(BendableBigDecimalScore other) {
    validateCompatible(other);
    for (var i = 0; i < hardScores.length; i++) {
      var hardScoreComparison = hardScores[i].compareTo(other.hardScore(i));
      if (hardScoreComparison != 0) {
        return hardScoreComparison;
      }
    }
    for (var i = 0; i < softScores.length; i++) {
      var softScoreComparison = softScores[i].compareTo(other.softScore(i));
      if (softScoreComparison != 0) {
        return softScoreComparison;
      }
    }
    return 0;
  }

  @Override
  public String toShortString() {
    return ScoreUtil.buildBendableShortString(
        this, n -> ((BigDecimal) n).compareTo(BigDecimal.ZERO) != 0);
  }

  @Override
  public String toString() {
    var s = new StringBuilder(((hardScores.length + softScores.length) * 4) + 7);
    s.append("[");
    var first = true;
    for (var hardScore : hardScores) {
      if (first) {
        first = false;
      } else {
        s.append("/");
      }
      s.append(hardScore);
    }
    s.append("]hard/[");
    first = true;
    for (var softScore : softScores) {
      if (first) {
        first = false;
      } else {
        s.append("/");
      }
      s.append(softScore);
    }
    s.append("]soft");
    return s.toString();
  }

  private void validateCompatible(BendableBigDecimalScore other) {
    if (hardLevelsSize() != other.hardLevelsSize()) {
      throw new IllegalArgumentException(
          "The score ("
              + this
              + ") with hardScoreSize ("
              + hardLevelsSize()
              + ") is not compatible with the other score ("
              + other
              + ") with hardScoreSize ("
              + other.hardLevelsSize()
              + ").");
    }
    if (softLevelsSize() != other.softLevelsSize()) {
      throw new IllegalArgumentException(
          "The score ("
              + this
              + ") with softScoreSize ("
              + softLevelsSize()
              + ") is not compatible with the other score ("
              + other
              + ") with softScoreSize ("
              + other.softLevelsSize()
              + ").");
    }
  }
}
