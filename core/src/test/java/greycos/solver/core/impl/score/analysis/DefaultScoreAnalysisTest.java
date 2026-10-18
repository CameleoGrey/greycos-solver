package greycos.solver.core.impl.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;

import org.junit.jupiter.api.Test;

class DefaultScoreAnalysisTest {

  private static final ConstraintRef CONSTRAINT_REF = ConstraintRef.of("constraint");

  @Test
  void diffNegatesMatchCountWhenConstraintOnlyExistsInOtherAnalysis() {
    var constraint =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.of(1), SimpleScore.of(3), null, 3);
    var left = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of());
    var right = new DefaultScoreAnalysis<>(SimpleScore.of(3), Map.of(CONSTRAINT_REF, constraint));

    var difference = left.diff(right).getConstraintAnalysis(CONSTRAINT_REF);

    assertThat(difference).isNotNull();
    assertThat(difference.weight()).isEqualTo(SimpleScore.of(-1));
    assertThat(difference.score()).isEqualTo(SimpleScore.of(-3));
    assertThat(difference.matchCount()).isEqualTo(-3);
    assertThat(difference.isMatchCountAvailable()).isTrue();
    assertThat(difference.matches()).isNull();
  }

  @Test
  void diffNegatesMatchesWhenConstraintOnlyExistsInOtherAnalysis() {
    MatchAnalysis<SimpleScore> match =
        new DefaultMatchAnalysis<>(
            CONSTRAINT_REF,
            SimpleScore.of(3),
            DefaultConstraintJustification.of(SimpleScore.of(3), "fact"));
    var constraint =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.of(1), SimpleScore.of(3), List.of(match));
    var left = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of());
    var right = new DefaultScoreAnalysis<>(SimpleScore.of(3), Map.of(CONSTRAINT_REF, constraint));

    var difference = left.diff(right).getConstraintAnalysis(CONSTRAINT_REF);

    assertThat(difference).isNotNull();
    assertThat(difference.matchCount()).isEqualTo(-1);
    assertThat(difference.isMatchCountAvailable()).isTrue();
    assertThat(difference.matches())
        .singleElement()
        .extracting(MatchAnalysis::score)
        .isEqualTo(SimpleScore.of(-3));
  }

  @Test
  void diffPreservesUnavailableMatchCountSentinel() {
    var constraint =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.of(1), SimpleScore.of(3), null, -1);
    var left = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of());
    var right = new DefaultScoreAnalysis<>(SimpleScore.of(3), Map.of(CONSTRAINT_REF, constraint));

    var difference = left.diff(right).getConstraintAnalysis(CONSTRAINT_REF);

    assertThat(difference).isNotNull();
    assertThat(difference.matchCount()).isEqualTo(-1);
    assertThat(difference.isMatchCountAvailable()).isFalse();
  }

  @Test
  void countOnlyDifferencesRetainAvailabilityThroughRepeatedSubtraction() {
    var empty = new DefaultScoreAnalysis<SimpleScore>(SimpleScore.ZERO, Map.of());
    var zero = scoreAnalysis(countedConstraint(0));
    for (int count : List.of(1, 2)) {
      var before = scoreAnalysis(countedConstraint(count));
      var delta = zero.diff(before);
      var removed = delta.getConstraintAnalysis(CONSTRAINT_REF);
      assertThat(removed.matchCount()).isEqualTo(-count);
      assertThat(removed.isMatchCountAvailable()).isTrue();
      assertThat(removed.toString()).contains(-count + " matches, justifications disabled");

      for (var reversed : List.of(empty.diff(delta), zero.diff(delta))) {
        var restored = reversed.getConstraintAnalysis(CONSTRAINT_REF);
        assertThat(restored.matchCount()).isEqualTo(count);
        assertThat(restored.isMatchCountAvailable()).isTrue();
      }
      var copied = delta.diff(empty).getConstraintAnalysis(CONSTRAINT_REF);
      assertThat(copied.matchCount()).isEqualTo(-count);
      assertThat(copied.isMatchCountAvailable()).isTrue();
      assertThat(delta.diff(delta).constraintMap()).isEmpty();
    }
  }

  @Test
  void shallowDifferencesNeverInventAvailableCountsOrRetainUnchangedConstraints() {
    var left =
        scoreAnalysis(
            new DefaultConstraintAnalysis<>(
                CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.of(3), null));
    var right =
        scoreAnalysis(
            new DefaultConstraintAnalysis<>(
                CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.of(2), null));
    var difference = left.diff(right);
    var constraint = difference.getConstraintAnalysis(CONSTRAINT_REF);
    assertThat(constraint.score()).isEqualTo(SimpleScore.ONE);
    assertThat(constraint.matchCount()).isEqualTo(-1);
    assertThat(constraint.isMatchCountAvailable()).isFalse();
    assertThat(left.diff(left).constraintMap()).isEmpty();
    assertThat(difference.diff(difference).constraintMap()).isEmpty();
    var nested = left.diff(difference).getConstraintAnalysis(CONSTRAINT_REF);
    assertThat(nested.matchCount()).isEqualTo(-1);
    assertThat(nested.isMatchCountAvailable()).isFalse();
  }

  @Test
  void unavailableCountsRemainIncompatibleWithSignedCountOnlyDifferences() {
    var shallow =
        scoreAnalysis(
            new DefaultConstraintAnalysis<>(
                CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, null));
    var delta = scoreAnalysis(countedConstraint(0)).diff(scoreAnalysis(countedConstraint(1)));
    assertThatThrownBy(() -> shallow.diff(delta))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provides a match count");
    assertThatThrownBy(() -> delta.diff(shallow))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provides a match count");
  }

  @Test
  void availableMinusOneAndUnavailableCountAreDistinctValues() {
    var unavailable =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, null, -1);
    var available =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, null, -1, null, true);
    assertThat(available).isNotEqualTo(unavailable);
    assertThat(available.isMatchCountAvailable()).isTrue();
    assertThat(unavailable.isMatchCountAvailable()).isFalse();
    assertThat(unavailable.toString()).contains("constraint matching disabled");
  }

  @Test
  void constructorsRejectContradictoryUnavailableCounts() {
    assertThatThrownBy(
            () ->
                new DefaultConstraintAnalysis<>(
                    CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, null, 0, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("matchCount (0) must be -1");
    assertThatThrownBy(
            () ->
                new DefaultConstraintAnalysis<>(
                    CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, List.of(), -1, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("matches ([]) must be null");
    assertThat(countedConstraint(-2).isMatchCountAvailable()).isTrue();
  }

  @Test
  void customCountAvailabilitySurvivesCopyNegationAndSubtraction() {
    var empty = new DefaultScoreAnalysis<SimpleScore>(SimpleScore.ZERO, Map.of());
    var available = scoreAnalysis(new CustomConstraint(-1, true));
    var unavailable = scoreAnalysis(new CustomConstraint(-1, false));
    var copied = available.diff(empty).getConstraintAnalysis(CONSTRAINT_REF);
    assertThat(copied.matchCount()).isEqualTo(-1);
    assertThat(copied.isMatchCountAvailable()).isTrue();
    var reversed = empty.diff(available).getConstraintAnalysis(CONSTRAINT_REF);
    assertThat(reversed.matchCount()).isEqualTo(1);
    assertThat(reversed.isMatchCountAvailable()).isTrue();
    var difference =
        scoreAnalysis(new CustomConstraint(0, true))
            .diff(available)
            .getConstraintAnalysis(CONSTRAINT_REF);
    assertThat(difference.matchCount()).isEqualTo(1);
    assertThat(difference.isMatchCountAvailable()).isTrue();
    assertThat(
            unavailable.diff(empty).getConstraintAnalysis(CONSTRAINT_REF).isMatchCountAvailable())
        .isFalse();
    assertThat(
            empty.diff(unavailable).getConstraintAnalysis(CONSTRAINT_REF).isMatchCountAvailable())
        .isFalse();
  }

  @Test
  void signedCountArithmeticRejectsOverflow() {
    var empty = new DefaultScoreAnalysis<SimpleScore>(SimpleScore.ZERO, Map.of());
    for (boolean withMatches : List.of(false, true)) {
      List<MatchAnalysis<SimpleScore>> matches = withMatches ? List.of() : null;
      var maximum =
          scoreAnalysis(
              new DefaultConstraintAnalysis<>(
                  CONSTRAINT_REF,
                  SimpleScore.ONE,
                  SimpleScore.ZERO,
                  matches,
                  Integer.MAX_VALUE,
                  null,
                  true));
      var minusOne =
          scoreAnalysis(
              new DefaultConstraintAnalysis<>(
                  CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, matches, -1, null, true));
      var minimum =
          scoreAnalysis(
              new DefaultConstraintAnalysis<>(
                  CONSTRAINT_REF,
                  SimpleScore.ONE,
                  SimpleScore.ZERO,
                  matches,
                  Integer.MIN_VALUE,
                  null,
                  true));
      assertThatThrownBy(() -> maximum.diff(minusOne)).isInstanceOf(ArithmeticException.class);
      assertThatThrownBy(() -> empty.diff(minimum)).isInstanceOf(ArithmeticException.class);
    }
  }

  @Test
  void diffRejectsIncompatibleFetchPolicies() {
    var withMatches =
        new DefaultConstraintAnalysis<>(
            CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, List.of());
    var withoutMatches =
        new DefaultConstraintAnalysis<>(CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.ZERO, null, 0);
    var left = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of(CONSTRAINT_REF, withMatches));
    var right =
        new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of(CONSTRAINT_REF, withoutMatches));

    assertThatThrownBy(() -> left.diff(right))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("while the other does not");
  }

  private static DefaultConstraintAnalysis<SimpleScore> countedConstraint(int matchCount) {
    return new DefaultConstraintAnalysis<>(
        CONSTRAINT_REF, SimpleScore.ONE, SimpleScore.of(matchCount), null, matchCount);
  }

  private static DefaultScoreAnalysis<SimpleScore> scoreAnalysis(
      ConstraintAnalysis<SimpleScore> constraint) {
    return new DefaultScoreAnalysis<>(constraint.score(), Map.of(CONSTRAINT_REF, constraint));
  }

  private record CustomConstraint(int matchCount, boolean isMatchCountAvailable)
      implements ConstraintAnalysis<SimpleScore> {

    @Override
    public ConstraintRef constraintRef() {
      return CONSTRAINT_REF;
    }

    @Override
    public SimpleScore weight() {
      return SimpleScore.ONE;
    }

    @Override
    public SimpleScore score() {
      return SimpleScore.ZERO;
    }

    @Override
    public List<MatchAnalysis<SimpleScore>> matches() {
      return null;
    }

    @Override
    public String summarize() {
      return "custom";
    }

    @Override
    public String summarize(int limit) {
      return summarize();
    }
  }
}
