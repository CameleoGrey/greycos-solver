package greycos.solver.core.impl.score.director.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class BavetRawScoreAnalysisTest {
  private record Reason(String text) implements ConstraintJustification {}

  @Test
  void rawCountDoesNotChangeGroupedCountAndTupleDuplicatesRemainVisible() {
    for (var policy :
        List.of(
            ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS,
            ConstraintMatchPolicy.ENABLED_WITH_RAW_INDICTMENTS,
            ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS)) {
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
              TestdataSolution.buildSolutionDescriptor(),
              constraintFactory ->
                  new Constraint[] {
                    constraintFactory
                        .forEach(TestdataEntity.class)
                        .join(TestdataEntity.class)
                        .filter((left, right) -> left == right)
                        .penalize(SimpleScore.ONE)
                        .justifyWith((left, right, score) -> new Reason("same"))
                        .asConstraint("constraint")
                  },
              EnvironmentMode.NO_ASSERT);
      try (var director =
          factory.createScoreDirectorBuilder().withConstraintMatchPolicy(policy).build()) {
        var solution = TestdataSolution.generateSolution(1, 2);
        director.setWorkingSolution(solution);
        var analysis = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
        var constraint = analysis.constraintMap().get(ConstraintRef.of("constraint"));
        assertThat(constraint.matchCount()).isOne();
        assertThat(constraint.score()).isEqualTo(SimpleScore.of(-2));
        if (!policy.isMatchOccurrenceEnabled()) {
          assertThat(constraint.rawMatchCount()).isNull();
          assertThat(analysis.rawAnalysis()).isNull();
          continue;
        }
        assertThat(constraint.rawMatchCount()).isEqualTo(2L);
        var raw = analysis.rawAnalysis();
        assertThat(raw.matchOccurrences()).hasSize(2);
        for (var occurrence : raw.matchOccurrences()) {
          assertThat(occurrence.indictedObjects()).hasSize(2);
          assertThat(occurrence.indictedObjects().getFirst())
              .isSameAs(occurrence.indictedObjects().getLast());
          assertThat(occurrence.matchScore()).isEqualTo(SimpleScore.of(-1));
          assertThat(raw.indictmentMap().get(occurrence.indictedObjects().getFirst()).getFirst())
              .isSameAs(occurrence);
          if (raw.originalsFetched()) {
            assertThat(occurrence.originalIndictedObjects()).hasSize(1);
            assertThat(occurrence.originalIndictedObjects().getFirst())
                .isSameAs(occurrence.indictedObjects().getFirst());
          } else {
            assertThat(occurrence.originalIndictedObjects()).isNull();
          }
        }
        var removed = solution.getEntityList().getFirst();
        director.beforeEntityRemoved(removed);
        solution.getEntityList().remove(removed);
        director.afterEntityRemoved(removed);
        var after = director.buildScoreAnalysis(ScoreAnalysisFetchPolicy.FETCH_ALL);
        assertThat(after.rawAnalysis().matchOccurrences()).hasSize(1);
        assertThat(after.constraintMap().get(ConstraintRef.of("constraint")).rawMatchCount())
            .isEqualTo(1L);
        assertThat(raw.matchOccurrences()).hasSize(2);
      }
    }
  }

  @Test
  void optionsSelectRawPoliciesWithoutChangingLegacyMatches() {
    assertThat(
            ConstraintMatchPolicy.match(
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL, true)))
        .isEqualTo(ConstraintMatchPolicy.ENABLED_WITH_RAW_INDICTMENTS);
    assertThat(
            ConstraintMatchPolicy.match(
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL,
                    IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL,
                    true)))
        .isEqualTo(ConstraintMatchPolicy.ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS);
    assertThat(
            ConstraintMatchPolicy.match(
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL, false)))
        .isEqualTo(ConstraintMatchPolicy.ENABLED_WITH_INDICTMENTS);
    assertThat(ConstraintMatchPolicy.match(ScoreAnalysisFetchPolicy.FETCH_ALL))
        .isEqualTo(ConstraintMatchPolicy.ENABLED);
  }
}
