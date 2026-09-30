package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.director.InnerScore;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchScoreComparatorTest {

  @Test
  void hardGuidanceCanLeaveFeasibilityAndUsesLowerLevelsAsTieBreak() {
    var comparator = new GuidedLocalSearchScoreComparator(0, new BigDecimal("0.1"));
    var infeasible = InnerScore.fullyAssigned(HardSoftScore.of(-1, 5));
    var feasible = InnerScore.fullyAssigned(HardSoftScore.ZERO);
    assertThat(comparator.compareProtectedPrefix(infeasible, feasible)).isZero();
    assertThat(
            comparator.compare(
                infeasible, GuidedLocalSearchNumber.ZERO, feasible, GuidedLocalSearchNumber.of(20)))
        .isPositive();
    // Equal guided hard score (-1), so the original soft score breaks the tie.
    assertThat(
            comparator.compare(
                infeasible, GuidedLocalSearchNumber.ZERO, feasible, GuidedLocalSearchNumber.of(10)))
        .isPositive();
  }

  @Test
  void structuralAndInitializationLevelsAlwaysRemainProtected() {
    var comparator = new GuidedLocalSearchScoreComparator(0, BigDecimal.ONE);
    var flawed = InnerScore.fullyAssigned(new SimpleScore(-1, Long.MAX_VALUE));
    var sound = InnerScore.fullyAssigned(SimpleScore.of(Long.MIN_VALUE));
    assertThat(
            comparator.compare(
                flawed,
                GuidedLocalSearchNumber.ZERO,
                sound,
                GuidedLocalSearchNumber.of(Long.MAX_VALUE)))
        .isNegative();
    var uninitialized = InnerScore.withUnassignedCount(SimpleScore.of(Long.MAX_VALUE), 1);
    assertThat(
            comparator.compare(
                uninitialized,
                GuidedLocalSearchNumber.ZERO,
                sound,
                GuidedLocalSearchNumber.of(Long.MAX_VALUE)))
        .isNegative();
  }

  @Test
  void fractionalGuidanceChangesIntegralScoreComparisonWithoutRounding() {
    var comparator = new GuidedLocalSearchScoreComparator(0, new BigDecimal("0.1"));
    assertThat(
            comparator.compare(
                InnerScore.fullyAssigned(SimpleScore.ONE),
                GuidedLocalSearchNumber.of(15),
                InnerScore.fullyAssigned(SimpleScore.ZERO),
                GuidedLocalSearchNumber.ZERO))
        .isNegative();
  }

  @Test
  void calibratedRationalPenaltyRetainsExactTies() {
    var comparator = new GuidedLocalSearchScoreComparator(0, new BigDecimal("0.1"));
    var left = InnerScore.fullyAssigned(SimpleScore.ONE);
    var right = InnerScore.fullyAssigned(SimpleScore.ZERO);
    var denominator = GuidedLocalSearchNumber.of(3);
    assertThat(
            comparator.compare(
                left,
                GuidedLocalSearchNumber.of(30),
                right,
                GuidedLocalSearchNumber.ZERO,
                denominator))
        .isZero();
    assertThat(
            comparator.compare(
                left,
                GuidedLocalSearchNumber.of(31),
                right,
                GuidedLocalSearchNumber.ZERO,
                denominator))
        .isNegative();
    assertThat(
            comparator.compare(
                left,
                GuidedLocalSearchNumber.of(29),
                right,
                GuidedLocalSearchNumber.ZERO,
                denominator))
        .isPositive();
  }

  @Test
  void allBuiltInScoresAndTargetsMatchIndependentDecimalOracle() {
    List<Function<long[], Score<?>>> factories =
        List.of(
            a -> SimpleScore.of(a[0]),
            a -> HardSoftScore.of(a[0], a[1]),
            a -> HardMediumSoftScore.of(a[0], a[1], a[2]),
            a -> BendableScore.of(new long[] {a[0], a[1]}, new long[] {a[2]}),
            a -> SimpleBigDecimalScore.of(decimal(a[0])),
            a -> HardSoftBigDecimalScore.of(decimal(a[0]), decimal(a[1])),
            a -> HardMediumSoftBigDecimalScore.of(decimal(a[0]), decimal(a[1]), decimal(a[2])),
            a ->
                BendableBigDecimalScore.of(
                    new BigDecimal[] {decimal(a[0]), decimal(a[1])},
                    new BigDecimal[] {decimal(a[2])}),
            a -> BendableScore.of(new long[] {a[0], a[1], a[2]}, new long[0]));
    var random = new Random(37);
    var alpha = new BigDecimal("0.125");
    for (var factory : factories) {
      for (int iteration = 0; iteration < 120; iteration++) {
        var left =
            inner(
                factory.apply(
                    new long[] {random.nextLong(), random.nextLong(), random.nextLong()}));
        var right =
            inner(
                factory.apply(
                    new long[] {random.nextLong(), random.nextLong(), random.nextLong()}));
        var leftPenalty = GuidedLocalSearchNumber.of(Long.MAX_VALUE).multiply(iteration + 1L);
        var rightPenalty = GuidedLocalSearchNumber.of(new BigDecimal("0.0125")).multiply(iteration);
        var leftLevels = left.raw().toLevelNumbers();
        var rightLevels = right.raw().toLevelNumbers();
        for (int target = 0; target < leftLevels.length; target++) {
          int expected = 0;
          for (int level = 0; level < leftLevels.length && expected == 0; level++) {
            var l = new BigDecimal(leftLevels[level].toString());
            var r = new BigDecimal(rightLevels[level].toString());
            if (level == target) {
              l = l.subtract(alpha.multiply(leftPenalty.toBigDecimal()));
              r = r.subtract(alpha.multiply(rightPenalty.toBigDecimal()));
            }
            expected = l.compareTo(r);
          }
          var comparator = new GuidedLocalSearchScoreComparator(target, alpha);
          assertThat(Integer.signum(comparator.compare(left, leftPenalty, right, rightPenalty)))
              .isEqualTo(Integer.signum(expected));
        }
      }
    }
  }

  @Test
  void rejectsOutOfRangeTargetEvenWhenPenaltiesMatch() {
    var comparator = new GuidedLocalSearchScoreComparator(1, BigDecimal.ONE);
    var score = InnerScore.fullyAssigned(SimpleScore.ZERO);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                comparator.compare(
                    score, GuidedLocalSearchNumber.ZERO, score, GuidedLocalSearchNumber.ZERO));
  }

  private static BigDecimal decimal(long value) {
    return BigDecimal.valueOf(value, 2);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static InnerScore<?> inner(Score<?> score) {
    return InnerScore.fullyAssigned((Score) score);
  }
}
