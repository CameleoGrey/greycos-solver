package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchPenaltyTableTest {

  @Test
  void calibratedAutomaticUtilityCompetesWithCustomCostsInSameUnits() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var automatic = Map.of("automatic", GuidedLocalSearchNumber.ONE);
    var custom = Map.of("custom", GuidedLocalSearchNumber.of(2));
    var scale = GuidedLocalSearchScale.of(new BigDecimal("4"));
    assertThat(table.incrementMaximumUtility(automatic, custom, scale)).isOne();
    assertThat(table.count("automatic")).isOne();
    assertThat(table.count("custom")).isZero();
    assertThat(table.incrementMaximumUtility(automatic, custom, scale)).isOne();
    assertThat(table.count("automatic") + table.count("custom")).isEqualTo(2);
  }

  @Test
  void rationalUtilityDoesNotRoundRepeatingFractions() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var scale = GuidedLocalSearchScale.of(GuidedLocalSearchNumber.ONE, 3);
    table.incrementMaximumUtility(
        Map.of("automatic", GuidedLocalSearchNumber.ONE),
        Map.of(
            "custom",
            GuidedLocalSearchNumber.of(new BigDecimal("0.3333333333333333333333333333333333"))),
        scale);
    assertThat(table.count("automatic")).isOne();
    assertThat(table.count("custom")).isZero();
  }

  @Test
  void incrementsBoundedExactMaximumUtilityTies() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var active = Map.of("a", GuidedLocalSearchNumber.of(10), "b", GuidedLocalSearchNumber.of(5));
    assertThat(table.incrementMaximumUtility(active)).isOne();
    assertThat(table.count("a")).isOne();
    assertThat(table.count("b")).isZero();
    assertThat(table.incrementMaximumUtility(active)).isOne();
    assertThat(table.count("a") + table.count("b")).isEqualTo(2);
    assertThat(table.version()).isEqualTo(2);
  }

  @Test
  void utilitiesAboveDoublePrecisionRemainDistinct() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    table.incrementMaximumUtility(
        Map.of(
            "larger", GuidedLocalSearchNumber.of(9_007_199_254_740_993L),
            "smaller", GuidedLocalSearchNumber.of(9_007_199_254_740_992L)));
    assertThat(table.count("larger")).isOne();
    assertThat(table.count("smaller")).isZero();
  }

  @Test
  void decimalScaleDoesNotBreakTiesAndZeroCostsAreExcluded() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    assertThat(
            table.incrementMaximumUtility(
                Map.of(
                    "a", GuidedLocalSearchNumber.of(new BigDecimal("0.1")),
                    "b", GuidedLocalSearchNumber.of(new BigDecimal("0.100")),
                    "zero", GuidedLocalSearchNumber.ZERO)))
        .isOne();
    assertThat(table.count("zero")).isZero();
    assertThat(table.incrementMaximumUtility(Map.of("zero", GuidedLocalSearchNumber.ZERO)))
        .isZero();
    assertThat(table.version()).isOne();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                table.incrementMaximumUtility(Map.of("negative", GuidedLocalSearchNumber.of(-1))));
    assertThat(table.version()).isOne();
  }

  @Test
  void largeTiesUseSeededSamplingWithoutReplacementAndCountOnlyPositiveFeatures() {
    for (int size : new int[] {2024, 1032}) {
      var active = new LinkedHashMap<String, GuidedLocalSearchNumber>();
      for (int i = 0; i < size; i++) active.put("key" + i, GuidedLocalSearchNumber.ONE);
      for (int i = 0; i < 100; i++) active.put("zero" + i, GuidedLocalSearchNumber.ZERO);
      var first = new GuidedLocalSearchPenaltyTable<String>();
      var sameSeed = new GuidedLocalSearchPenaltyTable<String>();
      var otherSeed = new GuidedLocalSearchPenaltyTable<String>();
      int expected = size == 2024 ? 127 : 65;
      assertThat(first.incrementMaximumUtility(active, new Random(37))).isEqualTo(expected);
      assertThat(sameSeed.incrementMaximumUtility(active, new Random(37))).isEqualTo(expected);
      otherSeed.incrementMaximumUtility(active, new Random(38));
      assertThat(first.snapshot().counts())
          .hasSize(expected)
          .isEqualTo(sameSeed.snapshot().counts());
      assertThat(first.snapshot().counts()).isNotEqualTo(otherSeed.snapshot().counts());
      assertThat(first.snapshot().counts().values()).allMatch(count -> count == 1L);
      assertThat(first.snapshot().counts().keySet()).noneMatch(key -> key.startsWith("zero"));
    }
  }

  @Test
  void tieBatchUsesTotalPositivePopulationAndCustomFeaturesCompeteExactly() {
    var automatic = new LinkedHashMap<String, GuidedLocalSearchNumber>();
    for (int i = 0; i < 15; i++)
      automatic.put("auto" + i, GuidedLocalSearchNumber.of(i < 2 ? 4 : 1));
    var custom =
        Map.of(
            "custom",
            GuidedLocalSearchNumber.ONE,
            "low",
            GuidedLocalSearchNumber.of(new BigDecimal("0.125")));
    var table = new GuidedLocalSearchPenaltyTable<String>();
    assertThat(
            table.incrementMaximumUtility(
                automatic,
                custom,
                GuidedLocalSearchScale.of(new BigDecimal("0.25")),
                new Random(1)))
        .isEqualTo(2);
    assertThat(table.snapshot().counts().keySet())
        .allMatch(key -> key.equals("auto0") || key.equals("auto1") || key.equals("custom"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void countOverflowDoesNotPartiallyMutateTheSelectedBatch() throws Exception {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var countsField = GuidedLocalSearchPenaltyTable.class.getDeclaredField("counts");
    countsField.setAccessible(true);
    var counts = (Map<String, Long>) countsField.get(table);
    counts.put("overflow", Long.MAX_VALUE);
    var active = new LinkedHashMap<String, GuidedLocalSearchNumber>();
    active.put("legal", GuidedLocalSearchNumber.ONE);
    active.put("overflow", GuidedLocalSearchNumber.of(BigInteger.ONE.shiftLeft(63)));
    for (int i = 0; i < 30; i++)
      active.put("lower" + i, GuidedLocalSearchNumber.of(new BigDecimal("0.1")));
    assertThatThrownBy(() -> table.incrementMaximumUtility(active, new Random(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Long.MAX_VALUE");
    assertThat(table.count("legal")).isZero();
    assertThat(table.count("overflow")).isEqualTo(Long.MAX_VALUE);
    assertThat(table.version()).isZero();
  }

  @Test
  void inactiveUnpenalizedOrdinalMetadataIsReleasedAtBarriersAndReset() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    for (int batch = 0; batch < 100; batch++) {
      var active = new LinkedHashMap<String, GuidedLocalSearchNumber>();
      for (int i = 0; i < 16; i++) active.put(batch + ":" + i, GuidedLocalSearchNumber.ONE);
      assertThat(table.incrementMaximumUtility(active)).isOne();
      assertThat(table.retainedOrdinalCount()).isEqualTo(batch + 16);
    }
    table.pruneInactive(Map.of(), Map.of());
    assertThat(table.retainedOrdinalCount()).isEqualTo(100);
    table.clear();
    assertThat(table.retainedOrdinalCount()).isZero();
    assertThat(table.snapshot().counts()).isEmpty();
  }

  @Test
  void generationViewsShareHistoryAndRemainReadOnly() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var previousGeneration = table.snapshot();
    table.incrementMaximumUtility(Map.of("a", GuidedLocalSearchNumber.ONE));
    var currentGeneration = table.snapshot();
    assertThat(currentGeneration.counts()).isSameAs(previousGeneration.counts());
    assertThat(previousGeneration.version()).isZero();
    assertThat(currentGeneration.version()).isOne();
    assertThatThrownBy(() -> currentGeneration.counts().put("a", 2L))
        .isInstanceOf(UnsupportedOperationException.class);
    table.clear();
    assertThat(table.count("a")).isZero();
    assertThat(table.version()).isEqualTo(2);
  }
}
