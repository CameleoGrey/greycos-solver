package greycos.solver.core.api.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FloatingScoreAccumulatorTest {

  @Test
  void cancellationAndUndoAreExactDespitePrimitiveRounding() {
    var floats = FloatingScoreAccumulator.create(SimpleFloatScore.ZERO);
    floats.add(SimpleFloatScore.of(Float.MAX_VALUE));
    floats.add(SimpleFloatScore.of(1.0f));
    floats.subtract(SimpleFloatScore.of(Float.MAX_VALUE));
    assertThat(floats.extractScore()).isEqualTo(SimpleFloatScore.ONE);
    floats.subtract(SimpleFloatScore.ONE);
    assertThat(floats.extractScore()).isEqualTo(SimpleFloatScore.ZERO);
    var doubles = FloatingScoreAccumulator.create(SimpleDoubleScore.ZERO);
    doubles.add(SimpleDoubleScore.of(Double.MAX_VALUE));
    doubles.add(SimpleDoubleScore.of(Double.MIN_VALUE));
    doubles.subtract(SimpleDoubleScore.of(Double.MAX_VALUE));
    assertThat(doubles.extractScore()).isEqualTo(SimpleDoubleScore.of(Double.MIN_VALUE));
  }

  @Test
  void intermediateOverflowIsReversibleAndExtractionDoesNotMutate() {
    var accumulator = FloatingScoreAccumulator.create(SimpleDoubleScore.ZERO);
    accumulator.add(SimpleDoubleScore.of(Double.MAX_VALUE));
    accumulator.add(SimpleDoubleScore.of(Double.MAX_VALUE));
    assertThatThrownBy(accumulator::extractScore)
        .isInstanceOf(ArithmeticException.class)
        .hasMessageContaining("level (0)");
    accumulator.subtract(SimpleDoubleScore.of(Double.MAX_VALUE));
    assertThat(accumulator.extractScore()).isEqualTo(SimpleDoubleScore.of(Double.MAX_VALUE));
    accumulator.clear();
    assertThat(accumulator.extractScore()).isEqualTo(SimpleDoubleScore.ZERO);
  }

  @Test
  void contributionsAndPermutationsMatchIndependentExactSum() {
    var random = new Random(1703L);
    var values = new ArrayList<SimpleDoubleScore>();
    var expected = BigDecimal.ZERO;
    for (int i = 0; i < 200; i++) {
      double value = Math.scalb(random.nextDouble() - 0.5, random.nextInt(1000) - 500);
      values.add(SimpleDoubleScore.of(value));
      expected = expected.add(new BigDecimal(value));
    }
    var accumulator = FloatingScoreAccumulator.create(SimpleDoubleScore.ZERO);
    for (int round = 0; round < 10; round++) {
      Collections.shuffle(values, random);
      values.forEach(accumulator::add);
      assertThat(accumulator.extractScore().score()).isEqualTo(expected.doubleValue());
      Collections.shuffle(values, random);
      values.forEach(accumulator::subtract);
      assertThat(accumulator.extractScore()).isEqualTo(SimpleDoubleScore.ZERO);
    }
  }

  @Test
  void allWeightPrecisionsReturnTheActualReversibleContribution() {
    var accumulator = FloatingScoreAccumulator.create(HardSoftFloatScore.ZERO);
    var weight = HardSoftFloatScore.of(0.1f, -0.3f);
    var longContribution = accumulator.addWeighted(weight, 17L);
    var floatContribution = accumulator.addWeighted(weight, 0.25f);
    var doubleContribution = accumulator.addWeighted(weight, 1.0 / 3.0);
    assertThat(longContribution.hardScore())
        .isEqualTo(
            new BigDecimal((double) weight.hardScore())
                .multiply(BigDecimal.valueOf(17L))
                .floatValue());
    assertThat(floatContribution.softScore())
        .isEqualTo(
            new BigDecimal((double) weight.softScore())
                .multiply(new BigDecimal(0.25))
                .floatValue());
    assertThat(doubleContribution.hardScore())
        .isEqualTo(
            new BigDecimal((double) weight.hardScore())
                .multiply(new BigDecimal(1.0 / 3.0))
                .floatValue());
    accumulator.subtract(floatContribution);
    accumulator.subtract(doubleContribution);
    assertThat(accumulator.extractScore()).isEqualTo(longContribution);
    accumulator.subtract(longContribution);
    assertThat(accumulator.extractScore()).isEqualTo(HardSoftFloatScore.ZERO);
    var doubles = FloatingScoreAccumulator.create(HardSoftDoubleScore.ZERO);
    var doubleWeight = HardSoftDoubleScore.of(0.1, 0.3);
    var contributions =
        new HardSoftDoubleScore[] {
          doubles.addWeighted(doubleWeight, 17L),
          doubles.addWeighted(doubleWeight, 0.25f),
          doubles.addWeighted(doubleWeight, 1.0 / 3.0)
        };
    for (var contribution : contributions) {
      doubles.subtract(contribution);
    }
    assertThat(doubles.extractScore()).isEqualTo(HardSoftDoubleScore.ZERO);
  }

  @Test
  void invalidWeightedContributionDoesNotPartiallyMutateAnyLevel() {
    var accumulator = FloatingScoreAccumulator.create(HardSoftDoubleScore.ZERO);
    accumulator.add(HardSoftDoubleScore.of(3.0, 4.0));
    assertThatThrownBy(
            () -> accumulator.addWeighted(HardSoftDoubleScore.of(1.0, Double.MAX_VALUE), 2L))
        .isInstanceOf(ArithmeticException.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> accumulator.addWeighted(HardSoftDoubleScore.ONE_HARD, Double.NaN));
    assertThat(accumulator.extractScore()).isEqualTo(HardSoftDoubleScore.of(3.0, 4.0));
  }

  @Test
  void allFamiliesAccumulateTheirDistinctLevels() {
    var triple = FloatingScoreAccumulator.create(HardMediumSoftDoubleScore.ZERO);
    var contribution = triple.addWeighted(HardMediumSoftDoubleScore.of(2.0, 3.0, 4.0), 0.5f);
    assertThat(contribution).isEqualTo(HardMediumSoftDoubleScore.of(1.0, 1.5, 2.0));
    assertThat(triple.extractScore()).isEqualTo(contribution);
    var floatTriple = FloatingScoreAccumulator.create(HardMediumSoftFloatScore.ZERO);
    assertThat(floatTriple.addWeighted(HardMediumSoftFloatScore.of(2.0f, 3.0f, 4.0f), 0.5))
        .isEqualTo(HardMediumSoftFloatScore.of(1.0f, 1.5f, 2.0f));
    var bendableFloat = FloatingScoreAccumulator.create(BendableFloatScore.zero(2, 1));
    assertThat(
            bendableFloat.addWeighted(
                BendableFloatScore.of(new float[] {1.0f, 2.0f}, new float[] {3.0f}), 2L))
        .isEqualTo(BendableFloatScore.of(new float[] {2.0f, 4.0f}, new float[] {6.0f}));
    var bendableDouble = FloatingScoreAccumulator.create(BendableDoubleScore.zero(0, 2));
    assertThat(
            bendableDouble.addWeighted(
                BendableDoubleScore.of(new double[0], new double[] {3.0, 5.0}), 0.5))
        .isEqualTo(BendableDoubleScore.of(new double[0], new double[] {1.5, 2.5}));
  }

  @Test
  void unsupportedZeroStructuralStateAndDimensionsFail() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FloatingScoreAccumulator.create(SimpleScore.ZERO));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FloatingScoreAccumulator.create(SimpleFloatScore.ONE));
    var simple = FloatingScoreAccumulator.create(SimpleFloatScore.ZERO);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> simple.add(new SimpleFloatScore(-1L, 0.0f)));
    var bendable = FloatingScoreAccumulator.create(BendableDoubleScore.zero(1, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> bendable.add(BendableDoubleScore.zero(0, 2)));
  }
}
