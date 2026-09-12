package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class AlnsIntegrationTest {
  private static AlnsPhaseConfig alns() {
    return new AlnsPhaseConfig()
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(4));
  }

  private static SolverConfig config() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withPhases(new ConstructionHeuristicPhaseConfig(), alns());
  }

  @Test
  void participatesInNormalPhaseLifecycleAndCanPrecedeLocalSearch() {
    var config =
        config()
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                alns(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var phaseIds = new ArrayList<EventProducerId>();
    var trialIndices = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            phaseIds.add(phaseScope.getPhaseId());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            if (stepScope instanceof AlnsStepScope<TestdataSolution> alnsStepScope) {
              trialIndices.add(stepScope.getStepIndex());
              assertThat(alnsStepScope.getTrialResult()).isNotNull();
              assertThat(alnsStepScope.getScore().isFullyAssigned()).isTrue();
            }
          }
        });
    var solution = solver.solve(TestdataSolution.generateUninitializedSolution(3, 6));
    assertThat(solution.getScore()).isNotNull();
    assertThat(phaseIds)
        .containsExactly(
            EventProducerId.constructionHeuristic(0),
            EventProducerId.alns(1),
            EventProducerId.localSearch(2));
    assertThat(trialIndices).containsExactly(0, 1, 2, 3);
  }

  @Test
  void solverManagerDeliversFirstInitializedSolutionBeforeAlns() throws Exception {
    var event = new AtomicReference<FirstInitializedSolutionEvent<TestdataSolution>>();
    try (var manager = SolverManager.<TestdataSolution>create(config())) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("alns")
              .withProblemFinder(id -> TestdataSolution.generateUninitializedSolution(3, 6))
              .withFirstInitializedSolutionEventConsumer(event::set)
              .run();
      assertThat(job.getFinalBestSolution().getScore()).isNotNull();
      assertThat(event.get()).isNotNull();
      assertThat(event.get().producerId()).isEqualTo(EventProducerId.constructionHeuristic(0));
      assertThat(event.get().isTerminatedEarly()).isFalse();
    }
  }

  @Test
  void runsAsExplicitInnerPhaseInTwoIslands() {
    var config =
        config()
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(1)
                    .withReceiveGlobalUpdateFrequency(1)
                    .withPhaseConfigList(List.of(alns())));
    var solution =
        PlannerTestUtils.solve(config, TestdataSolution.generateUninitializedSolution(3, 6));
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(solution.getScore()).isNotNull();
  }

  @Test
  void solverManagerInitializesBeforeIslandContainingAlns() throws Exception {
    var config =
        config()
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withPhaseConfigList(List.of(alns())));
    var event = new AtomicReference<FirstInitializedSolutionEvent<TestdataSolution>>();
    try (var manager = SolverManager.<TestdataSolution>create(config)) {
      var job =
          manager
              .solveBuilder()
              .withProblemId("island-alns")
              .withProblemFinder(id -> TestdataSolution.generateUninitializedSolution(3, 6))
              .withFirstInitializedSolutionEventConsumer(event::set)
              .run();
      assertThat(job.getFinalBestSolution().getScore()).isNotNull();
      assertThat(event.get()).isNotNull();
      assertThat(event.get().producerId()).isEqualTo(EventProducerId.constructionHeuristic(0));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listModelKeepsEveryValueAndShadowConsistent(boolean islands) {
    var phase =
        islands
            ? new IslandModelPhaseConfig()
                .withIslandCount(2)
                .withMigrationFrequency(1)
                .withReceiveGlobalUpdateFrequency(1)
                .withPhaseConfigList(List.of(alns()))
            : alns();
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class)
            .withPhases(new ConstructionHeuristicPhaseConfig(), phase);
    var result =
        PlannerTestUtils.solve(config, TestdataListSolution.generateUninitializedSolution(8, 3));
    assertThat(
            result.getEntityList().stream()
                .flatMap(entity -> entity.getValueList().stream())
                .toList())
        .containsExactlyInAnyOrderElementsOf(result.getValueList());
    result
        .getEntityList()
        .forEach(
            entity -> {
              for (int i = 0; i < entity.getValueList().size(); i++) {
                assertThat(entity.getValueList().get(i).getEntity()).isSameAs(entity);
                assertThat(entity.getValueList().get(i).getIndex()).isEqualTo(i);
              }
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedModelPreservesAllBindingsAndDeclarativeShadows(boolean islands) {
    var phase =
        islands
            ? new IslandModelPhaseConfig()
                .withIslandCount(2)
                .withMigrationFrequency(1)
                .withReceiveGlobalUpdateFrequency(1)
                .withPhaseConfigList(List.of(alns()))
            : alns();
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataMixedSolution.class,
                TestdataMixedEntity.class,
                TestdataMixedValue.class,
                TestdataMixedOtherValue.class)
            .withEasyScoreCalculatorClass(TestdataMixedEasyScoreCalculator.class)
            .withPhases(phase);
    var problem = TestdataMixedSolution.generateUninitializedSolution(3, 8, 3);
    for (int i = 0; i < problem.getEntityList().size(); i++) {
      var entity = problem.getEntityList().get(i);
      entity.setBasicValue(problem.getOtherValueList().get(i));
      entity.setSecondBasicValue(problem.getOtherValueList().get((i + 1) % 3));
    }
    for (int i = 0; i < problem.getValueList().size(); i++) {
      problem.getEntityList().get(i % 3).getValueList().add(problem.getValueList().get(i));
    }
    SolutionManager.updateShadowVariables(problem);
    var result = PlannerTestUtils.solve(config, problem);
    assertThat(
            result.getEntityList().stream()
                .flatMap(entity -> entity.getValueList().stream())
                .toList())
        .containsExactlyInAnyOrderElementsOf(result.getValueList());
    assertThat(result.getEntityList())
        .allSatisfy(
            entity -> {
              assertThat(entity.getBasicValue()).isNotNull();
              assertThat(entity.getSecondBasicValue()).isNotNull();
              assertThat(entity.getDeclarativeShadowVariableValue())
                  .isEqualTo(entity.getBasicValue().getStrength());
            });
  }

  @Test
  void islandsOwnSeparatePoliciesAndExecuteEachPolicyOnOneThread() {
    TrackingSelection.instances.clear();
    var config =
        config()
            .withMoveThreadCount("2")
            .withPhases(
                new ConstructionHeuristicPhaseConfig().withMoveThreadCount("NONE"),
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMigrationFrequency(1)
                    .withPhaseConfigList(
                        List.of(alns().withSelectionPolicyClass(TrackingSelection.class))));
    PlannerTestUtils.solve(config, TestdataSolution.generateUninitializedSolution(3, 6));
    assertThat(TrackingSelection.instances)
        .hasSize(2)
        .allSatisfy(
            policy -> {
              assertThat(policy.threads).hasSize(1);
              assertThat(policy.updates.get()).isEqualTo(4);
              assertThat(policy.closed).isTrue();
            });
    assertThat(TrackingSelection.instances.get(0).threads)
        .doesNotContainAnyElementsOf(TrackingSelection.instances.get(1).threads);
  }

  public static final class TrackingSelection
      implements AlnsSelectionPolicy<SimpleScore>, AutoCloseable {
    static final List<TrackingSelection> instances = new CopyOnWriteArrayList<>();
    final Set<Thread> threads = ConcurrentHashMap.newKeySet();
    final AtomicInteger updates = new AtomicInteger();
    boolean closed;

    public TrackingSelection() {
      instances.add(this);
    }

    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> pairs, RandomGenerator random) {
      threads.add(Thread.currentThread());
      return pairs.getFirst();
    }

    @Override
    public void update(AlnsTrialResult<SimpleScore> result) {
      threads.add(Thread.currentThread());
      updates.incrementAndGet();
    }

    @Override
    public Map<String, Double> weights() {
      return Map.of("custom", updates.doubleValue());
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
