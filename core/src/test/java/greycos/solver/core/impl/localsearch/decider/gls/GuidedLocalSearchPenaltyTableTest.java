package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

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
    assertThat(table.incrementMaximumUtility(automatic, custom, scale)).isEqualTo(2);
    assertThat(table.count("automatic")).isEqualTo(2);
    assertThat(table.count("custom")).isOne();
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
  void incrementsAllExactMaximumUtilityTies() {
    var table = new GuidedLocalSearchPenaltyTable<String>();
    var active = Map.of("a", GuidedLocalSearchNumber.of(10), "b", GuidedLocalSearchNumber.of(5));
    assertThat(table.incrementMaximumUtility(active)).isOne();
    assertThat(table.count("a")).isOne();
    assertThat(table.count("b")).isZero();
    assertThat(table.incrementMaximumUtility(active)).isEqualTo(2);
    assertThat(table.count("a")).isEqualTo(2);
    assertThat(table.count("b")).isOne();
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
        .isEqualTo(2);
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
