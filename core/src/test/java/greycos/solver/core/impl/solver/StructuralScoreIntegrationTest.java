package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.variable.InconsistentSolutionException;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolutionUpdatePolicy;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencyConstraintProvider;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencyEntity;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencySolution;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencyValue;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldEntity;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldSolution;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StructuralScoreIntegrationTest {

  private static SolverFactory<TestdataDependencyNoInconsistentFieldSolution> solverFactory() {
    return SolverFactory.create(
        new SolverConfig()
            .withSolutionClass(TestdataDependencyNoInconsistentFieldSolution.class)
            .withEntityClasses(
                TestdataDependencyNoInconsistentFieldEntity.class,
                TestdataDependencyNoInconsistentFieldValue.class)
            .withConstraintProviderClass(EntityCountConstraintProvider.class)
            .withPhases(new ConstructionHeuristicPhaseConfig()));
  }

  private static TestdataDependencyNoInconsistentFieldSolution solution(boolean cyclic) {
    var first = new TestdataDependencyNoInconsistentFieldValue("first");
    var second =
        new TestdataDependencyNoInconsistentFieldValue(
            "second", Duration.ofHours(1), List.of(first));
    var entity = new TestdataDependencyNoInconsistentFieldEntity("entity");
    entity.setValues(new ArrayList<>(cyclic ? List.of(second, first) : List.of(first, second)));
    return new TestdataDependencyNoInconsistentFieldSolution(
        List.of(entity), List.of(first, second));
  }

  @Test
  void nativeAnalysisPreservesStructuralStateAndBusinessScore() {
    var manager =
        SolutionManager.<TestdataDependencyNoInconsistentFieldSolution, HardSoftScore>create(
            solverFactory());
    var problem = solution(true);

    var analysis = manager.analyze(problem, ScoreAnalysisFetchPolicy.FETCH_ALL);

    assertThat(analysis.isSolutionStructurallyFlawed()).isTrue();
    assertThat(analysis.score()).isEqualTo(new HardSoftScore(-1, 0, -1));
    assertThat(problem.getScore()).isEqualTo(analysis.score());
    assertThat(analysis.structuralFlawAnalysis().getVariableLoops()).hasSize(1);
    assertThat(analysis.structuralFlawAnalysis().getVariableLoops().getFirst().entitySet())
        .containsExactlyInAnyOrderElementsOf(problem.getValues());
    assertThat(analysis.summarize()).contains("Structural flaws", "startTime", "endTime");
    assertThatThrownBy(() -> manager.update(problem))
        .isInstanceOf(InconsistentSolutionException.class);
  }

  @ParameterizedTest
  @EnumSource(SolutionUpdatePolicy.class)
  void legacyAnalysisPreservesBusinessScoreAndVariableLoops(SolutionUpdatePolicy updatePolicy) {
    var factory =
        SolverFactory.<TestdataDependencySolution>create(
            new SolverConfig()
                .withSolutionClass(TestdataDependencySolution.class)
                .withEntityClasses(TestdataDependencyEntity.class, TestdataDependencyValue.class)
                .withConstraintProviderClass(TestdataDependencyConstraintProvider.class)
                .withPhases(new ConstructionHeuristicPhaseConfig()));
    var manager = SolutionManager.<TestdataDependencySolution, HardSoftScore>create(factory);
    var first = new TestdataDependencyValue("first", Duration.ofHours(1));
    var second = new TestdataDependencyValue("second", Duration.ofHours(1), List.of(first));
    var entity = new TestdataDependencyEntity();
    entity.setValues(new ArrayList<>(List.of(second, first)));
    var problem = new TestdataDependencySolution(List.of(entity), List.of(first, second));
    var expectedScore = HardSoftScore.ofHard(-2);
    assertThat(manager.update(problem)).isEqualTo(expectedScore);

    var analysis = manager.analyze(problem, ScoreAnalysisFetchPolicy.FETCH_ALL, updatePolicy);

    assertThat(analysis.score()).isEqualTo(expectedScore);
    assertThat(problem.getScore()).isEqualTo(expectedScore);
    assertThat(analysis.structuralFlawAnalysis().getVariableLoops()).hasSize(1);
    assertThat(analysis.structuralFlawAnalysis().getVariableLoops().getFirst().entitySet())
        .containsExactlyInAnyOrder(first, second);
    assertThat(problem.getValues()).allSatisfy(value -> assertThat(value.getIsInvalid()).isTrue());
    assertThat(manager.update(problem)).isEqualTo(expectedScore);
  }

  @Test
  void nativeRecommendationsOmitCyclicAssignmentsAndPreserveInput() {
    var manager =
        SolutionManager.<TestdataDependencyNoInconsistentFieldSolution, HardSoftScore>create(
            solverFactory());
    var problem = solution(false);
    manager.update(problem);
    var first = problem.getValues().getFirst();
    var second = problem.getValues().getLast();
    var originalScore = problem.getScore();

    var recommendations =
        manager.recommendAssignment(
            problem,
            second,
            value ->
                value.getPreviousValue() == null
                    ? "first"
                    : "after " + value.getPreviousValue().getId());

    assertThat(recommendations).hasSize(1);
    assertThat(recommendations.getFirst().proposition()).isEqualTo("after first");
    assertThat(recommendations.getFirst().scoreAnalysisDiff().isSolutionStructurallyFlawed())
        .isFalse();
    assertThat(problem.getEntities().getFirst().getValues()).containsExactly(first, second);
    assertThat(problem.getScore()).isEqualTo(originalScore);
    assertThat(second.getPreviousValue()).isSameAs(first);
  }

  @Test
  void initialCycleRecoveryPublishesTheRecoveredClone() {
    var factory =
        (DefaultSolverFactory<TestdataDependencyNoInconsistentFieldSolution>) solverFactory();
    var problem = solution(true);
    var scope = new SolverScope<TestdataDependencyNoInconsistentFieldSolution>();
    try (var director = factory.getScoreDirectorFactory().buildScoreDirector()) {
      scope.setScoreDirector(director);
      scope.setBestSolution(problem);
      scope.setWorkingSolutionFromBestSolution();

      new BestSolutionRecaller<TestdataDependencyNoInconsistentFieldSolution>()
          .solvingStarted(scope);

      assertThat(scope.getBestSolution())
          .isNotSameAs(problem)
          .isNotSameAs(director.getWorkingSolution());
      assertThat(scope.getBestSolution().getEntities().getFirst().getValues()).isEmpty();
      assertThat(scope.getBestScore().isStructurallyFlawed()).isFalse();
      assertThat(problem.getEntities().getFirst().getValues()).hasSize(2);
    }
  }

  @Test
  void recallerRejectsCyclicPublicationWithoutLosingTheChangedProblem() {
    var factory =
        (DefaultSolverFactory<TestdataDependencyNoInconsistentFieldSolution>) solverFactory();
    var priorBest = solution(false);
    var priorScore = InnerScore.fullyAssigned(HardSoftScore.ofSoft(-1));
    priorBest.setScore(priorScore.raw());
    var changedProblem = solution(true);
    var scope = new SolverScope<TestdataDependencyNoInconsistentFieldSolution>();
    var events = mock(SolverEventSupport.class);
    var recaller = new BestSolutionRecaller<TestdataDependencyNoInconsistentFieldSolution>();
    recaller.setSolverEventSupport(events);
    try (var director = factory.getScoreDirectorFactory().buildScoreDirector()) {
      scope.setScoreDirector(director);
      scope.setBestSolution(priorBest);
      scope.setBestScore(priorScore);
      director.setWorkingSolution(changedProblem);
      assertThat(director.calculateScore().isStructurallyFlawed()).isTrue();

      assertThatThrownBy(
              () ->
                  recaller.updateBestSolutionAndFireIfInitialized(
                      scope, EventProducerId.problemChange()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot publish a structurally flawed solution");

      assertThat(scope.getBestSolution()).isSameAs(priorBest);
      assertThat(scope.getBestScore()).isEqualTo(priorScore);
      assertThat(director.getWorkingSolution()).isSameAs(changedProblem);
      assertThat(changedProblem.getEntities().getFirst().getValues())
          .containsExactly(
              changedProblem.getValues().getLast(), changedProblem.getValues().getFirst());
      verifyNoInteractions(events);
    }
  }

  public static final class EntityCountConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataDependencyNoInconsistentFieldEntity.class)
            .penalize(HardSoftScore.ONE_SOFT)
            .asConstraint("Entity count")
      };
    }
  }
}
