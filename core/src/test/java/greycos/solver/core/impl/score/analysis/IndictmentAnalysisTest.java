package greycos.solver.core.impl.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IndictmentAnalysisTest {

  private static final ConstraintRef FIRST = ConstraintRef.of("first");
  private static final ConstraintRef SECOND = ConstraintRef.of("second");
  private static final Reason REASON = new Reason("reason");

  private record Reason(String text) implements ConstraintJustification {}

  private record Key(String id) {}

  static Stream<Score<?>> scoreFamilies() {
    return Stream.of(
        SimpleScore.ONE,
        SimpleBigDecimalScore.of(BigDecimal.ONE),
        SimpleFloatScore.ONE,
        SimpleDoubleScore.ONE,
        HardSoftScore.of(1, 2),
        HardSoftBigDecimalScore.of(BigDecimal.ONE, BigDecimal.TWO),
        HardSoftFloatScore.of(1, 2),
        HardSoftDoubleScore.of(1, 2),
        HardMediumSoftScore.of(1, 2, 3),
        HardMediumSoftBigDecimalScore.of(BigDecimal.ONE, BigDecimal.TWO, BigDecimal.valueOf(3)),
        HardMediumSoftFloatScore.of(1, 2, 3),
        HardMediumSoftDoubleScore.of(1, 2, 3),
        BendableScore.of(new long[] {1}, new long[] {2, 3}),
        BendableBigDecimalScore.of(
            new BigDecimal[] {BigDecimal.ONE},
            new BigDecimal[] {BigDecimal.TWO, BigDecimal.valueOf(3)}),
        BendableFloatScore.of(new float[] {1}, new float[] {2, 3}),
        BendableDoubleScore.of(new double[] {1}, new double[] {2, 3}),
        new OtherScore(1));
  }

  @ParameterizedTest
  @MethodSource("scoreFamilies")
  <Score_ extends Score<Score_>> void builtInAndCustomScoreFamiliesPreserveOccurrences(
      Score_ unit) {
    var builder = new IndictmentAnalysisBuilder<>(unit.zero());
    var match = new ConstraintMatch<>(FIRST, REASON, unit);
    builder.add(match, List.of("A", "A"));
    builder.add(new ConstraintMatch<>(FIRST, REASON, unit), List.of("A"));
    var indictment = builder.build().get("A");
    assertThat(indictment.score()).isEqualTo(unit.add(unit));
    assertThat(indictment.matchCount()).isEqualTo(2);
    assertThat(indictment.matches())
        .singleElement()
        .satisfies(
            group -> {
              assertThat(group.matchCount()).isEqualTo(2);
              assertThat(group.score()).isEqualTo(unit.add(unit));
            });
  }

  @Test
  void equalJustificationsDoNotTransferScoresBetweenObjects() {
    var builder = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var key = new Key("A");
    builder.add(
        new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-2)), List.of(key, new Key("A")));
    builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-3)), List.of("B"));
    builder.add(new ConstraintMatch<>(SECOND, REASON, SimpleScore.of(5)), List.of(key, "B"));
    var result = builder.build();
    assertThat(result.sequencedKeySet()).containsExactly(key, "B");
    assertThat(result.firstEntry().getKey()).isSameAs(key);
    assertThat(result.get(new Key("A")).score()).isEqualTo(SimpleScore.of(3));
    assertThat(result.get("B").score()).isEqualTo(SimpleScore.of(2));
    assertThat(result.get(key).matches())
        .extracting(IndictmentMatchAnalysis::constraintRef)
        .containsExactly(FIRST, SECOND);
  }

  @Test
  void snapshotsAreDetachedFromSelectionsAndSubsequentBuilderUse() {
    var builder = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var selected = new ArrayList<Object>(List.of("A"));
    builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-2)), selected);
    selected.clear();
    var first = builder.build();
    builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-3)), List.of("B"));
    assertThat(first).containsOnlyKeys("A");
    assertThat(builder.build()).containsOnlyKeys("A", "B");
    assertThatThrownBy(() -> first.clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> first.get("A").matches().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void invalidSelectionDoesNotPartiallyAddObjects() {
    var builder = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var match = new ConstraintMatch<>(FIRST, REASON, SimpleScore.ONE);
    assertThatThrownBy(() -> builder.add(match, Arrays.asList("A", null)))
        .isInstanceOf(NullPointerException.class);
    assertThat(builder.build()).isEmpty();
    builder.add(match, List.of());
    assertThat(builder.build()).isEmpty();
  }

  @Test
  void floatingTotalUsesRawContributionsRatherThanRoundedGroups() {
    var builder = new IndictmentAnalysisBuilder<>(SimpleDoubleScore.ZERO);
    builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleDoubleScore.of(0x1p53)), List.of("A"));
    builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleDoubleScore.ONE), List.of("A"));
    builder.add(new ConstraintMatch<>(SECOND, REASON, SimpleDoubleScore.ONE), List.of("A"));
    var indictment = builder.build().get("A");
    assertThat(indictment.score()).isEqualTo(SimpleDoubleScore.of(0x1p53 + 2));
    assertThat(indictment.matches().getFirst().score()).isEqualTo(SimpleDoubleScore.of(0x1p53));
    assertThat(indictment.matches().getLast().score()).isEqualTo(SimpleDoubleScore.ONE);
  }

  @Test
  void floatingIntermediateOverflowCanCancelBeforeExtraction() {
    var builder = new IndictmentAnalysisBuilder<>(SimpleDoubleScore.ZERO);
    for (var value : new double[] {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE}) {
      builder.add(new ConstraintMatch<>(FIRST, REASON, SimpleDoubleScore.of(value)), List.of("A"));
    }
    assertThat(builder.build().get("A").score()).isEqualTo(SimpleDoubleScore.of(Double.MAX_VALUE));
  }

  @Test
  void diffPreservesZeroScoreRedistributionBetweenJustifications() {
    var left = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var right = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    left.add(new ConstraintMatch<>(FIRST, new Reason("new"), SimpleScore.ZERO), List.of("A"));
    right.add(new ConstraintMatch<>(FIRST, new Reason("old"), SimpleScore.ZERO), List.of("A"));
    var difference = scoreAnalysis(left).diff(scoreAnalysis(right));
    assertThat(difference.constraintMap()).isEmpty();
    var indictment = difference.getIndictmentAnalysis("A");
    assertThat(indictment.score()).isEqualTo(SimpleScore.ZERO);
    assertThat(indictment.matchCount()).isZero();
    assertThat(indictment.matches())
        .extracting(IndictmentMatchAnalysis::matchCount)
        .containsExactly(1L, -1L);
    assertThat(indictment.matches())
        .extracting(IndictmentMatchAnalysis::justification)
        .containsExactly(new Reason("new"), new Reason("old"));
    assertThat(difference.originalIndictmentMap()).isNull();
  }

  @Test
  void diffPreservesOwnershipChangesAndNegatesRemovedGroups() {
    var left = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var right = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    left.add(new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-3)), List.of("new"));
    right.add(new ConstraintMatch<>(FIRST, REASON, SimpleScore.of(-3)), List.of("old"));
    var difference = scoreAnalysis(left).diff(scoreAnalysis(right));
    assertThat(difference.getIndictmentAnalysis("new").score()).isEqualTo(SimpleScore.of(-3));
    assertThat(difference.getIndictmentAnalysis("old").score()).isEqualTo(SimpleScore.of(3));
    assertThat(difference.getIndictmentAnalysis("old").matchCount()).isEqualTo(-1);
    assertThat(scoreAnalysis(left).diff(scoreAnalysis(left)).indictmentMap()).isEmpty();
  }

  @Test
  void originalViewDiffIsIndependentFromTerminalView() {
    var terminal = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var beforeOriginal = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var afterOriginal = new IndictmentAnalysisBuilder<>(SimpleScore.ZERO);
    var match = new ConstraintMatch<>(FIRST, REASON, SimpleScore.ONE);
    terminal.add(match, List.of("terminal"));
    beforeOriginal.add(match, List.of("old source"));
    afterOriginal.add(match, List.of("new source"));
    var before = new IndictmentMaps<>(terminal.build(), beforeOriginal.build());
    var after = new IndictmentMaps<>(terminal.build(), afterOriginal.build());
    var difference = after.diff(before);
    assertThat(difference.terminal()).isEmpty();
    assertThat(difference.original()).containsOnlyKeys("old source", "new source");
    assertThat(difference.original().get("old source").matchCount()).isEqualTo(-1);
    assertThat(difference.original().get("new source").matchCount()).isEqualTo(1);
  }

  @Test
  void unavailableAndFetchedEmptyViewsAreDistinct() {
    var unavailable = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of());
    var fetched = scoreAnalysis(new IndictmentAnalysisBuilder<>(SimpleScore.ZERO));
    assertThat(unavailable.diff(unavailable).indictmentMap()).isNull();
    assertThat(fetched.diff(fetched).indictmentMap()).isEmpty();
    assertThat(fetched.getIndictmentAnalysis("absent")).isNull();
    assertThatThrownBy(() -> unavailable.getIndictmentAnalysis("absent"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> fetched.diff(unavailable))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("terminal");
    assertThatThrownBy(() -> unavailable.diff(fetched))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("terminal");
    var both =
        new DefaultScoreAnalysis<>(
            SimpleScore.ZERO,
            Map.of(),
            true,
            List.of(),
            new IndictmentMaps<SimpleScore>(new LinkedHashMap<>(), new LinkedHashMap<>()));
    assertThatThrownBy(() -> both.diff(fetched))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("original");
  }

  @Test
  void countsRemainLongAndMalformedCountsAreRejected() {
    var count = 1L + Integer.MAX_VALUE;
    var group = new DefaultIndictmentMatchAnalysis<>(FIRST, SimpleScore.ZERO, REASON, count);
    var indictment = new DefaultIndictmentAnalysis<>("A", SimpleScore.ZERO, List.of(group), count);
    var map = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    map.put("A", indictment);
    var difference =
        new IndictmentMaps<SimpleScore>(new LinkedHashMap<>(), null)
            .diff(new IndictmentMaps<>(map, null));
    assertThat(difference.terminal().get("A").matchCount()).isEqualTo(-count);
    assertThatThrownBy(
            () -> new DefaultIndictmentAnalysis<>("A", SimpleScore.ZERO, List.of(group), 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void oldFetchPoliciesRemainUnchangedAndEnabledPoliciesRequireFullMatches() {
    for (var fetch : ScoreAnalysisFetchPolicy.values()) {
      assertThat(ConstraintMatchPolicy.match(fetch, IndictmentFetchPolicy.NONE))
          .isEqualTo(ConstraintMatchPolicy.match(fetch));
    }
    assertThat(
            ConstraintMatchPolicy.match(
                ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL))
        .isEqualTo(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS);
    assertThat(
            ConstraintMatchPolicy.match(
                ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL))
        .isEqualTo(ConstraintMatchPolicy.ENABLED_WITH_ORIGINAL_INDICTMENTS);
    assertThatThrownBy(
            () ->
                ConstraintMatchPolicy.match(
                    ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT, IndictmentFetchPolicy.TERMINAL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FETCH_ALL");
  }

  private static DefaultScoreAnalysis<SimpleScore> scoreAnalysis(
      IndictmentAnalysisBuilder<SimpleScore> builder) {
    return new DefaultScoreAnalysis<>(
        SimpleScore.ZERO, Map.of(), true, List.of(), new IndictmentMaps<>(builder.build(), null));
  }

  private record OtherScore(long value) implements Score<OtherScore> {
    @Override
    public OtherScore add(OtherScore other) {
      return new OtherScore(Math.addExact(value, other.value));
    }

    @Override
    public OtherScore subtract(OtherScore other) {
      return new OtherScore(Math.subtractExact(value, other.value));
    }

    @Override
    public OtherScore multiply(double multiplicand) {
      return new OtherScore((long) Math.floor(value * multiplicand));
    }

    @Override
    public OtherScore divide(double divisor) {
      return new OtherScore((long) Math.floor(value / divisor));
    }

    @Override
    public OtherScore power(double exponent) {
      return new OtherScore((long) Math.floor(Math.pow(value, exponent)));
    }

    @Override
    public OtherScore abs() {
      return new OtherScore(Math.abs(value));
    }

    @Override
    public OtherScore zero() {
      return new OtherScore(0);
    }

    @Override
    public Number[] toLevelNumbers() {
      return new Number[] {value};
    }

    @Override
    public boolean isFeasible() {
      return true;
    }

    @Override
    public String toShortString() {
      return Long.toString(value);
    }

    @Override
    public int compareTo(OtherScore other) {
      return Long.compare(value, other.value);
    }
  }
}
