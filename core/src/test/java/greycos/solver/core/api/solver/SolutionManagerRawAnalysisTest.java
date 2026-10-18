package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SolutionManagerRawAnalysisTest {

  private static final ScoreAnalysisOptions RAW_BOTH =
      new ScoreAnalysisOptions(
          ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);

  @Test
  void invalidOptionsFailBeforeAnalysis() {
    assertThatThrownBy(() -> new ScoreAnalysisOptions(null, IndictmentFetchPolicy.NONE, false))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fetchPolicy");
    assertThatThrownBy(
            () -> new ScoreAnalysisOptions(ScoreAnalysisFetchPolicy.FETCH_ALL, null, false))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("indictmentPolicy");
    for (var fetch :
        List.of(
            ScoreAnalysisFetchPolicy.FETCH_SHALLOW, ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT)) {
      assertThatThrownBy(
              () -> new ScoreAnalysisOptions(fetch, IndictmentFetchPolicy.TERMINAL, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FETCH_ALL");
      assertThatThrownBy(
              () -> new ScoreAnalysisOptions(fetch, IndictmentFetchPolicy.TERMINAL, false))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FETCH_ALL");
    }
    assertThatThrownBy(
            () ->
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.NONE, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TERMINAL");
  }

  @Test
  void rawCollectionIsExplicitAndReusesEachSelection() {
    var manager = manager(Constraints.class);
    var solution = TestdataSolution.generateSolution(3, 2);
    Constraints.mappingCalls.set(0);
    var legacy = manager.analyze(solution);
    assertThat(legacy.score()).isEqualTo(SimpleScore.of(-5));
    assertThat(legacy.rawAnalysis()).isNull();
    assertThat(legacy.constraintAnalyses())
        .singleElement()
        .satisfies(
            constraint -> {
              assertThat(constraint.matchCount()).isOne();
              assertThat(constraint.rawMatchCount()).isNull();
            });
    assertThat(
            manager.analyzeWithOptions(
                solution,
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.NONE, false)))
        .isEqualTo(legacy);
    assertThat(Constraints.mappingCalls).hasValue(0);

    var analysis = manager.analyzeWithOptions(solution, RAW_BOTH);
    assertThat(Constraints.mappingCalls).hasValue(2);
    assertThat(analysis.score()).isEqualTo(legacy.score());
    assertThat(analysis.constraintAnalyses())
        .singleElement()
        .satisfies(
            constraint -> {
              assertThat(constraint.matchCount()).isOne();
              assertThat(constraint.rawMatchCount()).isEqualTo(2L);
            });
    var raw = analysis.rawAnalysis();
    assertThat(raw).isNotNull();
    assertThat(raw.originalsFetched()).isTrue();
    assertThat(raw.matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::matchScore)
        .containsExactlyInAnyOrder(SimpleScore.of(-2), SimpleScore.of(-3));
    for (var occurrence : raw.matchOccurrences()) {
      assertThat(occurrence.occurrenceSign()).isOne();
      assertThat(occurrence.indictedObjects()).hasSize(2);
      assertThat(occurrence.indictedObjects().get(0)).isSameAs(occurrence.indictedObjects().get(1));
      assertThat(raw.indictmentMap().get(occurrence.indictedObjects().getFirst()))
          .singleElement()
          .isSameAs(occurrence);
      assertThat(occurrence.originalIndictedObjects()).hasSize(1);
      assertThat(raw.originalIndictmentMap().get(occurrence.originalIndictedObjects().getFirst()))
          .singleElement()
          .isSameAs(occurrence);
    }
    assertThat(Constraints.mappingCalls).hasValue(2);
  }

  @Test
  void terminalOnlyRawAnalysisDoesNotClaimOriginals() {
    var analysis =
        manager(Constraints.class)
            .analyzeWithOptions(
                TestdataSolution.generateSolution(2, 1),
                new ScoreAnalysisOptions(
                    ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL, true));
    assertThat(analysis.rawAnalysis().originalsFetched()).isFalse();
    assertThat(analysis.rawAnalysis().originalIndictmentMap()).isNull();
    assertThat(analysis.rawAnalysis().matchOccurrences())
        .singleElement()
        .satisfies(match -> assertThat(match.originalIndictedObjects()).isNull());
  }

  @Test
  void explicitUpdatePolicyRetainsFreshScoreValidation() {
    var manager = manager(Constraints.class);
    var solution = TestdataSolution.generateSolution(2, 1);
    manager.update(solution);
    assertThat(
            manager.analyzeWithOptions(solution, RAW_BOTH, SolutionUpdatePolicy.NO_UPDATE).score())
        .isEqualTo(SimpleScore.of(-2));
    solution.setScore(SimpleScore.ZERO);
    assertThatThrownBy(
            () -> manager.analyzeWithOptions(solution, RAW_BOTH, SolutionUpdatePolicy.NO_UPDATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("do not match");
  }

  @Test
  void recommendationsUseRawPolicyBeforeAndAfterEachTemporaryAssignment() {
    var manager = manager(Constraints.class);
    var solution = TestdataSolution.generateSolution(3, 2);
    var entity = solution.getEntityList().getFirst();
    entity.setValue(null);
    var untouchedValue = solution.getEntityList().getLast().getValue();
    var recommendations =
        manager.recommendAssignmentWithOptions(
            solution, entity, TestdataEntity::getValue, RAW_BOTH);
    assertThat(recommendations).hasSize(3);
    assertThat(entity.getValue()).isNull();
    assertThat(solution.getEntityList().getLast().getValue()).isSameAs(untouchedValue);
    for (var recommendation : recommendations) {
      var diff = recommendation.scoreAnalysisDiff();
      assertThat(diff.score()).isEqualTo(SimpleScore.of(-2));
      assertThat(diff.rawAnalysis().matchOccurrences())
          .singleElement()
          .satisfies(
              match -> {
                assertThat(match.occurrenceSign()).isOne();
                assertThat(match.matchScore()).isEqualTo(SimpleScore.of(-2));
                assertThat(match.indictedObjects())
                    .containsExactly(
                        new EntityKey(entity.getCode()), new EntityKey(entity.getCode()));
                assertThat(match.originalIndictedObjects())
                    .singleElement()
                    .satisfies(
                        original ->
                            assertThat(((TestdataEntity) original).getCode())
                                .isEqualTo(entity.getCode()));
              });
    }
  }

  @ParameterizedTest
  @CsvSource({"false, false", "true, false", "false, true", "true, true"})
  void rawAnalysisRetainsAutomaticSharingAndProfiling(boolean sharing, boolean profiling) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(SolutionManagerIndictmentTest.SharedConstraints.class);
    config.getScoreDirectorFactoryConfig().setConstraintStreamAutomaticNodeSharing(sharing);
    config.getScoreDirectorFactoryConfig().setConstraintStreamProfilingEnabled(profiling);
    var manager =
        SolutionManager.<TestdataSolution, SimpleScore>create(SolverFactory.create(config));
    var solution = TestdataSolution.generateSolution(3, 2);
    var analysis = manager.analyzeWithOptions(solution, RAW_BOTH);
    assertThat(analysis.score()).isEqualTo(SimpleScore.of(2));
    assertThat(analysis.rawAnalysis().matchOccurrences()).hasSize(4);
    for (var entity : solution.getEntityList()) {
      assertThat(analysis.rawAnalysis().originalIndictmentMap().get(entity))
          .hasSize(2)
          .allSatisfy(match -> assertThat(match.originalIndictedObjects()).containsExactly(entity));
    }
  }

  private static SolutionManager<TestdataSolution, SimpleScore> manager(
      Class<? extends ConstraintProvider> provider) {
    return SolutionManager.create(
        SolverFactory.<TestdataSolution>create(
            new SolverConfig()
                .withSolutionClass(TestdataSolution.class)
                .withEntityClasses(TestdataEntity.class)
                .withConstraintProviderClass(provider)));
  }

  public record EntityKey(String code) {}

  public record Reason(String message) implements ConstraintJustification {}

  public static class Constraints implements ConstraintProvider {
    static final AtomicInteger mappingCalls = new AtomicInteger();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .penalize(SimpleScore.ONE, entity -> entity.getCode().endsWith("0") ? 2 : 3)
            .justifyWith((entity, score) -> new Reason("assigned"))
            .indictWith(
                entity -> {
                  mappingCalls.incrementAndGet();
                  var key = new EntityKey(entity.getCode());
                  return List.of(key, key);
                })
            .asConstraint("assigned")
      };
    }
  }
}
