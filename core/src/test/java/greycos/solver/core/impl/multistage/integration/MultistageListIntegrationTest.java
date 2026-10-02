package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.MultistageIntegrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultistageListIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void probesRestoreTemporaryUnassignmentAndFollowingStagesSeeUpdatedListShadows(String workers) {
    var config = listConfig(workers).withPhases(phase(RepairStages.class));
    var solver = MultistageIntegrationSupport.<TestdataListSolution>solver(config);
    var steps = new java.util.ArrayList<SimpleScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataListSolution> scope) {
            assertThat(scope.getScore().isFullyAssigned()).isTrue();
            assertListScoreAndShadows(scope.getWorkingSolution());
            steps.add((SimpleScore) scope.getScore().raw());
          }
        });
    var problem = listProblem();

    var result = solver.solve(problem);

    assertThat(steps).containsExactly(SimpleScore.of(3));
    assertThat(result.getEntityList().getFirst().getValueList())
        .extracting(TestdataListValue::getCode)
        .containsExactly("2");
    assertThat(result.getEntityList().getLast().getValueList())
        .extracting(TestdataListValue::getCode)
        .containsExactly("0", "1");
    assertListScoreAndShadows(result);
    assertThat(problem.getEntityList().getFirst().getValueList())
        .extracting(TestdataListValue::getCode)
        .containsExactly("2", "1", "0");
    assertThat(problem.getEntityList().getLast().getValueList()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = LocalSearchType.class,
      names = {
        "HILL_CLIMBING",
        "TABU_SEARCH",
        "VARIABLE_NEIGHBORHOOD_DESCENT",
        "GUIDED_LOCAL_SEARCH"
      })
  void completedListCandidateWorksWithOrdinarySearchAlgorithms(LocalSearchType type) {
    var result =
        MultistageIntegrationSupport.<TestdataListSolution>solver(
                listConfig("NONE").withPhases(phase(RepairStages.class).withLocalSearchType(type)))
            .solve(listProblem());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(3));
    assertListScoreAndShadows(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void abortAfterListMutationRollsBackMembershipOrderAndAllShadows(String workers) {
    var result =
        MultistageIntegrationSupport.<TestdataListSolution>solver(
                listConfig(workers).withPhases(phase(AbortStages.class)))
            .solve(listProblem());
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(result.getEntityList().getFirst().getValueList())
        .extracting(TestdataListValue::getCode)
        .containsExactly("2", "1", "0");
    assertThat(result.getEntityList().getLast().getValueList()).isEmpty();
    assertListScoreAndShadows(result);
  }

  private static LocalSearchPhaseConfig phase(Class<? extends ListVariableStageProvider> provider) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(
            new ListMultistageMoveSelectorConfig()
                .withStageProviderClass(provider)
                .withEntityClass(TestdataListEntity.class)
                .withVariableName("valueList")
                .withSelectionOrder(SelectionOrder.ORIGINAL)
                .withCandidateCountLimit(1))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  public static final class RepairStages
      implements ListVariableStageProvider<
          TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            ListVariableCustomStage<
                TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.unassign(evaluator.workingSolution().getValueList().getFirst())),
          evaluator -> {
            var solution = evaluator.workingSolution();
            var source = solution.getEntityList().getFirst();
            var target = solution.getEntityList().getLast();
            var value = solution.getValueList().getFirst();
            assertThat(value.getEntity()).isNull();
            assertThat(value.getIndex()).isNull();
            assertThat(evaluator.position(value).isUnassigned()).isTrue();
            var baseline = evaluator.currentEvaluation();
            assertThat(baseline.unassignedCount()).isEqualTo(1);
            assertThat(baseline.isComplete()).isFalse();
            var originalStoredScore = solution.getScore();
            var alternatives =
                List.of(evaluator.place(value, source, 0), evaluator.place(value, target, 0));
            var evaluations = evaluator.evaluateAll(alternatives);
            assertThat(evaluations)
                .allSatisfy(evaluation -> assertThat(evaluation.isComplete()).isTrue());
            assertThat(evaluations.getFirst().score()).isEqualTo(SimpleScore.ZERO);
            assertThat(evaluations.getLast().score()).isEqualTo(SimpleScore.ONE);
            assertThat(value.getEntity()).isNull();
            assertThat(value.getIndex()).isNull();
            assertThat(target.getValueList()).isEmpty();
            assertThat(source.getValueList())
                .extracting(TestdataListValue::getCode)
                .containsExactly("2", "1");
            assertThat(solution.getScore()).isEqualTo(originalStoredScore);
            return evaluator.bestFit(alternatives);
          },
          evaluator -> {
            var solution = evaluator.workingSolution();
            var target = solution.getEntityList().getLast();
            var repairedValue = solution.getValueList().getFirst();
            assertThat(repairedValue.getEntity()).isSameAs(target);
            assertThat(repairedValue.getIndex()).isZero();
            var nextValue = solution.getEntityList().getFirst().getValueList().getLast();
            assertThat(nextValue.getCode()).isEqualTo("1");
            return MultistageStageResult.apply(
                evaluator.place(nextValue, target, target.getValueList().size()));
          });
    }
  }

  public static final class AbortStages
      implements ListVariableStageProvider<
          TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            ListVariableCustomStage<
                TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.reverse(evaluator.workingSolution().getEntityList().getFirst(), 0, 3)),
          evaluator -> {
            var solution = evaluator.workingSolution();
            assertThat(solution.getEntityList().getFirst().getValueList())
                .extracting(TestdataListValue::getCode)
                .containsExactly("0", "1", "2");
            assertThat(solution.getValueList().getFirst().getIndex()).isZero();
            return MultistageStageResult.apply(
                evaluator.unassign(solution.getValueList().getFirst()));
          },
          evaluator -> {
            assertThat(evaluator.workingSolution().getValueList().getFirst().getEntity()).isNull();
            return MultistageStageResult.abortCandidate();
          },
          evaluator -> {
            throw new AssertionError("An aborted candidate must not invoke remaining stages.");
          });
    }
  }
}
