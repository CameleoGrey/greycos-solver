package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.MultistageIntegrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultistageBasicIntegrationTest {
  private static final Map<String, Observations> OBSERVATIONS = new ConcurrentHashMap<>();

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void dependentStagesHaveTheSameFixedWorkTraceAndNeverRunAgainDuringReplay(String workers) {
    var problem = basicProblem(2);
    var observations = new Observations(Behavior.DEPENDENT);
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers).withPhases(phase(3)));
      var trace = recordBasicSteps(solver);

      var result = solver.solve(problem);

      assertThat(trace)
          .containsExactly(
              new BasicStep(List.of("1", "1"), SimpleScore.of(2)),
              new BasicStep(List.of("2", "2"), SimpleScore.of(4)),
              new BasicStep(List.of("3", "3"), SimpleScore.of(6)));
      assertThat(observations.firstStages).hasValue(3);
      assertThat(observations.secondStages).hasValue(3);
      assertThat(observations.thirdStages).hasValue(3);
      assertBasicScore(result);
      assertThat(assignments(result)).containsExactly("3", "3");
      assertThat(assignments(problem)).containsExactly("0", "0");
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = LocalSearchType.class,
      names = {
        "HILL_CLIMBING",
        "TABU_SEARCH",
        "LATE_ACCEPTANCE",
        "SIMULATED_ANNEALING",
        "VARIABLE_NEIGHBORHOOD_DESCENT",
        "GUIDED_LOCAL_SEARCH"
      })
  void completedCandidateIsUsableByOrdinarySearchAlgorithms(LocalSearchType searchType) {
    var problem = basicProblem(2);
    var observations = new Observations(Behavior.DEPENDENT);
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var searchPhase = phase(3).withLocalSearchType(searchType);
      if (searchType == LocalSearchType.SIMULATED_ANNEALING) {
        searchPhase.setLocalSearchType(null);
        searchPhase.withAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.SIMULATED_ANNEALING))
                .withSimulatedAnnealingStartingTemperature("10"));
      }
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig("NONE").withPhases(searchPhase));
      var trace = recordBasicSteps(solver);
      var result = solver.solve(problem);
      assertBasicScore(result);
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(6));
      assertThat(trace).hasSize(3);
      assertThat(observations.firstStages).hasValue(3);
      assertThat(observations.secondStages).hasValue(3);
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void mandatoryUnassignmentCanBeRepairedByTheNextStage(String workers) {
    var problem = basicProblem(2);
    var observations = new Observations(Behavior.REPAIR);
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers).withPhases(phase(1)));
      var trace = recordBasicSteps(solver);
      var result = solver.solve(problem);
      assertThat(trace).containsExactly(new BasicStep(List.of("1", "1"), SimpleScore.of(2)));
      assertThat(observations.incompleteStates).hasValue(1);
      assertThat(observations.firstStages).hasValue(1);
      assertThat(observations.secondStages).hasValue(1);
      assertBasicScore(result);
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = Behavior.class,
      names = {"ABORT", "ROUND_TRIP", "SKIP", "UNREPAIRED", "BUDGET"})
  void discardedCandidatesRestoreTheWholeSolutionAndDoNotBecomeSteps(Behavior behavior) {
    for (var workers : List.of("NONE", "2")) {
      var problem = basicProblem(2);
      var observations = new Observations(behavior);
      OBSERVATIONS.put(problem.getCode(), observations);
      try {
        var selector = selector();
        if (behavior == Behavior.BUDGET) selector.withProbeCountLimit(1);
        var solver =
            MultistageIntegrationSupport.<TestdataSolution>solver(
                basicConfig(workers).withPhases(phase(1).withMoveSelectorConfig(selector)));
        var trace = recordBasicSteps(solver);
        var result = solver.solve(problem);
        assertThat(trace).isEmpty();
        assertThat(assignments(result)).containsExactly("0", "0");
        assertBasicScore(result);
        assertThat(observations.firstStages).hasValue(1);
        assertThat(observations.secondStages).hasValue(1);
        assertThat(observations.thirdStages)
            .hasValue(behavior == Behavior.ABORT || behavior == Behavior.BUDGET ? 0 : 1);
      } finally {
        OBSERVATIONS.remove(problem.getCode());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void independentIslandProvidersUseTheirOwnWorkingObjectsAndSupportSolverReuse(String workers) {
    var config =
        basicConfig("NONE")
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMoveThreadCount(workers)
                    .withCompareGlobalEnabled(false)
                    .withMigrationFrequency(Integer.MAX_VALUE)
                    .withPhaseConfigList(List.of(phase(3))));
    var solver = MultistageIntegrationSupport.<TestdataSolution>solver(config);
    for (int run = 0; run < 2; run++) {
      var problem = basicProblem(2);
      var observations = new Observations(Behavior.DEPENDENT);
      OBSERVATIONS.put(problem.getCode(), observations);
      try {
        var result = solver.solve(problem);
        assertThat(assignments(result)).containsExactly("3", "3");
        assertBasicScore(result);
        assertThat(observations.firstStages).hasValue(6);
        assertThat(observations.secondStages).hasValue(6);
      } finally {
        OBSERVATIONS.remove(problem.getCode());
      }
    }
  }

  @Test
  void partitionProvidersResolveObjectsInsideTheirOwnPartition() {
    var problem = basicProblem(4);
    var observations = new Observations(Behavior.DEPENDENT);
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig("NONE")
                  .withPhases(
                      new PartitionedSearchPhaseConfig()
                          .withSolutionPartitionerClass(PairPartitioner.class)
                          .withRunnablePartThreadLimit("1")
                          .withPhaseConfigs(phase(3))));
      var result = solver.solve(problem);
      assertThat(assignments(result)).containsExactly("3", "3", "3", "3");
      assertBasicScore(result);
      assertThat(observations.firstStages).hasValue(6);
      assertThat(observations.secondStages).hasValue(6);
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  static MultistageMoveSelectorConfig selector() {
    return new MultistageMoveSelectorConfig()
        .withStageProviderClass(DependentStages.class)
        .withEntityClass(TestdataEntity.class)
        .withVariableName("value")
        .withSelectionOrder(SelectionOrder.ORIGINAL)
        .withCandidateCountLimit(1);
  }

  static LocalSearchPhaseConfig phase(int steps) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(selector())
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private enum Behavior {
    DEPENDENT,
    REPAIR,
    ABORT,
    ROUND_TRIP,
    SKIP,
    UNREPAIRED,
    BUDGET
  }

  private static final class Observations {
    final Behavior behavior;
    final AtomicInteger firstStages = new AtomicInteger();
    final AtomicInteger secondStages = new AtomicInteger();
    final AtomicInteger thirdStages = new AtomicInteger();
    final AtomicInteger incompleteStates = new AtomicInteger();

    Observations(Behavior behavior) {
      this.behavior = behavior;
    }
  }

  public static final class DependentStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    private TestdataSolution solution;

    @Override
    public void initialize(TestdataSolution workingSolution) {
      solution = workingSolution;
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      assertThat(candidateIndex).isZero();
      return List.of(
          this::firstStage,
          this::secondStage,
          evaluator -> {
            assertThat(evaluator.workingSolution()).isSameAs(solution);
            observations().thirdStages.incrementAndGet();
            return MultistageStageResult.skip();
          });
    }

    private Observations observations() {
      return OBSERVATIONS.get(solution.getCode());
    }

    private MultistageStageResult<TestdataSolution> firstStage(
        BasicVariableMoveEvaluator<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>
            evaluator) {
      assertThat(evaluator.workingSolution()).isSameAs(solution);
      var observations = observations();
      observations.firstStages.incrementAndGet();
      var first = solution.getEntityList().getFirst();
      if (observations.behavior == Behavior.SKIP) return MultistageStageResult.skip();
      if (observations.behavior == Behavior.REPAIR
          || observations.behavior == Behavior.UNREPAIRED) {
        return MultistageStageResult.apply(evaluator.unassign(first));
      }
      int next = Integer.parseInt(first.getValue().getCode()) + 1;
      var operation = evaluator.assign(first, solution.getValueList().get(next));
      if (observations.behavior == Behavior.DEPENDENT) {
        var originalValue = first.getValue();
        var originalScore = solution.getScore();
        var evaluation = evaluator.evaluate(operation);
        assertThat(evaluation.isComplete()).isTrue();
        assertThat(evaluation.score()).isEqualTo(SimpleScore.of(2L * next - 1));
        assertThat(first.getValue()).isSameAs(originalValue);
        assertThat(solution.getScore()).isEqualTo(originalScore);
      }
      return MultistageStageResult.apply(operation);
    }

    private MultistageStageResult<TestdataSolution> secondStage(
        BasicVariableMoveEvaluator<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>
            evaluator) {
      assertThat(evaluator.workingSolution()).isSameAs(solution);
      var observations = observations();
      observations.secondStages.incrementAndGet();
      var first = solution.getEntityList().getFirst();
      var second = solution.getEntityList().getLast();
      return switch (observations.behavior) {
        case ABORT -> MultistageStageResult.abortCandidate();
        case SKIP, UNREPAIRED -> MultistageStageResult.skip();
        case ROUND_TRIP ->
            MultistageStageResult.apply(
                evaluator.assign(first, solution.getValueList().getFirst()));
        case REPAIR -> {
          assertThat(first.getValue()).isNull();
          var evaluation = evaluator.currentEvaluation();
          assertThat(evaluation.isComplete()).isFalse();
          assertThat(evaluation.unassignedCount()).isEqualTo(1);
          observations.incompleteStates.incrementAndGet();
          yield MultistageStageResult.apply(
              evaluator.sequence(
                  List.of(
                      evaluator.assign(first, solution.getValueList().get(1)),
                      evaluator.assign(second, solution.getValueList().get(1)))));
        }
        case BUDGET -> {
          evaluator.evaluate(evaluator.assign(second, solution.getValueList().get(1)));
          evaluator.evaluate(evaluator.assign(second, solution.getValueList().get(2)));
          throw new AssertionError("The second probe must exhaust the per-candidate budget.");
        }
        case DEPENDENT -> {
          assertThat(Integer.parseInt(first.getValue().getCode()))
              .isEqualTo(Integer.parseInt(second.getValue().getCode()) + 1);
          yield MultistageStageResult.apply(
              evaluator.assign(second, evaluator.currentValue(first)));
        }
      };
    }

    @Override
    public void phaseEnded() {
      solution = null;
    }
  }

  public static final class PairPartitioner implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      var solution = scoreDirector.getWorkingSolution();
      var partitions = new ArrayList<TestdataSolution>();
      for (int i = 0; i < solution.getEntityList().size(); i += 2) {
        var partition = new TestdataSolution(solution.getCode());
        partition.setValueList(new ArrayList<>(solution.getValueList()));
        partition.setEntityList(
            solution.getEntityList().subList(i, i + 2).stream()
                .map(entity -> new TestdataEntity(entity.getCode(), entity.getValue()))
                .toList());
        partitions.add(partition);
      }
      return partitions;
    }
  }
}
