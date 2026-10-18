package greycos.solver.core.impl.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.ScoreArithmetic;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;

import org.junit.jupiter.api.Test;

class RawScoreAnalysisTest {

  private static final ConstraintRef REF = ConstraintRef.of("constraint");
  private static final Reason REASON = new Reason("reason");

  private interface NamedReason extends ConstraintJustification {}

  private record Reason(String name) implements NamedReason {}

  private record OtherReason(String name) implements ConstraintJustification {}

  private record Key(String id) {}

  @Test
  void smallAndGeneralAssociationListsPreserveIdentityAndEqualityContracts() {
    var a = new Key("A");
    var equalA = new Key("A");
    var b = new Key("B");
    List<List<Key>> selections =
        List.of(
            List.of(),
            List.of(a),
            List.of(a, a),
            List.of(a, equalA),
            List.of(a, b),
            List.of(a, equalA, b),
            List.of(a, b, a));
    int[] expectedKeyCounts = {0, 1, 1, 1, 2, 2, 2};
    for (int i = 0; i < selections.size(); i++) {
      var selected = selections.get(i);
      var row = occurrence(-1, REASON, selected, null);
      var raw = new DefaultRawScoreAnalysis<>(List.of(row), false);
      assertThat(row.indictedObjects()).containsExactlyElementsOf(selected);
      assertThat(raw.indictmentMap()).hasSize(expectedKeyCounts[i]);
      assertThat(raw.indictmentMap().values())
          .allSatisfy(rows -> assertThat(rows).containsExactly(row));
      if (!selected.isEmpty()) {
        assertThat(raw.indictmentMap().firstEntry().getKey()).isSameAs(a);
      }
    }
    for (var originals :
        List.of(List.of(), List.of(a), List.of(a, equalA), List.of(a, equalA, b))) {
      var row = occurrence(-1, REASON, List.of(), originals);
      var raw = new DefaultRawScoreAnalysis<>(List.of(row), true);
      assertThat(row.originalIndictedObjects()).hasSize(originals.size());
      assertThat(raw.originalIndictmentMap())
          .hasSize(originals.isEmpty() ? 0 : originals.size() == 3 ? 2 : 1);
      assertThat(raw.originalIndictmentMap().values())
          .allSatisfy(rows -> assertThat(rows).containsExactly(row));
    }
    assertThatThrownBy(() -> occurrence(-1, REASON, List.of(), List.of(a, b, a)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source reference");
  }

  @Test
  void duplicateOccurrencesHaveDistinctIdentitiesAndIndexesShareCanonicalInstances() {
    var a = new Key("A");
    var equalA = new Key("A");
    var first = occurrence(-2, REASON, List.of(a, a, equalA), List.of(a, equalA));
    var second = occurrence(-2, REASON, List.of(a), List.of(a));
    var raw = new DefaultRawScoreAnalysis<>(List.of(first, second), true);
    assertThat(first).isNotEqualTo(second);
    assertThat(raw.matchOccurrences()).containsExactly(first, second);
    assertThat(raw.constraintMap().get(REF)).containsExactly(first, second);
    assertThat(raw.indictmentMap()).hasSize(1);
    assertThat(raw.indictmentMap().get(equalA).getFirst()).isSameAs(first);
    assertThat(raw.originalIndictmentMap().get(equalA).getFirst()).isSameAs(first);
    assertThat(first.indictedObjects()).hasSize(3);
    assertThat(first.indictedObjects().get(0)).isSameAs(a);
    assertThat(first.indictedObjects().get(1)).isSameAs(a);
    assertThat(first.indictedObjects().get(2)).isSameAs(equalA);
    assertThat(first.originalIndictedObjects()).hasSize(2);
    assertThat(raw.indictmentMap().firstEntry().getKey()).isSameAs(a);
    assertThatThrownBy(() -> raw.matchOccurrences().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> raw.constraintMap().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> raw.indictmentMap().get(a).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> raw.originalIndictmentMap().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void selectionsAndCanonicalListAreCopiedWithoutCopyingObjects() {
    var object = new Key("A");
    var selected = new ArrayList<Object>(List.of(object));
    var row = occurrence(-1, REASON, selected, selected);
    var rows = new ArrayList<MatchOccurrenceAnalysis<SimpleScore>>(List.of(row));
    var raw = new DefaultRawScoreAnalysis<>(rows, true);
    selected.clear();
    rows.clear();
    assertThat(raw.matchOccurrences()).containsExactly(row);
    assertThat(row.indictedObjects().getFirst()).isSameAs(object);
    assertThat(row.originalIndictedObjects().getFirst()).isSameAs(object);
  }

  @Test
  void malformedOccurrencesAndAvailabilityAreRejected() {
    assertThatThrownBy(
            () ->
                new DefaultMatchOccurrenceAnalysis<>(
                    REF, SimpleScore.ZERO, REASON, 0, List.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("+1 or -1");
    assertThatThrownBy(() -> occurrence(0, REASON, Arrays.asList("A", null), null))
        .isInstanceOf(NullPointerException.class);
    var source = new Object();
    assertThatThrownBy(() -> occurrence(0, REASON, List.of(), List.of(source, source)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("source reference");
    var row = occurrence(0, REASON, List.of(), null);
    assertThatThrownBy(() -> new DefaultRawScoreAnalysis<>(List.of(row, row), false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("distinct instances");
    assertThatThrownBy(() -> new DefaultRawScoreAnalysis<>(List.of(row), true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("availability");
  }

  @Test
  void duplicateCancellationPreservesAnIndividualSurvivorAndRemovalImpact() {
    var first = occurrence(-2, REASON, List.of("A"), null);
    var second = occurrence(-2, REASON, List.of("A"), null);
    var old = occurrence(-2, REASON, List.of("A"), null);
    var two = new DefaultRawScoreAnalysis<>(List.of(first, second), false);
    var one = new DefaultRawScoreAnalysis<>(List.of(old), false);
    var addition = DefaultRawScoreAnalysis.diff(two, one);
    assertThat(addition.matchOccurrences()).containsExactly(second);
    var removal = DefaultRawScoreAnalysis.diff(one, two).matchOccurrences().getFirst();
    assertThat(removal.occurrenceSign()).isEqualTo(-1);
    assertThat(removal.matchScore()).isEqualTo(SimpleScore.of(-2));
    assertThat(removal.score()).isEqualTo(SimpleScore.of(2));
    assertThat(DefaultRawScoreAnalysis.diff(two, two).matchOccurrences()).isEmpty();
  }

  @Test
  void associationOrderIsIgnoredButMultiplicityAndChangedImpactAreNot() {
    var before = raw(occurrence(-2, REASON, List.of("A", "B", "A"), null));
    var reordered = raw(occurrence(-2, REASON, List.of("B", new String("A"), "A"), null));
    assertThat(DefaultRawScoreAnalysis.diff(reordered, before).matchOccurrences()).isEmpty();
    var oneLess = raw(occurrence(-2, REASON, List.of("A", "B"), null));
    var associationsChanged = DefaultRawScoreAnalysis.diff(oneLess, before);
    assertThat(associationsChanged.matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::occurrenceSign)
        .containsExactly(1, -1);
    assertThat(associationsChanged.indictmentMap().get("A")).hasSize(2);
    assertThat(associationsChanged.indictmentMap().get("B")).hasSize(2);
    var changedScore =
        DefaultRawScoreAnalysis.diff(
            raw(occurrence(-3, REASON, List.of("A", "B", "A"), null)), before);
    assertThat(changedScore.matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::matchScore)
        .containsExactly(SimpleScore.of(-3), SimpleScore.of(-2));
    assertThat(changedScore.matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::score)
        .containsExactly(SimpleScore.of(-3), SimpleScore.of(2));
  }

  @Test
  void originalSourceBagsRetainEqualButDistinctIdentityMultiplicity() {
    var a = new Key("A");
    var equalA = new Key("A");
    var before =
        new DefaultRawScoreAnalysis<>(
            List.of(occurrence(0, REASON, List.of(), List.of(a, equalA))), true);
    var after =
        new DefaultRawScoreAnalysis<>(
            List.of(occurrence(0, REASON, List.of(), List.of(new Key("A")))), true);
    var difference = DefaultRawScoreAnalysis.diff(after, before);
    assertThat(difference.matchOccurrences()).hasSize(2);
    assertThat(difference.originalIndictmentMap().get(a)).hasSize(2);
    assertThat(difference.indictmentMap()).isEmpty();
  }

  @Test
  void signedDiffsCanBeSubtractedRepeatedly() {
    var two =
        raw(occurrence(-2, REASON, List.of("A"), null), occurrence(-2, REASON, List.of("A"), null));
    var one = raw(occurrence(-2, REASON, List.of("A"), null));
    var empty = new DefaultRawScoreAnalysis<SimpleScore>(List.of(), false);
    var delta = DefaultRawScoreAnalysis.diff(two, one);
    var reversed = DefaultRawScoreAnalysis.diff(empty, delta);
    var doubled = DefaultRawScoreAnalysis.diff(delta, reversed);
    assertThat(doubled.matchOccurrences())
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.occurrenceSign()).isEqualTo(1);
              assertThat(row.matchScore()).isEqualTo(SimpleScore.of(-2));
            });
    assertThat(doubled.matchOccurrences().getFirst())
        .isNotSameAs(doubled.matchOccurrences().getLast());
    assertThat(DefaultRawScoreAnalysis.diff(doubled, two).matchOccurrences()).isEmpty();
  }

  @Test
  void snapshotValueEqualityDoesNotCollapseDuplicateRowIdentity() {
    var first =
        raw(occurrence(-1, REASON, List.of("A"), null), occurrence(-1, REASON, List.of("A"), null));
    var copy =
        raw(
            occurrence(-1, new Reason("reason"), List.of(new String("A")), null),
            occurrence(-1, REASON, List.of("A"), null));
    assertThat(first).isEqualTo(copy).hasSameHashCodeAs(copy);
    assertThat(first.matchOccurrences().getFirst())
        .isNotEqualTo(copy.matchOccurrences().getFirst());
    assertThat(first).isNotEqualTo(raw(occurrence(-1, REASON, List.of("A"), null)));
  }

  @Test
  void fetchedEmptyAndUnavailableAreDistinctIncludingOriginalAvailability() {
    var empty = new DefaultRawScoreAnalysis<SimpleScore>(List.of(), false);
    var withOriginals = new DefaultRawScoreAnalysis<SimpleScore>(List.of(), true);
    assertThat(empty.originalIndictmentMap()).isNull();
    assertThat(withOriginals.originalIndictmentMap()).isEmpty();
    assertThat(DefaultRawScoreAnalysis.diff(empty, empty).matchOccurrences()).isEmpty();
    assertThat(DefaultRawScoreAnalysis.<SimpleScore>diff(null, null)).isNull();
    assertThatThrownBy(() -> DefaultRawScoreAnalysis.diff(empty, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> DefaultRawScoreAnalysis.diff(null, empty))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> DefaultRawScoreAnalysis.diff(empty, withOriginals))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rawOnlyAssociationChangesKeepConstraintAndRawIndexesWhenIndictmentCancels() {
    var before = analysis(List.of(occurrence(-2, REASON, List.of("A", "B"), null)));
    var after = analysis(List.of(occurrence(-2, REASON, List.of("A", "C"), null)));
    var difference = after.diff(before);
    var constraint = difference.getConstraintAnalysis(REF);
    assertThat(constraint).isNotNull();
    assertThat(constraint.score()).isEqualTo(SimpleScore.ZERO);
    assertThat(constraint.matches()).isEmpty();
    assertThat(constraint.rawMatchCount()).isZero();
    assertThat(difference.indictmentMap()).doesNotContainKey("A").containsOnlyKeys("B", "C");
    assertThat(difference.rawAnalysis().indictmentMap().get("A")).hasSize(2);
    assertThat(difference.summarize()).contains("2 raw occurrences (net count 0)");
  }

  @Test
  void rawCountsPreserveZeroScoreDuplicateChangesAndNegativeDiffOfDiffCounts() {
    var two =
        analysis(
            List.of(
                occurrence(0, REASON, List.of(), null), occurrence(0, REASON, List.of(), null)));
    var one = analysis(List.of(occurrence(0, REASON, List.of(), null)));
    var difference = two.diff(one);
    assertThat(two.getConstraintAnalysis(REF).matchCount()).isEqualTo(1);
    assertThat(two.getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(2L);
    assertThat(difference.getConstraintAnalysis(REF).matchCount()).isZero();
    assertThat(difference.getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(1L);
    assertThat(difference.getConstraintAnalysis(REF).matches()).isEmpty();
    var empty = analysis(List.of());
    var removed = empty.diff(one);
    assertThat(removed.getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(-1L);
    assertThat(empty.diff(removed).getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(1L);
    assertThat(empty.diff(removed).getConstraintAnalysis(REF).matchCount()).isEqualTo(1);
  }

  @Test
  void floatingRawDeltasNeedNotSumToDifferenceOfRoundedTotals() {
    var before = floatingAnalysis(List.of(0x1p53, 1d));
    var after = floatingAnalysis(List.of(0x1p53, 1d, 1d));
    var difference = after.diff(before);
    assertThat(difference.score()).isEqualTo(SimpleDoubleScore.of(2));
    assertThat(difference.rawAnalysis().matchOccurrences())
        .singleElement()
        .satisfies(row -> assertThat(row.score()).isEqualTo(SimpleDoubleScore.ONE));
    assertThat(difference.getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(1L);
  }

  @Test
  void justificationHelpersPreserveGlobalOccurrencesAndDistinctIndictmentValues() {
    var other = new OtherReason("other");
    var value =
        analysis(
            List.of(
                occurrence(-1, REASON, List.of("A"), null),
                occurrence(-1, new Reason("reason"), List.of("A"), null),
                occurrence(-1, other, List.of("A"), null)));
    assertThat(value.justifications()).containsExactly(REASON, REASON, other);
    assertThat(value.justifications(NamedReason.class)).containsExactly(REASON, REASON);
    assertThat(value.rawAnalysis().justifications(OtherReason.class)).containsExactly(other);
    assertThat(value.getIndictmentAnalysis("A").justifications()).containsExactly(REASON, other);
    assertThat(value.getIndictmentAnalysis("A").justifications(NamedReason.class))
        .containsExactly(REASON);
    assertThatThrownBy(() -> value.justifications(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> value.justifications().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of()).justifications())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("includeMatchOccurrences");
  }

  @Test
  void summariesHaveIndependentObjectAndGroupLimitsWithoutChangingUnfetchedText() {
    var legacy =
        new DefaultScoreAnalysis<>(
            SimpleScore.ONE,
            Map.of(
                REF,
                new DefaultConstraintAnalysis<>(
                    REF,
                    SimpleScore.ONE,
                    SimpleScore.ONE,
                    List.of(new DefaultMatchAnalysis<>(REF, SimpleScore.ONE, REASON)))));
    assertThat(legacy.rawAnalysis()).isNull();
    assertThat(legacy.getConstraintAnalysis(REF).rawMatchCount()).isNull();
    assertThat(legacy.summarize())
        .isEqualTo(
            """
        Explanation of score (1):
            Constraint matches:
                1: constraint (constraint) has 1 matches:
                    1: justified with (Reason[name=reason])
        """);
    var builder = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    for (int object = 0; object < 6; object++) {
      for (int group = 0; group < 4; group++) {
        builder.add(
            new ConstraintMatch<>(ConstraintRef.of("c" + group), REASON, SimpleScore.of(-1)),
            List.of("object" + object));
      }
    }
    var maps = new IndictmentMaps<>(builder.build(), builder.build());
    var value = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of(), true, List.of(), maps);
    assertThat(value.summarize())
        .contains(
            "Terminal indictments:",
            "Original indictments:",
            "... and 1 more indicted objects",
            "... and 1 more match groups")
        .doesNotContain("object5");
    assertThat(value.summarize(1, 1))
        .contains("... and 5 more indicted objects", "... and 3 more match groups");
    assertThat(value.getIndictmentAnalysis("object0").summarize())
        .contains("4 contributing occurrences", "... and 1 more match groups");
    assertThatThrownBy(() -> value.summarize(1, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> value.getIndictmentAnalysis("object0").summarize(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MatchOccurrenceAnalysis<SimpleScore> occurrence(
      long score, ConstraintJustification justification, List<?> terminal, List<?> original) {
    return new DefaultMatchOccurrenceAnalysis<>(
        REF, SimpleScore.of(score), justification, 1, terminal, original);
  }

  @SafeVarargs
  private static DefaultRawScoreAnalysis<SimpleScore> raw(
      MatchOccurrenceAnalysis<SimpleScore>... rows) {
    return new DefaultRawScoreAnalysis<>(List.of(rows), false);
  }

  private static DefaultScoreAnalysis<SimpleScore> analysis(
      List<MatchOccurrenceAnalysis<SimpleScore>> rows) {
    var raw = new DefaultRawScoreAnalysis<>(rows, false);
    var grouped = new LinkedHashMap<ConstraintJustification, List<SimpleScore>>();
    var indictmentBuilder = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    for (var row : rows) {
      grouped.computeIfAbsent(row.justification(), ignored -> new ArrayList<>()).add(row.score());
      indictmentBuilder.add(
          new ConstraintMatch<>(row.constraintRef(), row.justification(), row.score()),
          row.indictedObjects());
    }
    var score =
        ScoreArithmetic.sum(SimpleScore.ZERO, rows.stream().map(MatchOccurrenceAnalysis::score));
    var constraints = new LinkedHashMap<ConstraintRef, ConstraintAnalysis<SimpleScore>>();
    if (!rows.isEmpty()) {
      var matches = new ArrayList<MatchAnalysis<SimpleScore>>();
      grouped.forEach(
          (reason, scores) ->
              matches.add(
                  new DefaultMatchAnalysis<>(
                      REF, ScoreArithmetic.sum(SimpleScore.ZERO, scores.stream()), reason)));
      constraints.put(
          REF,
          new DefaultConstraintAnalysis<>(
              REF, SimpleScore.ONE, score, matches, matches.size(), (long) rows.size()));
    }
    return new DefaultScoreAnalysis<>(
        score,
        constraints,
        true,
        List.of(),
        new IndictmentMaps<>(indictmentBuilder.build(), null),
        raw);
  }

  private static DefaultScoreAnalysis<SimpleDoubleScore> floatingAnalysis(List<Double> values) {
    var rows =
        values.stream()
            .<MatchOccurrenceAnalysis<SimpleDoubleScore>>map(
                value ->
                    new DefaultMatchOccurrenceAnalysis<>(
                        REF, SimpleDoubleScore.of(value), REASON, 1, List.of(), null))
            .toList();
    var score =
        ScoreArithmetic.sum(
            SimpleDoubleScore.ZERO, rows.stream().map(MatchOccurrenceAnalysis::score));
    var grouped =
        new DefaultConstraintAnalysis<>(
            REF,
            SimpleDoubleScore.ONE,
            score,
            List.<MatchAnalysis<SimpleDoubleScore>>of(
                new DefaultMatchAnalysis<>(REF, score, REASON)),
            1,
            (long) values.size());
    return new DefaultScoreAnalysis<>(
        score,
        Map.of(REF, grouped),
        true,
        List.of(),
        new IndictmentMaps<>(new LinkedHashMap<>(), null),
        new DefaultRawScoreAnalysis<>(rows, false));
  }
}
