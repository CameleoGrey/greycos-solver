package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SolutionManagerIndictmentTest {

  @ParameterizedTest
  @CsvSource({"false, false", "true, false", "false, true", "true, true"})
  void attributionSurvivesAutomaticSharingAndSerialProfiling(boolean sharing, boolean profiling) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(SharedConstraints.class);
    config.getScoreDirectorFactoryConfig().setConstraintStreamAutomaticNodeSharing(sharing);
    config.getScoreDirectorFactoryConfig().setConstraintStreamProfilingEnabled(profiling);
    var manager =
        SolutionManager.<TestdataSolution, SimpleScore>create(SolverFactory.create(config));
    var problem = TestdataSolution.generateSolution(3, 2);
    var analysis =
        manager.analyzeWithIndictments(problem, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    assertThat(analysis.score()).isEqualTo(SimpleScore.of(2));
    assertThat(analysis.indictmentMap()).hasSize(2);
    assertThat(analysis.originalIndictmentMap()).hasSize(2);
    for (var entity : problem.getEntityList()) {
      var terminal = analysis.indictmentMap().get(new EntityKey(entity.getCode()));
      var original = analysis.originalIndictmentMap().get(entity);
      assertThat(terminal.score()).isEqualTo(SimpleScore.ONE);
      assertThat(terminal.matchCount()).isEqualTo(2L);
      assertThat(original.score()).isEqualTo(SimpleScore.ONE);
      assertThat(original.matchCount()).isEqualTo(2L);
      assertThat(original.indictedObject()).isSameAs(entity);
    }
  }

  @Test
  void existingAnalysisDoesNotEvaluateIndictmentMapping() {
    var problem = TestdataSolution.generateSolution(3, 2);
    var manager = manager();
    Constraints.mappingCalls.set(0);
    var existing = manager.analyze(problem);
    assertThat(existing.score()).isEqualTo(SimpleScore.of(-2));
    assertThat(existing.indictmentMap()).isNull();
    assertThat(existing.originalIndictmentMap()).isNull();
    assertThat(Constraints.mappingCalls).hasValue(0);
    var explicitNone =
        manager.analyze(
            problem,
            ScoreAnalysisFetchPolicy.FETCH_ALL,
            SolutionUpdatePolicy.UPDATE_ALL,
            IndictmentFetchPolicy.NONE);
    assertThat(explicitNone).isEqualTo(existing);
    assertThat(Constraints.mappingCalls).hasValue(0);

    var terminal = manager.analyzeWithIndictments(problem, IndictmentFetchPolicy.TERMINAL);
    assertThat(terminal.indictmentMap()).hasSize(2);
    assertThat(terminal.originalIndictmentMap()).isNull();
    assertThat(Constraints.mappingCalls).hasValue(2);
    var both = manager.analyzeWithIndictments(problem, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    assertThat(both.indictmentMap()).hasSize(2);
    assertThat(both.originalIndictmentMap()).hasSize(2);
    for (var entity : problem.getEntityList()) {
      var original = both.originalIndictmentMap().get(entity);
      assertThat(original.indictedObject()).isSameAs(entity);
      assertThat(original.score()).isEqualTo(SimpleScore.of(-1));
      assertThat(original.matchCount()).isEqualTo(1L);
      assertThat(both.indictmentMap().get(new EntityKey(entity.getCode())).score())
          .isEqualTo(original.score());
    }
  }

  @Test
  void indictmentModesRequireFullMatchAnalysis() {
    var manager = manager();
    var problem = TestdataSolution.generateSolution(3, 2);
    for (var fetch :
        List.of(
            ScoreAnalysisFetchPolicy.FETCH_SHALLOW, ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT)) {
      assertThatThrownBy(
              () ->
                  manager.analyze(
                      problem,
                      fetch,
                      SolutionUpdatePolicy.UPDATE_ALL,
                      IndictmentFetchPolicy.TERMINAL))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("FETCH_ALL");
    }
  }

  @Test
  void recommendationDiffsRetainAttributionAfterRestoringAssignments() {
    var manager = manager();
    var problem = TestdataSolution.generateSolution(3, 2);
    var fitted = problem.getEntityList().getFirst();
    fitted.setValue(null);
    var unchanged = problem.getEntityList().getLast().getValue();
    var recommendations =
        manager.recommendAssignment(
            problem,
            fitted,
            TestdataEntity::getValue,
            ScoreAnalysisFetchPolicy.FETCH_ALL,
            IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    assertThat(recommendations).hasSize(3);
    assertThat(fitted.getValue()).isNull();
    assertThat(problem.getEntityList().getLast().getValue()).isSameAs(unchanged);
    for (var recommendation : recommendations) {
      var diff = recommendation.scoreAnalysisDiff();
      assertThat(diff.score()).isEqualTo(SimpleScore.of(-1));
      assertThat(diff.indictmentMap()).hasSize(1);
      assertThat(diff.indictmentMap().get(new EntityKey(fitted.getCode())).matchCount())
          .isEqualTo(1L);
      assertThat(diff.originalIndictmentMap()).hasSize(1);
      var original = diff.originalIndictmentMap().firstEntry().getValue();
      assertThat(original.indictedObject())
          .isInstanceOfSatisfying(
              TestdataEntity.class,
              entity -> assertThat(entity.getCode()).isEqualTo(fitted.getCode()));
      assertThat(original.score()).isEqualTo(SimpleScore.of(-1));
      assertThat(original.matchCount()).isEqualTo(1L);
    }
  }

  private static SolutionManager<TestdataSolution, SimpleScore> manager() {
    var factory =
        SolverFactory.<TestdataSolution>create(
            new SolverConfig()
                .withSolutionClass(TestdataSolution.class)
                .withEntityClasses(TestdataEntity.class)
                .withConstraintProviderClass(Constraints.class));
    return SolutionManager.create(factory);
  }

  public record EntityKey(String code) {}

  public static class SharedConstraints implements ConstraintProvider {

    public static EntityKey key(TestdataEntity entity) {
      return new EntityKey(entity.getCode());
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .map(SharedConstraints::key)
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .map(SharedConstraints::key)
            .reward(SimpleScore.of(2))
            .asConstraint("second")
      };
    }
  }

  public static final class Constraints implements ConstraintProvider {
    static final AtomicInteger mappingCalls = new AtomicInteger();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .penalize(SimpleScore.ONE)
            .indictWith(
                entity -> {
                  mappingCalls.incrementAndGet();
                  return List.of(new EntityKey(entity.getCode()));
                })
            .asConstraint("assigned")
      };
    }
  }
}
