package greycos.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldConstraintProvider;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldEntity;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldSolution;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GuidedLocalSearchIntegrationTest {

  static Stream<Arguments> calculatorsAndWorkers() {
    return Stream.of(Backend.values())
        .flatMap(
            backend ->
                Stream.of("NONE", "1", "2", "4").map(workers -> Arguments.of(backend, workers)));
  }

  @ParameterizedTest
  @MethodSource("calculatorsAndWorkers")
  void escapesStrictLocalOptimumWithoutChangingBusinessScores(Backend backend, String workers) {
    var solver = solver(config(backend, workers, 1, 2));
    var trace = recordSteps(solver);
    var publishedScores = recordAndVerifyBestSolutions(solver);
    var problem = problem(false);
    // AA is a strict local optimum under single-variable changes: either mixed state costs 5.
    assertThat(new LandscapeEasyScore().calculateScore(problem))
        .isEqualTo(HardSoftScore.ofSoft(-2));

    var result = solver.solve(problem);

    assertThat(trace)
        .containsExactly(
            new Step(List.of("B", "A"), HardSoftScore.ofSoft(-5)),
            new Step(List.of("B", "B"), HardSoftScore.ZERO));
    assertThat(statistics(solver).penaltyUpdates()).isEqualTo(1);
    assertBestIsUnpenalized(result);
    assertThat(publishedScores)
        .contains(HardSoftScore.ZERO)
        .allSatisfy(score -> assertThat(score).isGreaterThanOrEqualTo(HardSoftScore.ofSoft(-2)));
    // The best solution is a planning clone, and trial moves did not leak into the input.
    assertThat(problem.entities).extracting(entity -> entity.choice.id).containsExactly("A", "A");
  }

  @ParameterizedTest
  @MethodSource("calculatorsAndWorkers")
  void constantPositiveFeatureCannotForceAnOriginalScoreRegression(
      Backend backend, String workers) {
    var config = config(backend, workers, 1, 2);
    ((LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst())
        .getGuidedLocalSearchConfig()
        .withFeatureProviderClass(ConstantFeatures.class);
    var solver = solver(config);
    var trace = recordSteps(solver);
    var attempt = new AtomicReference<LocalSearchStepScope<LandscapeSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<LandscapeSolution> scope) {
            attempt.set((LocalSearchStepScope<LandscapeSolution>) scope);
          }
        });

    var result = solver.solve(problem(false));

    assertThat(trace).isEmpty();
    assertThat(attempt.get().getStep()).isNull();
    assertThat(attempt.get().getAcceptedMoveCount()).isZero();
    assertThat(attempt.get().getSelectedMoveCount()).isEqualTo(130L);
    assertThat(attempt.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.GUIDED_RETRY_EXHAUSTED);
    assertThat(statistics(solver)).isEqualTo(new GuidedLocalSearchDecider.Statistics(65, 64, 0, 0));
    assertThat(result.entities).extracting(entity -> entity.choice.id).containsExactly("A", "A");
    assertThat(result.score)
        .isEqualTo(HardSoftScore.ofSoft(-2))
        .isEqualTo(new LandscapeEasyScore().calculateScore(result));
  }

  @ParameterizedTest
  @MethodSource("calculatorsAndWorkers")
  void hardTargetCanCrossInfeasibilityWhileSoftTargetProtectsHardScore(
      Backend backend, String workers) {
    var hardSolver = solver(config(backend, workers, 0, 2));
    var hardTrace = recordSteps(hardSolver);
    recordAndVerifyBestSolutions(hardSolver);

    var hardResult = hardSolver.solve(problem(true));

    assertThat(hardTrace)
        .containsExactly(
            new Step(List.of("B", "A"), HardSoftScore.of(-1, -5)),
            new Step(List.of("B", "B"), HardSoftScore.ZERO));
    assertBestIsUnpenalized(hardResult);

    var softSolver = solver(config(backend, workers, 1, 2));
    var softTrace = recordSteps(softSolver);
    var softResult = softSolver.solve(problem(true));

    assertThat(softTrace).isEmpty();
    assertThat(softResult.entities)
        .extracting(entity -> entity.choice.id)
        .containsExactly("A", "A");
    assertThat(softResult.score).isEqualTo(HardSoftScore.ofSoft(-2));
    assertThat(softResult.score).isEqualTo(new LandscapeEasyScore().calculateScore(softResult));
    assertThat(statistics(softSolver).penaltyUpdates()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void reusedSolverStartsWithFreshPenaltyHistory(String workers) {
    var solver = solver(config(Backend.INCREMENTAL, workers, 1, 2));
    var trace = recordSteps(solver);
    var first = solver.solve(problem(false));
    var firstTrace = List.copyOf(trace);
    var firstStatistics = statistics(solver);
    trace.clear();

    var second = solver.solve(problem(false));

    assertBestIsUnpenalized(first);
    assertBestIsUnpenalized(second);
    assertThat(trace).containsExactlyElementsOf(firstTrace);
    assertThat(statistics(solver)).isEqualTo(firstStatistics);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void resettingPenaltiesOnNewBestReducesRetriesWithoutChangingTheBestScore(String workers) {
    var histories = new ArrayList<List<Step>>();
    var penaltyUpdates = new ArrayList<Long>();
    for (boolean reset : List.of(false, true)) {
      var config = config(Backend.INCREMENTAL, workers, 1, 4);
      ((LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst())
          .getGuidedLocalSearchConfig()
          .withResetPenaltiesOnNewBest(reset);
      var solver = solver(config);
      histories.add(recordSteps(solver));
      var problem = problem(false);
      problem.choices.getLast().featureCost = 10L;

      assertBestIsUnpenalized(solver.solve(problem));
      penaltyUpdates.add(statistics(solver).penaltyUpdates());
    }

    assertThat(histories.getFirst())
        .containsExactly(
            new Step(List.of("B", "A"), HardSoftScore.ofSoft(-5)),
            new Step(List.of("B", "B"), HardSoftScore.ZERO),
            new Step(List.of("A", "B"), HardSoftScore.ofSoft(-5)),
            new Step(List.of("A", "A"), HardSoftScore.ofSoft(-2)));
    assertThat(histories.getLast()).containsExactlyElementsOf(histories.getFirst());
    // Retaining the left/A penalty requires two left/B increments before leaving BB;
    // clearing that history requires just one. Every committed move still improves guidance.
    assertThat(penaltyUpdates).containsExactly(3L, 2L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void problemChangeRebuildsFeaturesFromChangedCostsAndAssignments(String workers) {
    var solver = solver(config(Backend.INCREMENTAL, workers, 1, 2));
    var trace = recordSteps(solver);
    var queued = new AtomicBoolean();
    recordAndVerifyBestSolutions(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<LandscapeSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    var a = solution.choices.getFirst();
                    director.changeProblemProperty(a, choice -> choice.featureCost = 100L);
                    for (var entity : solution.entities) {
                      director.changeVariable(
                          entity, "choice", assignment -> assignment.choice = a);
                    }
                  });
            }
          }
        });

    var result = solver.solve(problem(false));

    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(result.choices.getFirst().featureCost).isEqualTo(100L);
    assertThat(trace).hasSize(4);
    assertThat(trace.subList(2, 4)).containsExactlyElementsOf(trace.subList(0, 2));
    assertBestIsUnpenalized(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2", "4"})
  void pendingMigrationResetPreservesNextCandidateEvaluation(String workers) {
    var solver = solver(config(Backend.BAVET, workers, 1, 3));
    var trace = recordSteps(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<LandscapeSolution> scope) {
            if (scope.getStepIndex() == 0) {
              var working = scope.getWorkingSolution();
              var descriptor =
                  scope
                      .getScoreDirector()
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(Assignment.class)
                      .getGenuineVariableDescriptor("choice");
              // Islands use this pending-move path to publish better assignments requiring a reset.
              var move =
                  new ChangeMove<>(
                      descriptor, working.entities.getLast(), working.choices.getLast());
              scope
                  .getPhaseScope()
                  .getSolverScope()
                  .setPendingMoveIfBetter(move, InnerScore.fullyAssigned(HardSoftScore.ZERO), true);
            }
          }
        });

    var result = solver.solve(problem(false));

    assertThat(trace)
        .containsExactly(
            new Step(List.of("B", "A"), HardSoftScore.ofSoft(-5)),
            new Step(List.of("B", "B"), HardSoftScore.ZERO),
            new Step(List.of("A", "B"), HardSoftScore.ofSoft(-5)));
    assertThat(statistics(solver).penaltyUpdates()).isEqualTo(3);
    assertBestIsUnpenalized(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void islandShorthandBuildsIndependentGlsSearches(String workers) {
    var config = config(Backend.BAVET, "NONE", 1, 2);
    config.withPhases(
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount(workers)
            .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
            .withGuidedLocalSearchConfig(guidance(1))
            .withMoveSelectorConfig(
                new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL))
            .withMigrationFrequency(1)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(4)));
    var solver = solver(config);
    recordAndVerifyBestSolutions(solver);

    assertBestIsUnpenalized(solver.solve(problem(false)));
    assertBestIsUnpenalized(solver.solve(problem(false)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void guidedMovesPreservePinnedAssignments(String workers) {
    var solver = solver(config(Backend.EASY, workers, 1, 4));
    var trace = recordSteps(solver);
    var problem = problem(false);
    problem.entities.getFirst().pinned = true;

    var result = solver.solve(problem);

    assertThat(trace)
        .hasSize(4)
        .allSatisfy(step -> assertThat(step.choices().getFirst()).isEqualTo("A"));
    assertThat(result.entities).extracting(entity -> entity.choice.id).containsExactly("A", "A");
    assertThat(result.score).isEqualTo(HardSoftScore.ofSoft(-2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void guidedListMovesPreservePinnedPrefixAndRecalculateMutableBoundary(String workers) {
    var problem = TestdataPinnedWithIndexListSolution.generateInitializedSolution(8, 2);
    problem.getEntityList().getFirst().setPinIndex(2);
    problem.getEntityList().getLast().setPinned(true);
    var prefix =
        problem.getEntityList().getFirst().getValueList().subList(0, 2).stream()
            .map(TestdataPinnedWithIndexListValue::getCode)
            .toList();
    var fullyPinned =
        problem.getEntityList().getLast().getValueList().stream()
            .map(TestdataPinnedWithIndexListValue::getCode)
            .toList();
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataPinnedWithIndexListSolution.class)
            .withEntityClasses(
                TestdataPinnedWithIndexListEntity.class, TestdataPinnedWithIndexListValue.class)
            .withEasyScoreCalculatorClass(TestdataPinnedWithIndexListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(workers)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
                            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                            .withFeatureProviderClass(PinnedListFeatures.class)
                            .withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE))
                    .withMoveSelectorConfig(
                        new UnionMoveSelectorConfig()
                            .withSelectionOrder(SelectionOrder.ORIGINAL)
                            .withMoveSelectors(
                                new ListChangeMoveSelectorConfig(),
                                new ListSwapMoveSelectorConfig()))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(4)));
    var solver =
        (DefaultSolver<TestdataPinnedWithIndexListSolution>)
            SolverFactory.<TestdataPinnedWithIndexListSolution>create(config).buildSolver();
    var boundaries = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataPinnedWithIndexListSolution> scope) {
            var solution = scope.getWorkingSolution();
            var firstRoute = solution.getEntityList().getFirst().getValueList();
            assertThat(firstRoute.subList(0, 2))
                .extracting(TestdataPinnedWithIndexListValue::getCode)
                .containsExactlyElementsOf(prefix);
            assertThat(solution.getEntityList().getLast().getValueList())
                .extracting(TestdataPinnedWithIndexListValue::getCode)
                .containsExactlyElementsOf(fullyPinned);
            assertThat(
                    solution.getEntityList().stream()
                        .flatMap(entity -> entity.getValueList().stream())
                        .map(TestdataPinnedWithIndexListValue::getCode)
                        .toList())
                .containsExactlyInAnyOrderElementsOf(
                    problem.getValueList().stream()
                        .map(TestdataPinnedWithIndexListValue::getCode)
                        .toList());
            boundaries.add(firstRoute.get(2).getCode());
          }
        });

    var result = solver.solve(problem);

    assertThat(boundaries).hasSize(4);
    assertThat(new LinkedHashSet<>(boundaries)).hasSize(2);
    assertThat(result.getScore())
        .isEqualTo(new TestdataPinnedWithIndexListEasyScoreCalculator().calculateScore(result));
    var phase =
        (DefaultLocalSearchPhase<TestdataPinnedWithIndexListSolution>)
            solver.getPhaseList().getFirst();
    assertThat(
            ((GuidedLocalSearchDecider<TestdataPinnedWithIndexListSolution>) phase.getDecider())
                .getStatistics()
                .penaltyUpdates())
        .isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void structuralCycleCandidateSkipsFeaturesAndRestoresShadowStateOnUndo(String workers) {
    var entity = new TestdataDependencyNoInconsistentFieldEntity("entity");
    var first = new TestdataDependencyNoInconsistentFieldValue("first");
    var second = new TestdataDependencyNoInconsistentFieldValue("second");
    second.setDependencies(List.of(first));
    entity.setValues(new ArrayList<>(List.of(first, second)));
    var problem =
        new TestdataDependencyNoInconsistentFieldSolution(List.of(entity), List.of(first, second));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataDependencyNoInconsistentFieldSolution.class)
            .withEntityClasses(
                TestdataDependencyNoInconsistentFieldEntity.class,
                TestdataDependencyNoInconsistentFieldValue.class)
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(
                        TestdataDependencyNoInconsistentFieldConstraintProvider.class))
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(workers)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
                            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                            .withFeatureProviderClass(AcyclicFeatures.class)
                            .withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE))
                    .withMoveSelectorConfig(
                        new ListSwapMoveSelectorConfig()
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solver =
        (DefaultSolver<TestdataDependencyNoInconsistentFieldSolution>)
            SolverFactory.<TestdataDependencyNoInconsistentFieldSolution>create(config)
                .buildSolver();
    var attempts =
        new ArrayList<LocalSearchStepScope<TestdataDependencyNoInconsistentFieldSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(
              AbstractStepScope<TestdataDependencyNoInconsistentFieldSolution> scope) {
            attempts.add(
                (LocalSearchStepScope<TestdataDependencyNoInconsistentFieldSolution>) scope);
          }
        });

    var result = solver.solve(problem);

    // Swapping first and second conflicts with second's dependency on first; every move is cyclic.
    assertThat(attempts).hasSize(1);
    assertThat(attempts.getFirst().getPhaseScope().getPhaseMoveEvaluationCount()).isPositive();
    assertThat(attempts.getFirst().getSelectedMoveCount()).isZero();
    assertThat(attempts.getFirst().getStep()).isNull();
    assertThat(attempts.getFirst().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.NO_ADMISSIBLE_MOVE);
    var route = result.getEntities().getFirst();
    assertThat(route.getValues())
        .extracting(TestdataDependencyNoInconsistentFieldValue::getId)
        .containsExactly("first", "second");
    assertThat(route.getValues().getFirst().getPreviousValue()).isNull();
    assertThat(route.getValues().getLast().getPreviousValue())
        .isSameAs(route.getValues().getFirst());
    assertThat(route.getValues().getFirst().getEndTime())
        .isEqualTo(route.getStartTime().plusHours(1));
    assertThat(route.getValues().getLast().getEndTime())
        .isEqualTo(route.getStartTime().plusHours(2));
    // Independently replaying two one-hour tasks gives completion times of 60 and 120 minutes.
    assertThat(result.getScore()).isEqualTo(HardSoftScore.ofSoft(-180));
    var phase =
        (DefaultLocalSearchPhase<TestdataDependencyNoInconsistentFieldSolution>)
            solver.getPhaseList().getFirst();
    assertThat(
            ((GuidedLocalSearchDecider<TestdataDependencyNoInconsistentFieldSolution>)
                    phase.getDecider())
                .getStatistics()
                .penaltyUpdates())
        .isZero();
  }

  @Test
  void emptyProblemEndsWithoutInventingSteps() {
    var solver = solver(config(Backend.EASY, "NONE", 1, 2));
    var trace = recordSteps(solver);
    var problem = problem(false);
    problem.entities.clear();

    var result = solver.solve(problem);

    assertThat(trace).isEmpty();
    assertThat(result.score).isEqualTo(HardSoftScore.ZERO);
  }

  private static SolverConfig config(
      Backend backend, String workers, int targetLevel, int stepLimit) {
    var scoreConfig = new ScoreDirectorFactoryConfig();
    switch (backend) {
      case EASY -> scoreConfig.withEasyScoreCalculatorClass(LandscapeEasyScore.class);
      case INCREMENTAL ->
          scoreConfig.withIncrementalScoreCalculatorClass(LandscapeIncrementalScore.class);
      case BAVET -> scoreConfig.withConstraintProviderClass(LandscapeConstraints.class);
    }
    return new SolverConfig()
        .withSolutionClass(LandscapeSolution.class)
        .withEntityClasses(Assignment.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(0L)
        .withMoveThreadCount(workers)
        .withScoreDirectorFactory(scoreConfig)
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                .withGuidedLocalSearchConfig(guidance(targetLevel))
                .withMoveSelectorConfig(
                    new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(stepLimit)));
  }

  private static GuidedLocalSearchConfig guidance(int targetLevel) {
    return new GuidedLocalSearchConfig()
        .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
        .withFeatureProviderClass(AssignmentFeatures.class)
        .withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE)
        .withTargetScoreLevelIndex(targetLevel)
        .withPenaltyFactor(BigDecimal.ONE);
  }

  private static DefaultSolver<LandscapeSolution> solver(SolverConfig config) {
    return (DefaultSolver<LandscapeSolution>)
        SolverFactory.<LandscapeSolution>create(config).buildSolver();
  }

  private static List<Step> recordSteps(DefaultSolver<LandscapeSolution> solver) {
    var trace = new ArrayList<Step>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<LandscapeSolution> scope) {
            var solution = scope.getWorkingSolution();
            var independentScore = new LandscapeEasyScore().calculateScore(solution);
            assertThat(scope.getScore().raw()).isEqualTo(independentScore);
            trace.add(
                new Step(
                    solution.entities.stream().map(entity -> entity.choice.id).toList(),
                    independentScore));
          }
        });
    return trace;
  }

  private static List<HardSoftScore> recordAndVerifyBestSolutions(
      DefaultSolver<LandscapeSolution> solver) {
    var scores = new CopyOnWriteArrayList<HardSoftScore>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertThat(best.score).isEqualTo(new LandscapeEasyScore().calculateScore(best));
          scores.add(best.score);
        });
    return scores;
  }

  private static GuidedLocalSearchDecider.Statistics statistics(
      DefaultSolver<LandscapeSolution> solver) {
    var phase = (DefaultLocalSearchPhase<LandscapeSolution>) solver.getPhaseList().getFirst();
    return ((GuidedLocalSearchDecider<LandscapeSolution>) phase.getDecider()).getStatistics();
  }

  private static void assertBestIsUnpenalized(LandscapeSolution result) {
    assertThat(result.entities).extracting(entity -> entity.id).containsExactly("left", "right");
    assertThat(result.entities).extracting(entity -> entity.choice.id).containsExactly("B", "B");
    assertThat(result.score).isEqualTo(HardSoftScore.ZERO);
    assertThat(result.score).isEqualTo(new LandscapeEasyScore().calculateScore(result));
  }

  private static LandscapeSolution problem(boolean hardBarrier) {
    var solution = new LandscapeSolution();
    var a = new Choice("A", 10L);
    var b = new Choice("B", 1L);
    solution.choices = List.of(a, b);
    solution.entities =
        new ArrayList<>(
            List.of(
                new Assignment("left", a, hardBarrier), new Assignment("right", a, hardBarrier)));
    return solution;
  }

  enum Backend {
    EASY,
    INCREMENTAL,
    BAVET
  }

  private record Step(List<String> choices, HardSoftScore score) {}

  @PlanningSolution
  public static class LandscapeSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "choices")
    public List<Choice> choices;

    @PlanningEntityCollectionProperty public List<Assignment> entities;
    @PlanningScore public HardSoftScore score;

    public LandscapeSolution() {}
  }

  public static class Choice {
    @PlanningId public String id;
    public long featureCost;

    public Choice() {}

    Choice(String id, long featureCost) {
      this.id = id;
      this.featureCost = featureCost;
    }
  }

  @PlanningEntity
  public static class Assignment {
    @PlanningId public String id;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "choices")
    public Choice choice;

    public boolean hardBarrier;

    public Assignment() {}

    Assignment(String id, Choice choice, boolean hardBarrier) {
      this.id = id;
      this.choice = choice;
      this.hardBarrier = hardBarrier;
    }
  }

  public static class LandscapeEasyScore
      implements EasyScoreCalculator<LandscapeSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(LandscapeSolution solution) {
      if (solution.entities.size() < 2
          || solution.entities.stream().anyMatch(entity -> entity.choice == null)) {
        return HardSoftScore.ZERO;
      }
      var left = solution.entities.getFirst();
      var right = solution.entities.getLast();
      if (!left.choice.id.equals(right.choice.id)) {
        return HardSoftScore.of(left.hardBarrier ? -1 : 0, -5);
      }
      return "A".equals(left.choice.id) ? HardSoftScore.ofSoft(-2) : HardSoftScore.ZERO;
    }
  }

  public static class LandscapeIncrementalScore
      implements IncrementalScoreCalculator<LandscapeSolution, HardSoftScore> {
    private int assigned;
    private int bCount;
    private boolean hardBarrier;

    @Override
    public void resetWorkingSolution(LandscapeSolution solution) {
      assigned = 0;
      bCount = 0;
      hardBarrier = !solution.entities.isEmpty() && solution.entities.getFirst().hardBarrier;
      solution.entities.forEach(entity -> update(entity, 1));
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      update((Assignment) entity, -1);
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      update((Assignment) entity, 1);
    }

    private void update(Assignment entity, int delta) {
      if (entity.choice != null) {
        assigned += delta;
        if ("B".equals(entity.choice.id)) {
          bCount += delta;
        }
      }
    }

    @Override
    public HardSoftScore calculateScore() {
      if (assigned != 2) {
        return HardSoftScore.ZERO;
      }
      return switch (bCount) {
        case 0 -> HardSoftScore.ofSoft(-2);
        case 1 -> HardSoftScore.of(hardBarrier ? -1 : 0, -5);
        case 2 -> HardSoftScore.ZERO;
        default ->
            throw new IllegalStateException("Unexpected B assignment count (" + bCount + ").");
      };
    }
  }

  public static class LandscapeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachUniquePair(Assignment.class)
            .filter((left, right) -> left.hardBarrier && !left.choice.id.equals(right.choice.id))
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Mixed assignments infeasible"),
        factory
            .forEachUniquePair(Assignment.class)
            .penalize(
                HardSoftScore.ONE_SOFT,
                (left, right) ->
                    !left.choice.id.equals(right.choice.id)
                        ? 5
                        : "A".equals(left.choice.id) ? 2 : 0)
            .asConstraint("Assignment landscape")
      };
    }
  }

  public record AssignmentKey(String entityId, String choiceId) {}

  public record ListArc(String routeId, String fromId, String toId) {}

  public static class AcyclicFeatures
      implements GuidedLocalSearchFeatureProvider<
          TestdataDependencyNoInconsistentFieldSolution, String> {
    @Override
    public void extractFeatures(
        TestdataDependencyNoInconsistentFieldSolution solution,
        GuidedLocalSearchFeatureConsumer<String> consumer) {
      assertAcyclic(solution);
      for (var value : solution.getValues()) {
        consumer.accept(
            value.getId(),
            Duration.between(value.getEntity().getStartTime(), value.getEndTime()).toMinutes());
      }
    }

    private static void assertAcyclic(TestdataDependencyNoInconsistentFieldSolution solution) {
      assertThat(solution.getEntities().getFirst().getValues())
          .as("GLS must not read feature costs while the dependency graph is cyclic")
          .extracting(TestdataDependencyNoInconsistentFieldValue::getId)
          .containsExactly("first", "second");
      assertThat(solution.getValues())
          .allSatisfy(value -> assertThat(value.getEndTime()).isNotNull());
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataDependencyNoInconsistentFieldSolution, String>
        newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private TestdataDependencyNoInconsistentFieldSolution solution;
        private boolean dirty;

        @Override
        public void resetWorkingSolution(TestdataDependencyNoInconsistentFieldSolution solution) {
          this.solution = solution;
          dirty = true;
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          dirty = true;
        }

        @Override
        public void afterListVariableChanged(
            Object entity, String variableName, int fromIndex, int toIndex) {
          dirty = true;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          assertAcyclic(solution);
          if (dirty) {
            for (var value : solution.getValues()) {
              updater.accept(
                  value.getId(),
                  Duration.between(value.getEntity().getStartTime(), value.getEndTime())
                      .toMinutes());
            }
            dirty = false;
          }
        }
      };
    }
  }

  public static class PinnedListFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataPinnedWithIndexListSolution, ListArc> {
    @Override
    public void extractFeatures(
        TestdataPinnedWithIndexListSolution solution,
        GuidedLocalSearchFeatureConsumer<ListArc> consumer) {
      solution.getEntityList().forEach(route -> extractRoute(route, consumer));
    }

    private static void extractRoute(
        TestdataPinnedWithIndexListEntity route,
        GuidedLocalSearchFeatureConsumer<ListArc> consumer) {
      if (route.isPinned()) {
        return;
      }
      var values = route.getValueList();
      for (int index = route.getPinIndex(); index < values.size(); index++) {
        var from = index == 0 ? "depot" : values.get(index - 1).getCode();
        consumer.accept(new ListArc(route.getCode(), from, values.get(index).getCode()), 1L);
      }
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataPinnedWithIndexListSolution, ListArc>
        newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private final Set<TestdataPinnedWithIndexListEntity> dirty = new LinkedHashSet<>();
        private final Map<TestdataPinnedWithIndexListEntity, List<ListArc>> emitted =
            new IdentityHashMap<>();

        @Override
        public void resetWorkingSolution(TestdataPinnedWithIndexListSolution solution) {
          emitted.clear();
          dirty.clear();
          dirty.addAll(solution.getEntityList());
        }

        @Override
        public void afterListVariableChanged(
            Object entity, String variableName, int fromIndex, int toIndex) {
          dirty.add((TestdataPinnedWithIndexListEntity) entity);
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<ListArc> updater) {
          for (var route : dirty) {
            emitted.getOrDefault(route, List.of()).forEach(updater::remove);
            var keys = new ArrayList<ListArc>();
            extractRoute(
                route,
                new GuidedLocalSearchFeatureConsumer<>() {
                  @Override
                  public void accept(ListArc key, long cost) {
                    updater.accept(key, cost);
                    keys.add(key);
                  }

                  @Override
                  public void accept(ListArc key, BigDecimal cost) {
                    updater.accept(key, cost);
                    keys.add(key);
                  }
                });
            emitted.put(route, keys);
          }
          dirty.clear();
        }
      };
    }
  }

  public static class AssignmentFeatures
      implements GuidedLocalSearchFeatureProvider<LandscapeSolution, AssignmentKey> {
    @Override
    public void extractFeatures(
        LandscapeSolution solution, GuidedLocalSearchFeatureConsumer<AssignmentKey> consumer) {
      for (var entity : solution.entities) {
        if (!entity.pinned && entity.choice != null) {
          consumer.accept(new AssignmentKey(entity.id, entity.choice.id), featureCost(entity));
        }
      }
    }

    private static long featureCost(Assignment entity) {
      // Distinct costs make the first penalized assignment independent of tie sampling.
      return ("left".equals(entity.id) ? 3L : 1L) * entity.choice.featureCost;
    }

    @Override
    public GuidedLocalSearchFeatureSession<LandscapeSolution, AssignmentKey> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private final Set<Assignment> dirty = new LinkedHashSet<>();
        private final Map<Assignment, AssignmentKey> emitted = new IdentityHashMap<>();

        @Override
        public void resetWorkingSolution(LandscapeSolution solution) {
          emitted.clear();
          dirty.clear();
          dirty.addAll(solution.entities);
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          dirty.add((Assignment) entity);
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<AssignmentKey> updater) {
          for (var entity : dirty) {
            var previous = emitted.remove(entity);
            if (previous != null) {
              updater.remove(previous);
            }
            if (!entity.pinned && entity.choice != null) {
              var key = new AssignmentKey(entity.id, entity.choice.id);
              updater.accept(key, featureCost(entity));
              emitted.put(entity, key);
            }
          }
          dirty.clear();
        }
      };
    }
  }

  public static class ConstantFeatures
      implements GuidedLocalSearchFeatureProvider<LandscapeSolution, String> {
    @Override
    public void extractFeatures(
        LandscapeSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      consumer.accept("present-in-every-assignment", 10L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<LandscapeSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(LandscapeSolution solution) {}

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          updater.accept("present-in-every-assignment", 10L);
        }
      };
    }
  }
}
