package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.impl.cotwin.valuerange.buildin.collection.ListValueRange;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmOperatorsTest {

  private static final BasicVariableDescriptor<TestdataSolution> VALUE_DESCRIPTOR =
      TestdataEntity.buildVariableDescriptorForValue();

  @Test
  void rankedSelectionUsesCeilingSizedPrefixAndSuffix() {
    var minimum = random(new double[] {0.0}, 0);
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(128, 0.05, true, minimum)).isZero();
    minimum.assertExhausted();
    var maximum = random(new double[] {Math.nextDown(1.0)}, 6);
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(128, 0.05, true, maximum)).isEqualTo(6);
    maximum.assertExhausted();
    var worst = random(new double[] {Math.nextDown(1.0)}, 121);
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(128, 0.05, false, worst)).isEqualTo(121);
    worst.assertExhausted();
    var worstSingleton = random(new double[] {0.0}, 127);
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(128, 0.05, false, worstSingleton))
        .isEqualTo(127);
    worstSingleton.assertExhausted();
    var fullPopulation = random(new double[] {Math.nextDown(1.0)}, 127);
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(128, 1.0, true, fullPopulation))
        .isEqualTo(127);
    fullPopulation.assertExhausted();
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(1, 0.05, true, new Random(0))).isZero();
    assertThat(GeneticAlgorithmOperators.selectRankedIndex(1, 0.05, false, new Random(0))).isZero();
  }

  @Test
  void sharedCrossoverExchangesAllMovableAssignmentsAndRetainsPins() {
    var slots = slots(4);
    slots.set(1, immutable(slots.get(1)));
    var operators =
        new GeneticAlgorithmOperators<>(slots, new GeneticAlgorithmPhaseConfig().resolve());
    var first = genome(0, 1, 2, 3);
    var second = genome(10, 11, 12, 13);
    var random = random(new double[] {0.5, Math.nextDown(0.5)});
    var pair = operators.cross(first, second, random);
    assertThat(pair.crossed()).isTrue();
    assertThat(pair.first().toArray()).containsExactly(10, 1, 12, 13);
    assertThat(pair.second().toArray()).containsExactly(0, 11, 2, 3);
    assertThat(first.toArray()).containsExactly(0, 1, 2, 3);
    assertThat(second.toArray()).containsExactly(10, 11, 12, 13);
    random.assertExhausted();
  }

  @Test
  void sharedWeightHalfRoundsUpAndGateEqualityCrosses() {
    var operators =
        new GeneticAlgorithmOperators<>(slots(2), new GeneticAlgorithmPhaseConfig().resolve());
    var first = genome(0, 1);
    var second = genome(2, 3);
    var retainRandom = random(new double[] {0.5, 0.5});
    var retain = operators.cross(first, second, retainRandom);
    assertThat(retain.crossed()).isTrue();
    assertThat(retain.first()).isSameAs(first);
    assertThat(retain.second()).isSameAs(second);
    retainRandom.assertExhausted();
    var skipRandom = random(new double[] {Math.nextUp(0.5)});
    var skipped = operators.cross(first, second, skipRandom);
    assertThat(skipped.crossed()).isFalse();
    assertThat(skipped.first()).isSameAs(first);
    skipRandom.assertExhausted();

    var zeroProbability =
        new GeneticAlgorithmOperators<>(
            slots(2), new GeneticAlgorithmPhaseConfig().withCrossoverProbability(0.0).resolve());
    assertThat(zeroProbability.cross(first, second, random(new double[] {0.0, 0.0})).crossed())
        .isTrue();
    assertThat(
            zeroProbability.cross(first, second, random(new double[] {Double.MIN_VALUE})).crossed())
        .isFalse();
  }

  @Test
  void changeSamplesEachRecipientsActualRangeAndAllowsNull() {
    var slots = slots(3);
    slots.set(0, withRange(slots.get(0), new ListValueRange<>(Arrays.asList(null, 9))));
    slots.set(1, withRange(slots.get(1), new ListValueRange<>(List.of(20, 21))));
    slots.set(2, immutable(slots.get(2)));
    var operators =
        new GeneticAlgorithmOperators<>(
            slots,
            config(GeneticAlgorithmMutationType.CHANGE)
                .withMutationRateMultiplier(100.0)
                .resolve());
    var random = random(new double[] {0.0, 0.0, 0.0}, 0, 0, 1).withLongs(0, 1);
    var mutation = operators.mutate(genome(0, 1, 2), random);
    assertThat(mutation.type()).isEqualTo(GeneticAlgorithmMutationType.CHANGE);
    assertThat(mutation.genome().toArray()).containsExactly(null, 21, 2);
    random.assertExhausted();
  }

  @Test
  void zeroMultiplierStillChangesOneAndEqualityDoesNotCountAsBernoulliSuccess() {
    var operators =
        new GeneticAlgorithmOperators<>(slots(3), config(GeneticAlgorithmMutationType.CHANGE));
    var random = random(new double[] {0.0, 0.0, 0.0, 0.0}, 0, 2).withLongs(8);
    assertThat(operators.mutate(genome(0, 1, 2), random).genome().toArray())
        .containsExactly(0, 1, 8);
    random.assertExhausted();
  }

  @Test
  void swapCyclicallyRotatesSelectedPositionsInSampledOrder() {
    var operators =
        new GeneticAlgorithmOperators<>(
            slots(4),
            config(GeneticAlgorithmMutationType.SWAP).withMutationRateMultiplier(2.0).resolve());
    // The binomial draws choose three positions, even though the minimum is two.
    var random = random(new double[] {0.0, 0.1, 0.1, 0.1, 0.5}, 0, 2, 2, 3);
    assertThat(operators.mutate(genome(0, 1, 2, 3), random).genome().toArray())
        .containsExactly(3, 1, 0, 2);
    random.assertExhausted();
  }

  @Test
  void edgeSwapsPreserveSequentialOverlapSemantics() {
    var overlapping =
        new GeneticAlgorithmOperators<>(slots(3), config(GeneticAlgorithmMutationType.SWAP_EDGES));
    var overlapRandom = random(new double[] {0.0, 0.0, 0.0}, 0, 0, 1);
    assertThat(overlapping.mutate(genome(0, 1, 2), overlapRandom).genome().toArray())
        .containsExactly(1, 2, 0);
    overlapRandom.assertExhausted();
    var disjoint =
        new GeneticAlgorithmOperators<>(slots(4), config(GeneticAlgorithmMutationType.SWAP_EDGES));
    var disjointRandom = random(new double[] {0.0, 0.0, 0.0, 0.0}, 0, 0, 2);
    assertThat(disjoint.mutate(genome(0, 1, 2, 3), disjointRandom).genome().toArray())
        .containsExactly(2, 3, 0, 1);
    disjointRandom.assertExhausted();
    var three =
        new GeneticAlgorithmOperators<>(
            slots(6),
            config(GeneticAlgorithmMutationType.SWAP_EDGES)
                .withMutationRateMultiplier(3.0)
                .resolve());
    var threeRandom = random(new double[] {0.0, 0.0, 0.0, 0.0, 0.5, 0.5}, 0, 0, 2, 4);
    assertThat(three.mutate(genome(0, 1, 2, 3, 4, 5), threeRandom).genome().toArray())
        .containsExactly(2, 3, 4, 5, 0, 1);
    threeRandom.assertExhausted();
  }

  @Test
  void scrambleUsesContiguousBlockWithLengthBetweenThreeAndSix() {
    var operators =
        new GeneticAlgorithmOperators<>(slots(8), config(GeneticAlgorithmMutationType.SCRAMBLE));
    var shortRandom = random(new double[] {0.0}, 0, 3, 2, 0, 0);
    assertThat(operators.mutate(genome(0, 1, 2, 3, 4, 5, 6, 7), shortRandom).genome().toArray())
        .containsExactly(0, 1, 3, 4, 2, 5, 6, 7);
    shortRandom.assertExhausted();
    var longRandom = random(new double[] {0.0}, 0, 6, 1, 0, 0, 0, 0, 0);
    assertThat(operators.mutate(genome(0, 1, 2, 3, 4, 5, 6, 7), longRandom).genome().toArray())
        .containsExactly(0, 2, 3, 4, 5, 6, 1, 7);
    longRandom.assertExhausted();
  }

  @Test
  void insertionFollowsEndpointOrderAndInverseReversesInclusiveBlock() {
    var insertion =
        new GeneticAlgorithmOperators<>(slots(4), config(GeneticAlgorithmMutationType.INSERTION));
    var forward = random(new double[] {0.0}, 0, 0, 2);
    assertThat(insertion.mutate(genome(0, 1, 2, 3), forward).genome().toArray())
        .containsExactly(1, 2, 0, 3);
    forward.assertExhausted();
    var backward = random(new double[] {0.0}, 0, 2, 2);
    assertThat(insertion.mutate(genome(0, 1, 2, 3), backward).genome().toArray())
        .containsExactly(2, 0, 1, 3);
    backward.assertExhausted();
    var inverse =
        new GeneticAlgorithmOperators<>(slots(4), config(GeneticAlgorithmMutationType.INVERSE));
    var reverse = random(new double[] {0.0}, 0, 3, 1);
    assertThat(inverse.mutate(genome(0, 1, 2, 3), reverse).genome().toArray())
        .containsExactly(0, 3, 2, 1);
    reverse.assertExhausted();
  }

  @Test
  void positiveEligibleOperatorsRenormalizeAndExplicitZerosStayDisabled() {
    var config =
        new GeneticAlgorithmPhaseConfig()
            .withMutationOperators(
                weight(GeneticAlgorithmMutationType.INSERTION, 0.1),
                weight(GeneticAlgorithmMutationType.CHANGE, 0.0),
                weight(GeneticAlgorithmMutationType.SWAP_EDGES, 0.5),
                weight(GeneticAlgorithmMutationType.SCRAMBLE, 0.2),
                weight(GeneticAlgorithmMutationType.SWAP, 0.2))
            .resolve();
    var operators = new GeneticAlgorithmOperators<>(slots(2), config);
    assertThat(operators.mutate(genome(0, 1), random(new double[] {0.0, 0.0, 0.0}, 0, 0, 1)).type())
        .isEqualTo(GeneticAlgorithmMutationType.SWAP);
    assertThat(
            operators.mutate(genome(0, 1), random(new double[] {0.65, 0.0, 0.0}, 0, 0, 1)).type())
        .isEqualTo(GeneticAlgorithmMutationType.SWAP);
    assertThat(operators.mutate(genome(0, 1), random(new double[] {0.68}, 0, 0, 1)).type())
        .isEqualTo(GeneticAlgorithmMutationType.INSERTION);
    assertThat(
            operators
                .mutate(genome(0, 1), random(new double[] {Math.nextDown(1.0)}, 0, 0, 1))
                .type())
        .isEqualTo(GeneticAlgorithmMutationType.INSERTION);
  }

  @Test
  void groupsAreChosenUniformlyAfterOperatorAndKeepSlotEncounterOrder() {
    var entityDescriptor = TestdataMultiVarEntity.buildEntityDescriptor();
    var primary =
        (BasicVariableDescriptor<TestdataMultiVarSolution>)
            entityDescriptor.getGenuineVariableDescriptor("primaryValue");
    var secondary =
        (BasicVariableDescriptor<TestdataMultiVarSolution>)
            entityDescriptor.getGenuineVariableDescriptor("secondaryValue");
    var slots = new ArrayList<GeneticAlgorithmSlot<TestdataMultiVarSolution>>();
    for (int id = 0; id < 5; id++) {
      slots.add(
          new GeneticAlgorithmSlot<>(
              new TestdataMultiVarEntity("e" + id),
              id % 2 == 0 ? primary : secondary,
              new ListValueRange<>(List.of(0, 1, 2, 3, 4)),
              true));
    }
    var operators =
        new GeneticAlgorithmOperators<>(
            slots,
            config(GeneticAlgorithmMutationType.SWAP).withMutationRateMultiplier(100.0).resolve());
    var firstGroup = random(new double[] {0.0, 0.0, 0.0, 0.0}, 0, 0, 1, 2);
    assertThat(operators.mutate(genome(0, 1, 2, 3, 4), firstGroup).genome().toArray())
        .containsExactly(2, 1, 4, 3, 0);
    firstGroup.assertExhausted();
    var secondGroup = random(new double[] {0.0, 0.0, 0.0}, 1, 0, 1);
    assertThat(operators.mutate(genome(0, 1, 2, 3, 4), secondGroup).genome().toArray())
        .containsExactly(0, 3, 2, 1, 4);
    secondGroup.assertExhausted();
  }

  @Test
  void tabuExpirationIsBoundedAndOldestFirst() {
    var operators =
        new GeneticAlgorithmOperators<>(
            slots(3),
            config(GeneticAlgorithmMutationType.CHANGE).withTabuEntityRate(1.0).resolve());
    for (int expected : new int[] {0, 1, 2, 0, 1, 2}) {
      var random = random(new double[] {0.0, 0.0, 0.0, 0.0}, 0, 0).withLongs(9);
      var result = operators.mutate(genome(0, 1, 2), random).genome().toArray();
      assertThat(result[expected]).isEqualTo(9);
      assertThat(IntStream.range(0, 3).filter(i -> !result[i].equals(i)).count()).isEqualTo(1);
      random.assertExhausted();
    }
    var smallTenure =
        new GeneticAlgorithmOperators<>(
            slots(3),
            config(GeneticAlgorithmMutationType.CHANGE).withTabuEntityRate(0.01).resolve());
    for (int expected : new int[] {0, 1, 0, 1}) {
      var random = random(new double[] {0.0, 0.0, 0.0, 0.0}, 0, 0).withLongs(9);
      assertThat(smallTenure.mutate(genome(0, 1, 2), random).genome().value(expected)).isEqualTo(9);
      random.assertExhausted();
    }
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.01, 0.5, 1.0})
  void mixedOperatorsExpireTabuWhenPositionUniverseShrinks(double tabuRate) {
    var operators =
        new GeneticAlgorithmOperators<>(
            slots(8),
            new GeneticAlgorithmPhaseConfig()
                .withTabuEntityRate(tabuRate)
                .withMutationRateMultiplier(8.0)
                .resolve());
    var initial = genome(0, 1, 2, 3, 4, 5, 6, 7);
    var random = new Random(37);
    for (int i = 0; i < 1_000; i++) {
      var result = operators.mutate(initial, random);
      assertThat(result.genome().size()).isEqualTo(initial.size());
      if (result.type() != GeneticAlgorithmMutationType.CHANGE) {
        assertThat(result.genome().toArray()).containsExactlyInAnyOrder(initial.toArray());
      }
    }
  }

  @ParameterizedTest
  @EnumSource(GeneticAlgorithmMutationType.class)
  void everyOperatorHonorsMinimaAndImmutableSlots(GeneticAlgorithmMutationType type) {
    int minimum =
        switch (type) {
          case CHANGE -> 1;
          case SWAP, INSERTION, INVERSE -> 2;
          case SWAP_EDGES, SCRAMBLE -> 3;
        };
    for (int size = 1; size <= 8; size++) {
      var slots = slots(size + 2);
      slots.set(0, immutable(slots.get(0)));
      slots.set(
          size + 1,
          immutable(withRange(slots.get(size + 1), new ListValueRange<>(List.of(size + 1)))));
      if (size < minimum) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new GeneticAlgorithmOperators<>(slots, config(type)))
            .withMessageContaining("no enabled mutation eligible");
      } else {
        var operators =
            new GeneticAlgorithmOperators<>(
                slots,
                config(type).withMutationRateMultiplier(100.0).withTabuEntityRate(1.0).resolve());
        var initial = new GeneticAlgorithmGenome(IntStream.range(0, size + 2).boxed().toArray());
        var random = new Random(1);
        for (int attempt = 0; attempt < 50; attempt++) {
          var result = operators.mutate(initial, random).genome();
          assertThat(result.value(0)).isEqualTo(0);
          assertThat(result.value(size + 1)).isEqualTo(size + 1);
        }
      }
    }
  }

  @Test
  void noMovableAssignmentsAreReportedWithoutInventingAnOperator() {
    var allPinned = slots(2).stream().map(GeneticAlgorithmOperatorsTest::immutable).toList();
    var operators =
        new GeneticAlgorithmOperators<>(allPinned, config(GeneticAlgorithmMutationType.SCRAMBLE));
    assertThat(operators.hasMovableSlots()).isFalse();
    assertThat(operators.sampleSeed(genome(0, 1), random(new double[0]))).isEqualTo(genome(0, 1));
    assertThatIllegalStateException()
        .isThrownBy(() -> operators.mutate(genome(0, 1), new Random(0)));
    assertThat(
            new GeneticAlgorithmOperators<TestdataSolution>(
                    List.of(), config(GeneticAlgorithmMutationType.CHANGE))
                .hasMovableSlots())
        .isFalse();
  }

  @Test
  void seedSamplesIndependentlyAndRetainsPinnedAndSingletonSlots() {
    var slots = slots(4);
    slots.set(1, immutable(slots.get(1)));
    slots.set(2, withRange(slots.get(2), new ListValueRange<>(Arrays.asList(null, 20))));
    slots.set(3, immutable(withRange(slots.get(3), new ListValueRange<>(List.of(3)))));
    var operators =
        new GeneticAlgorithmOperators<>(slots, config(GeneticAlgorithmMutationType.CHANGE));
    var random = random(new double[0]).withLongs(9, 0);
    assertThat(operators.sampleSeed(genome(0, 1, 2, 3), random).toArray())
        .containsExactly(9, 1, null, 3);
    random.assertExhausted();
  }

  @SuppressWarnings("unchecked")
  @Test
  void largeIndexedRangeIsSampledWithoutIntegerTruncationOrEnumeration() {
    var range = (ValueRange<Object>) mock(ValueRange.class);
    when(range.getSize()).thenReturn(5_000_000_000L);
    when(range.get(4_000_000_000L)).thenReturn("large-index");
    var slots = slots(1);
    slots.set(0, withRange(slots.getFirst(), range));
    var operators =
        new GeneticAlgorithmOperators<>(slots, config(GeneticAlgorithmMutationType.CHANGE));
    var seedRandom = random(new double[0]).withLongs(4_000_000_000L);
    assertThat(operators.sampleSeed(genome("initial"), seedRandom).value(0))
        .isEqualTo("large-index");
    seedRandom.assertExhausted();
    var mutationRandom = random(new double[] {0.0, 0.0}, 0, 0).withLongs(4_000_000_000L);
    assertThat(operators.mutate(genome("initial"), mutationRandom).genome().value(0))
        .isEqualTo("large-index");
    mutationRandom.assertExhausted();
    verify(range, never()).createOriginalIterator();
  }

  @Test
  void permutationDoesNotClampValuesToRecipientRanges() {
    var slots = slots(2);
    slots.set(0, withRange(slots.get(0), new ListValueRange<>(List.of(0, 1))));
    slots.set(1, withRange(slots.get(1), new ListValueRange<>(List.of(2, 3))));
    var operators =
        new GeneticAlgorithmOperators<>(slots, config(GeneticAlgorithmMutationType.SWAP));
    var result =
        operators.mutate(genome(0, 2), random(new double[] {0.0, 0.0, 0.0}, 0, 0, 1)).genome();
    assertThat(result.toArray()).containsExactly(2, 0);
    assertThat(slots.get(0).valueRange().contains(result.value(0))).isFalse();
    assertThat(slots.get(1).valueRange().contains(result.value(1))).isFalse();
  }

  private static GeneticAlgorithmPhaseConfig config(GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmPhaseConfig().withMutationOperators(weight(type, 1.0)).resolve();
  }

  private static GeneticAlgorithmMutationOperatorConfig weight(
      GeneticAlgorithmMutationType type, double probability) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(probability);
  }

  private static List<GeneticAlgorithmSlot<TestdataSolution>> slots(int count) {
    var slots = new ArrayList<GeneticAlgorithmSlot<TestdataSolution>>();
    var range =
        new ListValueRange<Object>(
            IntStream.range(0, Math.max(10, count)).mapToObj(i -> (Object) i).toList());
    for (int i = 0; i < count; i++) {
      slots.add(
          new GeneticAlgorithmSlot<>(new TestdataEntity("e" + i), VALUE_DESCRIPTOR, range, true));
    }
    return slots;
  }

  private static GeneticAlgorithmSlot<TestdataSolution> immutable(
      GeneticAlgorithmSlot<TestdataSolution> slot) {
    return new GeneticAlgorithmSlot<>(
        slot.entity(), slot.variableDescriptor(), slot.valueRange(), false);
  }

  private static GeneticAlgorithmSlot<TestdataSolution> withRange(
      GeneticAlgorithmSlot<TestdataSolution> slot, ValueRange<Object> range) {
    return new GeneticAlgorithmSlot<>(
        slot.entity(), slot.variableDescriptor(), range, slot.movable());
  }

  private static GeneticAlgorithmGenome genome(Object... values) {
    return new GeneticAlgorithmGenome(values);
  }

  private static ScriptedRandom random(double[] doubles, int... integers) {
    return new ScriptedRandom(doubles, integers);
  }

  /**
   * Every scripted draw is range checked, and unscripted draws fail rather than hiding extra work.
   */
  private static final class ScriptedRandom implements RandomGenerator {
    private final ArrayDeque<Double> doubles = new ArrayDeque<>();
    private final ArrayDeque<Integer> integers = new ArrayDeque<>();
    private final ArrayDeque<Long> longs = new ArrayDeque<>();

    private ScriptedRandom(double[] doubles, int[] integers) {
      Arrays.stream(doubles).forEach(this.doubles::addLast);
      Arrays.stream(integers).forEach(this.integers::addLast);
    }

    private ScriptedRandom withLongs(long... values) {
      Arrays.stream(values).forEach(longs::addLast);
      return this;
    }

    @Override
    public double nextDouble() {
      double result = doubles.removeFirst();
      assertThat(result).isGreaterThanOrEqualTo(0).isLessThan(1);
      return result;
    }

    @Override
    public double nextDouble(double origin, double bound) {
      return origin + (bound - origin) * nextDouble();
    }

    @Override
    public int nextInt(int bound) {
      return nextInt(0, bound);
    }

    @Override
    public int nextInt(int origin, int bound) {
      int result = integers.removeFirst();
      assertThat(result).isGreaterThanOrEqualTo(origin).isLessThan(bound);
      return result;
    }

    @Override
    public long nextLong(long bound) {
      long result = longs.removeFirst();
      assertThat(result).isGreaterThanOrEqualTo(0).isLessThan(bound);
      return result;
    }

    @Override
    public long nextLong() {
      throw new AssertionError("Unexpected unbounded random draw.");
    }

    private void assertExhausted() {
      assertThat(doubles).isEmpty();
      assertThat(integers).isEmpty();
      assertThat(longs).isEmpty();
    }
  }
}
