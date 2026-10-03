package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.CUSTOMERS;
import static greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.QUANTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.multistage.PreparedMultistageMove;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Customer;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Plan;
import greycos.solver.core.impl.multistage.integration.CrossVariableVehicleTestSupport.Vehicle;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * GLS must reconsider a prepared move after changing penalties without spending its provider budget
 * again.
 */
@Timeout(30)
class MultistageGlsIntegrationTest {
  private static final Map<String, Observations> OBSERVATIONS = new ConcurrentHashMap<>();

  static Stream<Arguments> basicBudgets() {
    return Stream.of("NONE", "1", "2", "4")
        .flatMap(
            workers ->
                Stream.of(
                    Arguments.of(workers, null, null, 64),
                    Arguments.of(workers, 1, null, 1),
                    Arguments.of(workers, 1, 1, 1)));
  }

  @ParameterizedTest
  @MethodSource("basicBudgets")
  void basicPlateauEscapeMatchesOrdinaryChangeWithoutRepreparingCandidates(
      String workers, Integer candidateLimit, Integer sampleSize, int expectedCandidates) {
    var ordinary = basicTrace(new ChangeMoveSelectorConfig(), workers, sampleSize);
    assertThat(ordinary).containsExactly("B");

    var problem = basicProblem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var selector = basicSelector();
      if (candidateLimit != null) selector.withCandidateCountLimit(candidateLimit);
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers)
                  .withPhases(phase(selector, BasicFeatures.class, 0, sampleSize, 1)));
      var trace = recordBasicTrace(solver);

      var result = solver.solve(problem);

      assertThat(trace).containsExactlyElementsOf(ordinary);
      assertThat(observations.candidates).hasValue(expectedCandidates);
      assertThat(observations.stages).hasValue(expectedCandidates);
      assertThat(observations.probes).hasValue(expectedCandidates);
      assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
      assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(1);
      assertThat(decider(solver).getStatistics().decisionRounds()).isEqualTo(2);
      assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
      assertThat(problem.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void preparedCandidatesAreDiscardedBetweenStepsAndSolverRuns(String workers) {
    var solver =
        MultistageIntegrationSupport.<TestdataSolution>solver(
            basicConfig(workers)
                .withPhases(
                    phase(
                        basicSelector().withCandidateCountLimit(1), BasicFeatures.class, 0, 1, 2)));
    var trace = recordBasicTrace(solver);
    for (int run = 0; run < 2; run++) {
      var problem = basicProblem();
      var observations = new Observations();
      OBSERVATIONS.put(problem.getCode(), observations);
      try {
        trace.clear();
        var result = solver.solve(problem);
        assertThat(trace).containsExactly("B", "A");
        assertThat(observations.candidates).hasValue(2);
        assertThat(observations.stages).hasValue(2);
        assertThat(observations.probes).hasValue(2);
        assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
        assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
      } finally {
        OBSERVATIONS.remove(problem.getCode());
      }
    }
  }

  static Stream<Arguments> discardedCandidates() {
    return Stream.of("NONE", "2")
        .flatMap(
            workers ->
                Stream.of(CandidateBehavior.values())
                    .map(behavior -> Arguments.of(workers, behavior)));
  }

  @ParameterizedTest
  @MethodSource("discardedCandidates")
  void abortedIncompleteUnchangedAndInitiallyFilteredCandidatesAreNeverReplayed(
      String workers, CandidateBehavior behavior) {
    var problem = basicProblem();
    var observations = new Observations();
    observations.candidateBehavior = behavior;
    if (behavior == CandidateBehavior.COMPLETE)
      observations.filterBehavior = FilterBehavior.REJECT_ALL;
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var selector = basicSelector().withCandidateCountLimit(1);
      if (behavior == CandidateBehavior.COMPLETE) selector.withFilterClass(ReplayFilter.class);
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers).withPhases(phase(selector, BasicFeatures.class, 0, 1, 1)));
      var trace = recordBasicTrace(solver);

      var result = solver.solve(problem);

      assertThat(trace).isEmpty();
      assertThat(observations.candidates).hasValue(1);
      assertThat(observations.stages).hasValue(1);
      assertThat(observations.filters).hasValue(behavior == CandidateBehavior.COMPLETE ? 1 : 0);
      assertThat(decider(solver).getControllerDiagnostics().attemptedCandidates()).isEqualTo(1);
      assertThat(decider(solver).getStatistics().penaltyUpdates()).isZero();
      assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
      assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
      assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void retainedScoringConsumesTheScoreCalculationTerminationBudget(String workers) {
    var problem = basicProblem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var phase =
          phase(basicSelector().withCandidateCountLimit(1), ConstantFeatures.class, 0, 1, 1)
              .withTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(12L));
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers).withPhases(phase));
      var trace = recordBasicTrace(solver);
      var decision = new AtomicReference<LocalSearchStepScope<TestdataSolution>>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
              decision.set((LocalSearchStepScope<TestdataSolution>) scope);
            }
          });

      var result = solver.solve(problem);

      assertThat(trace).isEmpty();
      assertThat(observations.candidates).hasValue(1);
      assertThat(observations.probes).hasValue(1);
      assertThat(decider(solver).getControllerDiagnostics().attemptedCandidates())
          .isBetween(2L, 12L);
      assertThat(decider(solver).getStatistics().penaltyUpdates()).isBetween(1L, 12L);
      assertThat(decision.get().getNoStepReason())
          .isEqualTo(LocalSearchStepScope.NoStepReason.TERMINATED);
      assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
      assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void adoptingPendingStateResetsProviderSessionsAndDiscardsEarlierCandidates(String workers) {
    var problem = basicProblem();
    var observations = new Observations();
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers)
                  .withPhases(
                      phase(
                          basicSelector().withCandidateCountLimit(1),
                          BasicFeatures.class,
                          0,
                          1,
                          3)));
      var trace = recordBasicTrace(solver);
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              if (scope.getStepIndex() != 0) return;
              var working = scope.getWorkingSolution();
              var descriptor =
                  scope
                      .getScoreDirector()
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(TestdataEntity.class)
                      .getGenuineVariableDescriptor("value");
              scope
                  .getPhaseScope()
                  .getSolverScope()
                  .setPendingMove(
                      new ChangeMove<>(
                          descriptor,
                          working.getEntityList().getFirst(),
                          working.getValueList().getFirst()),
                      true);
            }
          });

      var result = solver.solve(problem);

      assertThat(trace).containsExactly("B", "A", "B");
      assertThat(observations.candidates).hasValue(2);
      assertThat(observations.stages).hasValue(2);
      assertThat(observations.probes).hasValue(2);
      assertThat(observations.initialized.get()).isGreaterThanOrEqualTo(2);
      assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
      assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
      assertThat(problem.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  static Stream<Arguments> replayFilters() {
    return Stream.of("NONE", "2")
        .flatMap(
            workers ->
                Stream.of(
                        FilterBehavior.REJECT_REPLAY,
                        FilterBehavior.FAIL_REPLAY,
                        FilterBehavior.TERMINATE_REPLAY)
                    .map(behavior -> Arguments.of(workers, behavior)));
  }

  @ParameterizedTest
  @MethodSource("replayFilters")
  void retainedCandidatesRunCoordinatorFiltersAgainAndCleanupAllowsSolverReuse(
      String workers, FilterBehavior behavior) {
    var solver =
        MultistageIntegrationSupport.<TestdataSolution>solver(
            basicConfig(workers)
                .withPhases(
                    phase(
                        basicSelector()
                            .withCandidateCountLimit(1)
                            .withFilterClass(ReplayFilter.class),
                        BasicFeatures.class,
                        0,
                        1,
                        1)));
    var trace = recordBasicTrace(solver);
    for (int run = 0; run < 2; run++) {
      var problem = basicProblem();
      var observations = new Observations();
      observations.filterBehavior = run == 0 ? behavior : FilterBehavior.ACCEPT;
      observations.terminate = solver::terminateEarly;
      OBSERVATIONS.put(problem.getCode(), observations);
      try {
        trace.clear();
        if (run == 0 && behavior == FilterBehavior.FAIL_REPLAY) {
          assertThatThrownBy(() -> solver.solve(problem))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Retained candidate filter failure");
        } else {
          var result = solver.solve(problem);
          assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
        }
        if (run == 0) assertThat(trace).isEmpty();
        else assertThat(trace).containsExactly("B");
        assertThat(observations.filters.get()).isGreaterThanOrEqualTo(2);
        assertThat(observations.candidates).hasValue(1);
        assertThat(observations.stages).hasValue(1);
        assertThat(observations.closed.get()).isEqualTo(observations.initialized.get());
        assertThat(problem.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
      } finally {
        OBSERVATIONS.remove(problem.getCode());
      }
    }
  }

  static Stream<Arguments> vehicleSelectors() {
    return Stream.of("NONE", "1", "2", "4")
        .flatMap(
            workers ->
                Stream.of(
                    Arguments.of(workers, false, false),
                    Arguments.of(workers, true, false),
                    Arguments.of(workers, true, true)));
  }

  @ParameterizedTest
  @MethodSource("vehicleSelectors")
  void listAndCrossVariablePlateausReplayWithIndependentScoresAndAllListShadows(
      String workers, boolean cross, boolean union) {
    var problem = CrossVariableVehicleTestSupport.problem();
    // Equal unit costs make the coordinated quantity/list move a genuine business-score plateau.
    problem.vehicles.forEach(vehicle -> vehicle.unitCost = 1);
    var original = CrossVariableVehicleTestSupport.assignments(problem);
    var observations = new Observations();
    OBSERVATIONS.put(problem.id, observations);
    try {
      MoveSelectorConfig<?> selector =
          cross
              ? new CrossVariableMultistageMoveSelectorConfig()
                  .withVariables(QUANTITY, CUSTOMERS)
                  .withStageProviderClass(CrossStages.class)
                  .withCandidateCountLimit(1)
              : new ListMultistageMoveSelectorConfig()
                  .withEntityClass(Vehicle.class)
                  .withVariableName("customers")
                  .withStageProviderClass(ListStages.class)
                  .withCandidateCountLimit(1);
      if (union) {
        // The finite multistage child is exhausted before GLS retries; the ordinary child keeps
        // producing moves.
        selector =
            new UnionMoveSelectorConfig()
                .withSelectionOrder(SelectionOrder.ORIGINAL)
                .withMoveSelectors(
                    selector,
                    new ChangeMoveSelectorConfig()
                        .withSelectionOrder(SelectionOrder.RANDOM)
                        .withEntitySelectorConfig(new EntitySelectorConfig(Vehicle.class))
                        .withValueSelectorConfig(new ValueSelectorConfig("quantity")));
      }
      var config =
          CrossVariableVehicleTestSupport.config(CrossStages.class, workers)
              .withPhases(phase(selector, VehicleFeatures.class, 1, union ? 4 : 1, 1));
      var solver = MultistageIntegrationSupport.<Plan>solver(config);
      var trace = new ArrayList<List<String>>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<Plan> scope) {
              var solution = scope.getWorkingSolution();
              assertThat(scope.getScore().isFullyAssigned()).isTrue();
              assertThat(scope.getScore().raw())
                  .isEqualTo(CrossVariableVehicleTestSupport.independentScore(solution));
              CrossVariableVehicleTestSupport.verify(solution);
              trace.add(CrossVariableVehicleTestSupport.assignments(solution));
              if (cross) {
                assertThat(solution.vehicles.getFirst().quantity).isEqualTo(1100);
                assertThat(solution.vehicles.getLast().quantity).isEqualTo(500);
                assertThat(solution.customers.getFirst().vehicle)
                    .isSameAs(solution.vehicles.getLast());
              } else {
                assertThat(solution.vehicles.getFirst().customers)
                    .extracting(customer -> customer.id)
                    .containsExactly("source-resident", "c", "b", "a");
              }
            }
          });

      var result = solver.solve(problem);

      assertThat(trace).hasSize(1);
      assertThat(trace.getFirst()).isNotEqualTo(original);
      assertThat(observations.candidates).hasValue(1);
      assertThat(observations.stages).hasValue(cross ? 3 : 1);
      assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(1);
      CrossVariableVehicleTestSupport.verify(result);
      assertThat(CrossVariableVehicleTestSupport.assignments(problem)).isEqualTo(original);
      CrossVariableVehicleTestSupport.verifyAssignments(problem);
    } finally {
      OBSERVATIONS.remove(problem.id);
    }
  }

  private static List<String> basicTrace(
      MoveSelectorConfig<?> selector, String workers, Integer sampleSize) {
    var solver =
        MultistageIntegrationSupport.<TestdataSolution>solver(
            basicConfig(workers)
                .withPhases(phase(selector, BasicFeatures.class, 0, sampleSize, 1)));
    var trace = recordBasicTrace(solver);
    var result = solver.solve(basicProblem());
    assertThat(result.getScore()).isEqualTo(independentBasicScore(result));
    return trace;
  }

  private static TestdataSolution basicProblem() {
    var a = new TestdataValue("A");
    var solution = new TestdataSolution(UUID.randomUUID().toString());
    solution.setValueList(List.of(a, new TestdataValue("B")));
    solution.setEntityList(List.of(new TestdataEntity("entity", a)));
    return solution;
  }

  private static SolverConfig basicConfig(String workers) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(BasicScore.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(7L);
  }

  private static MultistageMoveSelectorConfig basicSelector() {
    return new MultistageMoveSelectorConfig()
        .withEntityClass(TestdataEntity.class)
        .withVariableName("value")
        .withStageProviderClass(BasicStages.class);
  }

  private static LocalSearchPhaseConfig phase(
      MoveSelectorConfig<?> selector,
      Class<? extends GuidedLocalSearchFeatureProvider> features,
      int targetLevel,
      Integer sampleSize,
      int steps) {
    var guidance =
        new GuidedLocalSearchConfig()
            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
            .withTargetScoreLevelIndex(targetLevel)
            .withPenaltyFactor(BigDecimal.ONE)
            .withFeatureProviderClass(features);
    if (sampleSize != null) guidance.withSampleSize(sampleSize);
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
        .withGuidedLocalSearchConfig(guidance)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static List<String> recordBasicTrace(DefaultSolver<TestdataSolution> solver) {
    var trace = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var solution = scope.getWorkingSolution();
            assertThat(scope.getScore().isFullyAssigned()).isTrue();
            assertThat(scope.getScore().raw()).isEqualTo(independentBasicScore(solution));
            assertThat(solution.getValueList())
                .contains(solution.getEntityList().getFirst().getValue());
            trace.add(solution.getEntityList().getFirst().getValue().getCode());
          }
        });
    return trace;
  }

  private static SimpleScore independentBasicScore(TestdataSolution solution) {
    long assigned =
        solution.getEntityList().stream().filter(entity -> entity.getValue() != null).count();
    return SimpleScore.of(-assigned);
  }

  @SuppressWarnings("unchecked")
  private static <S> GuidedLocalSearchDecider<S> decider(DefaultSolver<S> solver) {
    return (GuidedLocalSearchDecider<S>)
        ((DefaultLocalSearchPhase<S>) solver.getPhaseList().getFirst()).getDecider();
  }

  private enum FilterBehavior {
    ACCEPT,
    REJECT_ALL,
    REJECT_REPLAY,
    FAIL_REPLAY,
    TERMINATE_REPLAY
  }

  private enum CandidateBehavior {
    COMPLETE,
    ABORT,
    UNCHANGED,
    INCOMPLETE
  }

  private static final class Observations {
    final Thread coordinator = Thread.currentThread();
    final AtomicInteger candidates = new AtomicInteger();
    final AtomicInteger stages = new AtomicInteger();
    final AtomicInteger probes = new AtomicInteger();
    final AtomicInteger filters = new AtomicInteger();
    final AtomicInteger initialized = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    FilterBehavior filterBehavior = FilterBehavior.ACCEPT;
    CandidateBehavior candidateBehavior = CandidateBehavior.COMPLETE;
    Runnable terminate;
  }

  public static final class BasicScore
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          -solution.getEntityList().stream().filter(entity -> entity.getValue() != null).count());
    }
  }

  public static final class BasicStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    private TestdataSolution solution;

    @Override
    public void initialize(TestdataSolution solution) {
      this.solution = solution;
      OBSERVATIONS.get(solution.getCode()).initialized.incrementAndGet();
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      var observations = OBSERVATIONS.get(solution.getCode());
      observations.candidates.incrementAndGet();
      return List.of(
          evaluator -> {
            observations.stages.incrementAndGet();
            assertThat(evaluator.workingSolution()).isSameAs(solution);
            var entity = solution.getEntityList().getFirst();
            if (observations.candidateBehavior == CandidateBehavior.ABORT)
              return MultistageStageResult.abortCandidate();
            if (observations.candidateBehavior == CandidateBehavior.UNCHANGED)
              return MultistageStageResult.apply(evaluator.assign(entity, entity.getValue()));
            if (observations.candidateBehavior == CandidateBehavior.INCOMPLETE)
              return MultistageStageResult.apply(evaluator.unassign(entity));
            var next = solution.getValueList().get(entity.getValue().getCode().equals("A") ? 1 : 0);
            var operation = evaluator.assign(entity, next);
            assertThat(evaluator.evaluate(operation).score()).isEqualTo(SimpleScore.of(-1));
            observations.probes.incrementAndGet();
            return MultistageStageResult.apply(operation);
          });
    }

    @Override
    public void phaseEnded() {
      OBSERVATIONS.get(solution.getCode()).closed.incrementAndGet();
      solution = null;
    }
  }

  public static final class ReplayFilter
      implements SelectionFilter<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public boolean accept(ScoreDirector<TestdataSolution> director, Move<TestdataSolution> move) {
      var solution = director.getWorkingSolution();
      var observations = OBSERVATIONS.get(solution.getCode());
      assertThat(Thread.currentThread()).isSameAs(observations.coordinator);
      assertThat(move).isInstanceOf(PreparedMultistageMove.class);
      assertThat(solution.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
      assertThat(move.getPlanningEntities().getFirst())
          .isSameAs(solution.getEntityList().getFirst());
      assertThat(move.getPlanningValues().getFirst()).isSameAs(solution.getValueList().get(1));
      var inspection = observations.filters.incrementAndGet();
      if (observations.filterBehavior == FilterBehavior.REJECT_ALL) return false;
      if (inspection == 1) return true;
      return switch (observations.filterBehavior) {
        case ACCEPT -> true;
        case REJECT_ALL, REJECT_REPLAY -> false;
        case FAIL_REPLAY -> throw new IllegalStateException("Retained candidate filter failure");
        case TERMINATE_REPLAY -> {
          observations.terminate.run();
          yield false;
        }
      };
    }
  }

  public static final class ListStages
      implements ListVariableStageProvider<Plan, Vehicle, Customer, HardSoftScore> {
    private Plan solution;

    @Override
    public void initialize(Plan solution) {
      this.solution = solution;
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<ListVariableCustomStage<Plan, Vehicle, Customer, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var observations = OBSERVATIONS.get(solution.id);
      observations.candidates.incrementAndGet();
      return List.of(
          evaluator -> {
            observations.stages.incrementAndGet();
            return MultistageStageResult.apply(
                evaluator.reverse(solution.vehicles.getFirst(), 0, 4));
          });
    }
  }

  public static final class CrossStages implements CrossVariableStageProvider<Plan, HardSoftScore> {
    private Plan solution;

    @Override
    public void initialize(Plan solution) {
      this.solution = solution;
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var observations = OBSERVATIONS.get(solution.id);
      observations.candidates.incrementAndGet();
      var source = solution.vehicles.getFirst();
      var target = solution.vehicles.getLast();
      var customer = source.customers.getFirst();
      return List.of(
          evaluator -> {
            observations.stages.incrementAndGet();
            return MultistageStageResult.apply(
                evaluator.basic(QUANTITY).assign(source, source.quantity - customer.demand));
          },
          evaluator -> {
            observations.stages.incrementAndGet();
            return MultistageStageResult.apply(
                evaluator.basic(QUANTITY).assign(target, target.quantity + customer.demand));
          },
          evaluator -> {
            observations.stages.incrementAndGet();
            return MultistageStageResult.apply(
                evaluator.list(CUSTOMERS).place(customer, target, target.customers.size()));
          });
    }
  }

  public static final class BasicFeatures extends CurrentFeature<TestdataSolution> {
    public BasicFeatures() {
      super(solution -> solution.getEntityList().getFirst().getValue().getCode());
    }
  }

  public static final class VehicleFeatures extends CurrentFeature<Plan> {
    public VehicleFeatures() {
      super(solution -> solution.vehicles.getFirst().customers.getFirst().id);
    }
  }

  public static final class ConstantFeatures extends CurrentFeature<TestdataSolution> {
    public ConstantFeatures() {
      super(solution -> "constant");
    }
  }

  /**
   * The tiny model has one feature; rescanning it independently also checks rollback notifications.
   */
  public abstract static class CurrentFeature<S>
      implements GuidedLocalSearchFeatureProvider<S, String> {
    private final Function<S, String> key;

    protected CurrentFeature(Function<S, String> key) {
      this.key = key;
    }

    @Override
    public void extractFeatures(S solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      consumer.accept(key.apply(solution), 1L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<S, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private S solution;
        private String previous;

        @Override
        public void resetWorkingSolution(S solution) {
          this.solution = solution;
          previous = null;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          var current = key.apply(solution);
          if (!current.equals(previous)) {
            if (previous != null) updater.remove(previous);
            updater.accept(current, 1L);
            previous = current;
          }
        }
      };
    }
  }
}
