package greycos.solver.core.impl.phase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(20)
@Execution(ExecutionMode.SAME_THREAD)
class ResumablePhaseExecutionTest {

  @ParameterizedTest
  @EnumSource(
      value = LocalSearchType.class,
      names = {
        "LATE_ACCEPTANCE",
        "DIVERSIFIED_LATE_ACCEPTANCE",
        "HILL_CLIMBING",
        "GREAT_DELUGE",
        "TABU_SEARCH",
        "VARIABLE_NEIGHBORHOOD_DESCENT"
      })
  void localSearchRetainsTrajectoryAcrossQuanta(LocalSearchType type) {
    var config = basicConfig().withPhases(localSearchConfig(type));
    if (type == LocalSearchType.DIVERSIFIED_LATE_ACCEPTANCE) {
      config.withPreviewFeature(PreviewFeature.DIVERSIFIED_LATE_ACCEPTANCE);
    }
    assertLocalSearchParity(config);
  }

  @ParameterizedTest
  @EnumSource(
      value = AcceptorType.class,
      names = {"SIMULATED_ANNEALING", "STEP_COUNTING_HILL_CLIMBING"})
  void configuredAcceptorRetainsCoolingAndHistory(AcceptorType type) {
    var acceptor = new LocalSearchAcceptorConfig().withAcceptorTypeList(List.of(type));
    if (type == AcceptorType.SIMULATED_ANNEALING) {
      acceptor.withSimulatedAnnealingStartingTemperature("10");
    } else {
      acceptor.withStepCountingHillClimbingSize(3);
    }
    assertLocalSearchParity(
        basicConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withAcceptorConfig(acceptor)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(9))));
  }

  @Test
  void compositeAcceptorAndForagerRetainTheirOrderedState() {
    var acceptor =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(List.of(AcceptorType.LATE_ACCEPTANCE, AcceptorType.ENTITY_TABU))
            .withLateAcceptanceSize(3)
            .withEntityTabuSize(2);
    assertLocalSearchParity(
        basicConfig()
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withAcceptorConfig(acceptor)
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(3))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(9))));
  }

  private static void assertLocalSearchParity(SolverConfig config) {
    var uninterrupted = run(config, TestdataSolution.generateSolution(3, 8), List.of(), BASIC);
    for (var grants : List.of(List.of(1L), List.of(2L, 1L, 4L), List.of(100L))) {
      assertThat(run(config, TestdataSolution.generateSolution(3, 8), grants, BASIC))
          .isEqualTo(uninterrupted);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = ConstructionHeuristicType.class,
      names = {"FIRST_FIT", "ALLOCATE_FROM_POOL"})
  void constructionRetainsPlacementAndTerminationAcrossQuanta(ConstructionHeuristicType type) {
    var config =
        basicConfig()
            .withPhases(new ConstructionHeuristicPhaseConfig().withConstructionHeuristicType(type));
    var uninterrupted =
        run(config, TestdataSolution.generateUninitializedSolution(3, 8), List.of(), BASIC);
    for (var grants : List.of(List.of(1L), List.of(3L, 2L), List.of(100L))) {
      assertThat(run(config, TestdataSolution.generateUninitializedSolution(3, 8), grants, BASIC))
          .isEqualTo(uninterrupted);
    }
  }

  @Test
  void listConstructionRetainsStepBoundAndShadows() {
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(new ConstructionHeuristicPhaseConfig());
    Function<TestdataListSolution, List<String>> assignments =
        solution ->
            solution.getEntityList().stream()
                .map(
                    entity ->
                        entity.getCode()
                            + ":"
                            + entity.getValueList().stream()
                                .map(TestdataListValue::getCode)
                                .toList())
                .toList();
    assertThat(
            run(
                config,
                TestdataListSolution.generateUninitializedSolution(7, 2),
                List.of(1L),
                assignments))
        .isEqualTo(
            run(
                config,
                TestdataListSolution.generateUninitializedSolution(7, 2),
                List.of(),
                assignments));
  }

  @Test
  void ownerAndGrantChecksDoNotAlterResidentState() throws InterruptedException {
    var solver =
        start(
            basicConfig().withPhases(localSearchConfig(LocalSearchType.LATE_ACCEPTANCE)),
            TestdataSolution.generateSolution(3, 8));
    var scope = solver.getSolverScope();
    var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    try {
      var execution = phase.startExecution(scope);
      assertThatThrownBy(() -> execution.advance(0L)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> phase.startExecution(scope))
          .hasMessageContaining("active execution");
      var wrongOwnerFailure = new AtomicReference<Throwable>();
      var other =
          new Thread(() -> wrongOwnerFailure.set(catchThrowable(() -> execution.advance(1L))));
      other.start();
      other.join();
      assertThat(wrongOwnerFailure.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("belongs to thread");
      assertThat(execution.advance(1L)).isOne();
      assertThat(execution.getPhaseScope().getNextStepIndex()).isEqualTo(1);
      execution.finish();
      execution.finish();
      assertThat(execution.advance(1L)).isZero();
    } finally {
      solver.solvingEnded(scope);
      solver.outerSolvingEnded(scope);
    }
  }

  @Test
  void failingCallbackDoesNotEndOrResumeThePhase() {
    var solver =
        start(
            basicConfig().withPhases(localSearchConfig(LocalSearchType.LATE_ACCEPTANCE)),
            TestdataSolution.generateSolution(3, 8));
    var scope = solver.getSolverScope();
    var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var events = new ArrayList<String>();
    var original = new IllegalStateException("Step callback failed.");
    phase.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
            throw original;
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            events.add("ended");
          }
        });
    var execution = phase.startExecution(scope);
    assertThat(catchThrowable(() -> execution.advance(1L))).isSameAs(original);
    assertThatThrownBy(() -> execution.advance(1L)).hasMessageContaining("failed");
    assertThatThrownBy(execution::finish).hasMessageContaining("failed");
    assertThat(events).isEmpty();
    solver.solvingError(scope, original);
    assertThat(scope.getScoreDirector().getWorkingSolution()).isNull();
  }

  @Test
  void noSearchSpaceDoesNotStartOrEndPhase() {
    var config = basicConfig().withPhases(localSearchConfig(LocalSearchType.LATE_ACCEPTANCE));
    var result = run(config, TestdataSolution.generateSolution(1, 1), List.of(1L), BASIC);
    assertThat(result.events()).isEmpty();
  }

  @Test
  void decisionWithoutMoveDoesNotCompleteAStep() {
    var phase =
        localSearchConfig(LocalSearchType.LATE_ACCEPTANCE)
            .withMoveSelectorConfig(
                new ChangeMoveSelectorConfig()
                    .withSelectionOrder(SelectionOrder.ORIGINAL)
                    .withFilterClass(RejectAllMoves.class));
    var config = basicConfig().withPhases(phase);
    var result = run(config, TestdataSolution.generateSolution(3, 8), List.of(1L), BASIC);
    assertThat(result.events()).containsExactly("phaseStarted", "stepStarted:0", "phaseEnded:0");
    assertThat(result)
        .isEqualTo(run(config, TestdataSolution.generateSolution(3, 8), List.of(), BASIC));
  }

  @Test
  void constructionEarlyTerminationMetadataIsIndependentOfQuanta() {
    var config =
        basicConfig()
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    assertThat(
            run(config, TestdataSolution.generateUninitializedSolution(3, 8), List.of(1L), BASIC))
        .isEqualTo(
            run(config, TestdataSolution.generateUninitializedSolution(3, 8), List.of(), BASIC));
  }

  public static final class RejectAllMoves implements SelectionFilter<TestdataSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataSolution> scoreDirector, Object selection) {
      return false;
    }
  }

  private static final Function<TestdataSolution, List<String>> BASIC =
      solution ->
          solution.getEntityList().stream()
              .map(
                  entity ->
                      entity.getCode()
                          + ":"
                          + (entity.getValue() == null ? "null" : entity.getValue().getCode()))
              .toList();

  private static SolverConfig basicConfig() {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(37L);
  }

  private static LocalSearchPhaseConfig localSearchConfig(LocalSearchType type) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(type)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(9));
  }

  private static <Solution_> DefaultSolver<Solution_> start(
      SolverConfig config, Solution_ problem) {
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    var scope = solver.getSolverScope();
    scope.setBestSolution(problem);
    solver.outerSolvingStarted(scope);
    solver.solvingStarted(scope);
    return solver;
  }

  private static <Solution_> RunResult run(
      SolverConfig config,
      Solution_ problem,
      List<Long> grants,
      Function<Solution_, List<String>> assignments) {
    var solver = start(config, problem);
    var scope = solver.getSolverScope();
    var phase = solver.getPhaseList().getFirst();
    var events = new ArrayList<String>();
    phase.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
            events.add("phaseStarted");
          }

          @Override
          public void stepStarted(AbstractStepScope<Solution_> stepScope) {
            events.add("stepStarted:" + stepScope.getStepIndex());
          }

          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            events.add(
                "stepEnded:"
                    + stepScope.getStepIndex()
                    + ":"
                    + stepScope.getScore()
                    + ":"
                    + assignments.apply(stepScope.getWorkingSolution()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
            events.add("phaseEnded:" + phaseScope.getNextStepIndex());
          }
        });
    try {
      if (grants.isEmpty()) {
        phase.solve(scope);
      } else {
        var director = scope.getScoreDirector();
        var working = scope.getWorkingSolution();
        var execution =
            phase instanceof DefaultLocalSearchPhase<Solution_> local
                ? local.startExecution(scope)
                : ((DefaultConstructionHeuristicPhase<Solution_>) phase).startExecution(scope);
        int index = 0;
        while (!execution.isComplete()) {
          long grant = grants.get(index++ % grants.size());
          int before = execution.getPhaseScope().getNextStepIndex();
          long completed = execution.advance(grant);
          assertThat(completed).isBetween(0L, grant);
          assertThat((long) execution.getPhaseScope().getNextStepIndex() - before)
              .isEqualTo(completed);
          assertThat(scope.getScoreDirector()).isSameAs(director);
          assertThat(scope.getWorkingSolution()).isSameAs(working);
          assertThat(index).isLessThan(100);
        }
        execution.finish();
      }
      var random = scope.getWorkingRandom();
      return new RunResult(
          List.copyOf(events),
          assignments.apply(scope.getBestSolution()),
          scope.getBestScore().toString(),
          scope.getMoveEvaluationCount(),
          scope.getScoreCalculationCount(),
          List.of(
              random.moveIteratorUsage().nextLong(),
              random.factoryUsage().nextLong(),
              random.acceptorUsage().nextLong()),
          phase instanceof DefaultConstructionHeuristicPhase<Solution_> construction
              ? construction.getTerminationStatus().toString()
              : "localSearch");
    } catch (RuntimeException | Error failure) {
      solver.solvingError(scope, failure);
      throw failure;
    } finally {
      if (scope.getScoreDirector().getWorkingSolution() != null) {
        solver.solvingEnded(scope);
        solver.outerSolvingEnded(scope);
      }
    }
  }

  private record RunResult(
      List<String> events,
      List<String> bestAssignments,
      String bestScore,
      long evaluations,
      long scoreCalculations,
      List<Long> nextRandomValues,
      String termination) {}
}
