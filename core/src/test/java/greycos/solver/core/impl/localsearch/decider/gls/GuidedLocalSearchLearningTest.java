package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchLearningTest {

  @Test
  void signedObservationsUseLargerChangedSetAndNeutralPrior() {
    var learning = fixed(BigDecimal.ONE);
    var before = learning.snapshot();
    assertThat(before.weight(0, "unknown")).isEqualTo(4);
    learning.observe(
        delta(List.of("removed", "removed2"), List.of("added", "added2", "added3")),
        new Number[] {3L},
        new Number[] {0L},
        true);
    var after = learning.publish(Set.of("removed", "added", "unknown"), emptyPenalties(1));
    assertThat(after.weight(0, "removed")).isEqualTo(8);
    assertThat(after.weight(0, "added")).isOne();
    assertThat(after.weight(0, "unknown")).isEqualTo(4);
    assertThat(before.weight(0, "removed")).isEqualTo(4);
    assertThat(after.version()).isOne();
    assertThatThrownBy(() -> after.weights(0).put("key", 16))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void truncatesTowardZeroAtEitherSignAndClampsBothExtremes() {
    var learning = fixed(BigDecimal.TEN);
    learning.observe(
        delta(List.of("positive"), List.of("negative")), new Number[] {3}, new Number[] {0}, false);
    var snapshot = learning.publish(Set.of("positive", "negative"), emptyPenalties(1));
    assertThat(snapshot.weight(0, "positive")).isEqualTo(5);
    assertThat(snapshot.weight(0, "negative")).isEqualTo(3);
    learning.observe(
        delta(List.of("maximum"), List.of("minimum")),
        new Number[] {1_000_000L},
        new Number[] {0L},
        false);
    snapshot = learning.publish(Set.of("maximum", "minimum"), emptyPenalties(1));
    assertThat(snapshot.weight(0, "maximum")).isEqualTo(16);
    assertThat(snapshot.weight(0, "minimum")).isOne();
  }

  @Test
  void featureMedianKeepsLatestEightRelevantObservationsIncludingZeros() {
    var learning = fixed(BigDecimal.ONE);
    for (int i = 0; i < 8; i++) observe(learning, "feature", 100, false);
    assertThat(learning.publish(Set.of("feature"), emptyPenalties(1)).weight(0, "feature"))
        .isEqualTo(16);
    for (int i = 0; i < 5; i++) observe(learning, "feature", 0, false);
    assertThat(learning.featureObservationCount("feature")).isEqualTo(8);
    assertThat(learning.publish(Set.of("feature"), emptyPenalties(1)).weight(0, "feature"))
        .isEqualTo(4);
  }

  @Test
  void eventExpiryRetainsPublishedWeightWithoutNewObservationsThenPrunesInactiveHistory() {
    var learning = fixed(BigDecimal.ONE);
    observe(learning, "feature", 1, false);
    assertThat(learning.publish(Set.of("feature"), emptyPenalties(1)).weight(0, "feature"))
        .isEqualTo(8);
    for (int i = 0; i < 256; i++) {
      learning.observe(
          GuidedLocalSearchFeatureTracker.AutomaticDelta.EMPTY,
          new Number[] {0},
          new Number[] {0},
          true);
    }
    assertThat(learning.featureObservationCount("feature")).isZero();
    var retained = learning.publish(Set.of("feature"), emptyPenalties(1));
    assertThat(retained.weight(0, "feature")).isEqualTo(8);
    assertThat(retained.retainedEventCount()).isEqualTo(256);
    var pruned = learning.publish(Set.of(), emptyPenalties(1));
    assertThat(pruned.retainedFeatureCount()).isZero();
    assertThat(pruned.weight(0, "feature")).isEqualTo(4);
  }

  @Test
  void unpublishedTransientHistoryIsBoundedWithoutAnyPenaltyBarrier() {
    var learning = fixed(BigDecimal.ONE);
    for (int i = 0; i < 10_000; i++) observe(learning, "candidate" + i, 1, false);
    assertThat(learning.retainedFeatureCount()).isEqualTo(256);
    assertThat(learning.featureObservationCount("candidate0")).isZero();
    assertThat(learning.featureObservationCount("candidate9999")).isOne();
  }

  @Test
  void aPenaltyAtAnyLevelRetainsHistoryAndOnlyEligibleNonzeroSamplesCalibrate() {
    var learning = new GuidedLocalSearchLearning(Collections.nCopies(2, null));
    var change = delta(List.of("feature"), List.of());
    learning.observe(change, new Number[] {100, 1000}, new Number[] {0, 0}, false);
    var penalties =
        List.of(
            new GuidedLocalSearchPenaltyTable.Snapshot<Object>(0, Map.of()),
            new GuidedLocalSearchPenaltyTable.Snapshot<Object>(1, Map.of("feature", 1L)));
    var first = learning.publish(Set.of(), penalties);
    assertThat(first.retainedFeatureCount()).isOne();
    assertThat(first.calibrated()).containsExactly(false, false);
    assertThat(first.scales())
        .containsExactly(GuidedLocalSearchScale.ONE, GuidedLocalSearchScale.ONE);
    learning.observe(change, new Number[] {7, 0}, new Number[] {0, 0}, true);
    var second = learning.publish(Set.of(), penalties);
    assertThat(second.scales())
        .containsExactly(
            GuidedLocalSearchScale.of(new BigDecimal("7")), GuidedLocalSearchScale.ONE);
    assertThat(second.calibrated()).containsExactly(true, false);
    assertThat(second.scaleObservationCounts()).containsExactly(1, 0);
  }

  @Test
  void scaleChangeAloneRetainsPublishedFeatureWeight() {
    var learning = new GuidedLocalSearchLearning(Collections.singletonList(null));
    observe(learning, "first", 1, true);
    var first = learning.publish(Set.of("first"), emptyPenalties(1));
    assertThat(first.weight(0, "first")).isEqualTo(8);
    observe(learning, "second", 100, true);
    var second = learning.publish(Set.of("first", "second"), emptyPenalties(1));
    assertThat(second.scales().getFirst())
        .isEqualTo(GuidedLocalSearchScale.of(new BigDecimal("2")));
    assertThat(second.weight(0, "first")).isEqualTo(8);
  }

  @Test
  void nativeDoubleDifferenceIsNotRoundedToItsDecimalSpelling() {
    var learning = fixed(new BigDecimal("0.1"));
    learning.observe(
        delta(List.of("feature"), List.of()), new Number[] {0.3d}, new Number[] {0.2d}, false);
    assertThat(learning.publish(Set.of("feature"), emptyPenalties(1)).weight(0, "feature"))
        .isEqualTo(7);
  }

  @Test
  void evenFeatureMedianKeepsNonTerminatingRational() {
    var learning = fixed(BigDecimal.ONE);
    learning.observe(
        delta(List.of("feature", "a", "b"), List.of()), new Number[] {1}, new Number[] {0}, false);
    learning.observe(
        delta(List.of("feature", "c", "d"), List.of()), new Number[] {2}, new Number[] {0}, false);
    assertThat(learning.publish(Set.of("feature"), emptyPenalties(1)).weight(0, "feature"))
        .isEqualTo(6);
  }

  private static GuidedLocalSearchLearning fixed(BigDecimal scale) {
    return new GuidedLocalSearchLearning(List.of(scale));
  }

  private static void observe(
      GuidedLocalSearchLearning learning, Object key, int difference, boolean scaleEligible) {
    learning.observe(
        delta(List.of(key), List.of()), new Number[] {difference}, new Number[] {0}, scaleEligible);
  }

  private static GuidedLocalSearchFeatureTracker.AutomaticDelta delta(
      List<Object> removed, List<Object> added) {
    return new GuidedLocalSearchFeatureTracker.AutomaticDelta(removed, added);
  }

  private static List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> emptyPenalties(int levels) {
    return Collections.nCopies(levels, new GuidedLocalSearchPenaltyTable.Snapshot<>(0, Map.of()));
  }
}
