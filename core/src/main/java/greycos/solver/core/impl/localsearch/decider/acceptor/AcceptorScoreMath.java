package greycos.solver.core.impl.localsearch.decider.acceptor;

import java.math.BigDecimal;
import java.math.BigInteger;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.ScoreArithmetic;

/** Arithmetic for acceptance decisions; public score arithmetic keeps its existing semantics. */
public final class AcceptorScoreMath {

  private AcceptorScoreMath() {}

  public static int signum(Number value) {
    return switch (value) {
      case Long number -> Long.compare(number, 0L);
      case BigDecimal number -> number.signum();
      case BigInteger number -> number.signum();
      default -> FloatingScoreSupport.exact(value).signum();
    };
  }

  public static Number[] difference(Score<?> left, Score<?> right) {
    return switch (left) {
      case SimpleScore value ->
          new Number[] {difference(value.score(), ((SimpleScore) right).score())};
      case HardSoftScore value -> {
        var other = (HardSoftScore) right;
        yield new Number[] {
          difference(value.hardScore(), other.hardScore()),
          difference(value.softScore(), other.softScore())
        };
      }
      case HardMediumSoftScore value -> {
        var other = (HardMediumSoftScore) right;
        yield new Number[] {
          difference(value.hardScore(), other.hardScore()),
          difference(value.mediumScore(), other.mediumScore()),
          difference(value.softScore(), other.softScore())
        };
      }
      case BendableScore value -> {
        var other = (BendableScore) right;
        var result = new Number[value.levelsSize()];
        for (int i = 0; i < result.length; i++) {
          result[i] = difference(value.hardOrSoftScore(i), other.hardOrSoftScore(i));
        }
        yield result;
      }
      // Floating and decimal differences are already exact; retain custom score arithmetic.
      default -> ScoreArithmetic.difference(left, right);
    };
  }

  private static Number difference(long left, long right) {
    try {
      return Math.subtractExact(left, right);
    } catch (ArithmeticException overflow) {
      return BigInteger.valueOf(left).subtract(BigInteger.valueOf(right));
    }
  }
}
