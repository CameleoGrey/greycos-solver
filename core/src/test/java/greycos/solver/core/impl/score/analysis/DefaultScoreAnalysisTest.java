package greycos.solver.core.impl.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
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
}
