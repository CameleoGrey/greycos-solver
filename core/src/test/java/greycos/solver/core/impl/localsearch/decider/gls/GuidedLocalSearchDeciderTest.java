package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GuidedLocalSearchDeciderTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void neverEndingNonDoableMovesConsumeTheSampleBudget(String threads) {
    var gls = glsConfig().withSampleSize(4).withMaxUnproductiveRounds(3);
    var solver =
        build(
            phase(
                gls,
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(NonDoableMoveFactory.class)),
            threads);
    var last = observe(solver, new AtomicInteger(), new AtomicInteger());
    try {
      var result = solver.solve(TestdataSolution.generateSolution(2, 1));

      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(NonDoableMoveFactory.GENERATED.get()).hasValue(12);
      assertThat(last.get().getNoStepReason())
          .isEqualTo(LocalSearchStepScope.NoStepReason.SAMPLE_EXHAUSTED);
      assertThat(last.get().getSelectedMoveCount()).isZero();
      assertThat(decider(solver).getStatistics().emptyRounds()).isEqualTo(3);
      var diagnostics = decider(solver).getControllerDiagnostics();
      assertThat(diagnostics.attemptedCandidates()).isEqualTo(12);
      assertThat(diagnostics.doableCandidates()).isZero();
      assertThat(diagnostics.admissibleCandidates()).isZero();
      assertThat(diagnostics.committedMovesByFocusLevel()).containsExactly(0L);
      assertThat(diagnostics.committedMovesByReason()).containsExactly(0L, 0L, 0L);
    } finally {
      NonDoableMoveFactory.GENERATED.remove();
    }
  }

  static Stream<Arguments> recoveryConfigurations() {
    return Stream.of("NONE", "2")
        .flatMap(threads -> Stream.of(1, 3).map(size -> Arguments.of(threads, size)));
  }

  @ParameterizedTest
  @MethodSource("recoveryConfigurations")
  void sampledRetriesDoNotRetainOrdinaryMoves(String threads, int sampleSize) {
    var gls =
        new GuidedLocalSearchConfig()
            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
            .withFeatureProviderClass(RecoveryFeatures.class)
            .withPenaltyFactor(BigDecimal.ONE)
            .withTargetScoreLevelIndex(1)
            .withSampleSize(sampleSize);
    var phaseConfig =
        phase(
            gls,
            new MoveIteratorFactoryConfig()
                .withMoveIteratorFactoryClass(RecoveryMoveFactory.class)
                .withSelectionOrder(SelectionOrder.ORIGINAL));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataHardSoftScoreSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(RecoveryScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phaseConfig);
    var solver =
        (DefaultSolver<TestdataHardSoftScoreSolution>)
            SolverFactory.<TestdataHardSoftScoreSolution>create(config).buildSolver();
    var trace = new ArrayList<HardSoftScore>();
    var lastDecision = new AtomicReference<LocalSearchStepScope<TestdataHardSoftScoreSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataHardSoftScoreSolution> scope) {
            lastDecision.set((LocalSearchStepScope<TestdataHardSoftScoreSolution>) scope);
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataHardSoftScoreSolution> scope) {
            trace.add((HardSoftScore) scope.getScore().raw());
          }
        });
    var solution = new TestdataHardSoftScoreSolution("recovery");
    var a = new TestdataValue("A");
    solution.setValueList(List.of(a, new TestdataValue("B"), new TestdataValue("C")));
    solution.setEntityList(List.of(new TestdataEntity("entity", a)));

    var best = solver.solve(solution);

    assertThat(trace).isEmpty();
    assertThat(best.getScore()).isEqualTo(HardSoftScore.ZERO);
    assertThat(best.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
    assertThat(lastDecision.get().getSelectedMoveCount()).isEqualTo(4L * sampleSize);
    assertThat(lastDecision.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.SAMPLE_EXHAUSTED);
    var controller =
        (GuidedLocalSearchDecider<TestdataHardSoftScoreSolution>)
            ((DefaultLocalSearchPhase<TestdataHardSoftScoreSolution>)
                    solver.getPhaseList().getFirst())
                .getDecider();
    assertThat(controller.getStatistics().penaltyUpdates()).isEqualTo(1);
    assertThat(controller.getStatistics().decisionRounds()).isEqualTo(4);
    assertThat(controller.getStatistics().recoveryMoves()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void aGuidedTieIsRejectedUntilTheNextPenaltyUpdateCreatesStrictImprovement(String threads) {
    var gls =
        new GuidedLocalSearchConfig()
            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
            .withFeatureProviderClass(RecoveryFeatures.class)
            .withPenaltyFactor(new BigDecimal("0.1"))
            .withTargetScoreLevelIndex(1)
            .withSampleSize(1);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataHardSoftScoreSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(RecoveryScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(
                phase(
                    gls,
                    new MoveIteratorFactoryConfig()
                        .withMoveIteratorFactoryClass(RepeatedRecoveryMoveFactory.class)
                        .withSelectionOrder(SelectionOrder.ORIGINAL)));
    var solver =
        (DefaultSolver<TestdataHardSoftScoreSolution>)
            SolverFactory.<TestdataHardSoftScoreSolution>create(config).buildSolver();
    var trace = new ArrayList<HardSoftScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataHardSoftScoreSolution> scope) {
            trace.add((HardSoftScore) scope.getScore().raw());
          }
        });
    var solution = new TestdataHardSoftScoreSolution("strict");
    var a = new TestdataValue("A");
    solution.setValueList(List.of(a, new TestdataValue("B")));
    solution.setEntityList(List.of(new TestdataEntity("entity", a)));

    var best = solver.solve(solution);

    assertThat(trace).containsExactly(HardSoftScore.ofSoft(-1));
    assertThat(best.getScore()).isEqualTo(HardSoftScore.ZERO);
    var controller =
        (GuidedLocalSearchDecider<TestdataHardSoftScoreSolution>)
            ((DefaultLocalSearchPhase<TestdataHardSoftScoreSolution>)
                    solver.getPhaseList().getFirst())
                .getDecider();
    assertThat(controller.getStatistics().penaltyUpdates()).isEqualTo(2);
    assertThat(controller.getStatistics().decisionRounds()).isEqualTo(3);
    assertThat(controller.getStatistics().recoveryMoves()).isZero();
    var diagnostics = controller.getControllerDiagnostics();
    assertThat(diagnostics.attemptedCandidates()).isEqualTo(3);
    assertThat(diagnostics.doableCandidates()).isEqualTo(3);
    assertThat(diagnostics.admissibleCandidates()).isEqualTo(3);
    assertThat(diagnostics.penaltyUpdatesByLevel()).containsExactly(0L, 2L);
    assertThat(diagnostics.penalizedFeaturesByLevel()).containsExactly(0L, 2L);
    assertThat(diagnostics.scaleObservationCounts()).containsExactly(0, 0);
    assertThat(diagnostics.committedMovesByFocusLevel()).containsExactly(0L, 1L);
    assertThat(diagnostics.committedMovesByState()).containsExactly(1L, 0L, 0L);
    assertThat(diagnostics.committedMovesByReason()).containsExactly(0L, 1L, 0L);
    assertThatThrownBy(() -> diagnostics.penaltyUpdatesByLevel().add(1L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void constantFeaturesExhaustTheRetryBudgetWithoutCommittingAMove(String threads) {
    var solver =
        build(
            phase(
                glsConfig()
                    .withFeatureProviderClass(ConstantFeatures.class)
                    .withSampleSize(3)
                    .withMaxPenaltyUpdatesPerStep(7),
                new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL)),
            threads);
    var ended = new AtomicInteger();
    var last = observe(solver, new AtomicInteger(), ended);

    var best = solver.solve(TestdataSolution.generateSolution(2, 1));

    assertThat(best.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(ended).hasValue(0);
    assertThat(last.get().getAcceptedMoveCount()).isZero();
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.GUIDED_RETRY_EXHAUSTED);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(7);
    assertThat(decider(solver).getStatistics().decisionRounds()).isEqualTo(8);
    assertThat(decider(solver).getControllerDiagnostics().retryExhaustions()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void fixedTargetCanExplicitlyUseAutomaticFeaturesWithoutAProvider(String threads) {
    var config =
        new GuidedLocalSearchConfig()
            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
            .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC)
            .withSampleSize(3);
    var solver =
        build(
            phase(
                config, new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL)),
            threads);
    var ended = new AtomicInteger();
    observe(solver, new AtomicInteger(), ended);

    solver.solve(TestdataSolution.generateSolution(2, 1));

    assertThat(ended).hasValue(1);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(1);
    assertThat(decider(solver).getControllerDiagnostics().featureComposition())
        .isEqualTo(GuidedLocalSearchFeatureComposition.AUTOMATIC);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "1", "2"})
  void emptySamplesStopWithStepOnlyTermination(String threads) {
    var gls = glsConfig().withSampleSize(4).withMaxUnproductiveRounds(3);
    var phaseConfig =
        phase(
            gls,
            new ChangeMoveSelectorConfig()
                .withSelectionOrder(SelectionOrder.ORIGINAL)
                .withFilterClass(RejectAll.class));
    var solver = build(phaseConfig, threads);
    var started = new AtomicInteger();
    var ended = new AtomicInteger();
    var last = observe(solver, started, ended);

    var result = solver.solve(TestdataSolution.generateSolution(2, 1));

    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(started).hasValue(1);
    assertThat(ended).hasValue(0);
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.SAMPLE_EXHAUSTED);
    assertThat(last.get().getSelectedMoveCount()).isZero();
    assertThat(decider(solver).getStatistics().emptyRounds()).isEqualTo(3);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void exhaustiveEmptyNeighborhoodStopsAfterOneSweep(String threads) {
    var gls = glsConfig().withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE);
    var solver =
        build(phase(gls, new ChangeMoveSelectorConfig().withFilterClass(RejectAll.class)), threads);
    var last = observe(solver, new AtomicInteger(), new AtomicInteger());
    solver.solve(TestdataSolution.generateSolution(2, 1));
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.NO_ADMISSIBLE_MOVE);
    assertThat(decider(solver).getStatistics().decisionRounds()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void noPenalizableFeaturesStopsWithoutRejectedFallback(String threads) {
    var solver =
        build(
            phase(
                glsConfig().withSampleSize(3),
                new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL)),
            threads);
    var ended = new AtomicInteger();
    var last = observe(solver, new AtomicInteger(), ended);
    solver.solve(TestdataSolution.generateSolution(2, 1));
    assertThat(last.get().getSelectedMoveCount()).isPositive();
    assertThat(last.get().getAcceptedMoveCount()).isZero();
    assertThat(last.get().getStep()).isNull();
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.NO_PENALIZABLE_FEATURES);
    assertThat(ended).hasValue(0);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isZero();
  }

  @Test
  void featureProviderRequiredAtBuildTime() {
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        new GuidedLocalSearchConfig()
                            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("featureProviderClass");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void reusingSolverForEmptyInputClearsPreviousStatistics(String threads) {
    var solver =
        build(
            phase(
                glsConfig(),
                new ChangeMoveSelectorConfig()
                    .withSelectionOrder(SelectionOrder.ORIGINAL)
                    .withFilterClass(RejectAll.class)),
            threads);
    solver.solve(TestdataSolution.generateSolution(2, 1));
    assertThat(decider(solver).getStatistics().decisionRounds()).isEqualTo(3);
    if (!threads.equals("NONE")) {
      assertThat(decider(solver).getMoveEvaluationDiagnostics()).isNotNull();
    }

    solver.solve(TestdataSolution.generateSolution(2, 0));

    assertThat(decider(solver).getStatistics())
        .isEqualTo(new GuidedLocalSearchDecider.Statistics(0, 0, 0, 0));
    assertThat(decider(solver).getMoveEvaluationDiagnostics()).isNull();
    assertThat(decider(solver).getUncreditedCalculationCount()).isZero();
    assertThat(decider(solver).getFocusSwitchCount()).isZero();
    assertThat(decider(solver).getFocusScoreLevelIndex()).isEqualTo(-1);
    assertThat(decider(solver).getControllerDiagnostics().attemptedCandidates()).isZero();
    assertThat(decider(solver).getControllerDiagnostics().penaltyUpdatesByLevel()).isEmpty();
    assertThat(decider(solver).getControllerDiagnostics().eligibleOriginProbes()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void diagnosticsRetainPhaseTotalsAcrossPendingMigrationGuidanceReset(String threads) {
    var gls =
        new GuidedLocalSearchConfig()
            .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC)
            .withDirectedOriginSelection(true)
            .withSampleSize(3);
    var solver =
        build(
            phase(gls, new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)),
            threads);
    var beforeMigration = new AtomicReference<GuidedLocalSearchDecider.ControllerDiagnostics>();
    var afterMigration = new AtomicReference<GuidedLocalSearchDecider.ControllerDiagnostics>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            if (scope.getStepIndex() == 2) {
              afterMigration.set(decider(solver).getControllerDiagnostics());
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            if (scope.getStepIndex() == 0) {
              beforeMigration.set(decider(solver).getControllerDiagnostics());
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
                          working.getValueList().getLast()),
                      true);
            }
          }
        });

    solver.solve(TestdataSolution.generateSolution(3, 1));

    var before = beforeMigration.get();
    var after = afterMigration.get();
    assertThat(before.eligibleOriginProbes()).isPositive();
    assertThat(after.eligibleOriginProbes()).isEqualTo(before.eligibleOriginProbes());
    assertThat(after.emittedOrigins()).isEqualTo(before.emittedOrigins());
    assertThat(after.ordinaryOrigins()).isEqualTo(before.ordinaryOrigins());
    assertThat(after.attemptedCandidates()).isEqualTo(before.attemptedCandidates());
    assertThat(after.penaltyUpdatesByLevel()).isEqualTo(before.penaltyUpdatesByLevel());
    assertThat(after.committedMovesByReason()).containsExactly(0L, 1L, 1L);
    var diagnostics = decider(solver).getControllerDiagnostics();
    assertThat(diagnostics.eligibleOriginProbes()).isGreaterThan(after.eligibleOriginProbes());
    assertThat(diagnostics.eligibleOriginProbes())
        .isGreaterThanOrEqualTo(diagnostics.emittedOrigins());
    assertThat(diagnostics.committedMovesByFocusLevel()).containsExactly(3L);
    assertThat(diagnostics.committedMovesByState()).containsExactly(3L, 0L, 0L);
    assertThat(diagnostics.committedMovesByReason()).containsExactly(0L, 2L, 1L);
    assertThat(diagnostics.penaltyUpdatesByLevel().getFirst())
        .isEqualTo(diagnostics.penaltyUpdates());
  }

  @Test
  void invalidSettingsFailAtBuildTime() {
    for (var invalid :
        List.of(
            glsConfig().withMaxPenaltyUpdatesPerStep(0),
            glsConfig().withExcursionStepLimit(0),
            glsConfig().withExcursionRepairStepLimit(0),
            glsConfig().withFocusStepLimit(0),
            glsConfig().withFocusPenaltyUpdateLimit(0))) {
      assertThatThrownBy(() -> build(phase(invalid, new ChangeMoveSelectorConfig()), "NONE"))
          .hasMessageContaining("positive");
    }
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        glsConfig()
                            .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("featureProviderClass");
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        new GuidedLocalSearchConfig()
                            .withFeatureComposition(GuidedLocalSearchFeatureComposition.COMBINED),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("featureProviderClass");
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        glsConfig().withAutomaticListOwnershipEnabled(true),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("CUSTOM");
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        glsConfig().withPenaltyFactor(BigDecimal.ZERO),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("penaltyFactor");
    assertThatThrownBy(
            () ->
                build(
                    phase(glsConfig().withTargetScoreLevelIndex(1), new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("targetScoreLevelIndex");
    assertThatThrownBy(
            () ->
                build(phase(glsConfig().withSampleSize(0), new ChangeMoveSelectorConfig()), "NONE"))
        .hasMessageContaining("sampleSize");
    assertThatThrownBy(
            () ->
                build(
                    phase(glsConfig().withMaxUnproductiveRounds(0), new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("maxUnproductiveRounds");
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        glsConfig()
                            .withSearchMode(GuidedLocalSearchSearchMode.EXHAUSTIVE)
                            .withSampleSize(10),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("sampleSize")
        .hasMessageContaining("EXHAUSTIVE");
  }

  @Test
  void rejectsIncompatibleConfiguration() {
    assertThatThrownBy(
            () ->
                build(
                    phase(glsConfig(), new ChangeMoveSelectorConfig())
                        .withForagerConfig(new LocalSearchForagerConfig()),
                    "NONE"))
        .hasMessageContaining("forager");
    assertThatThrownBy(
            () ->
                build(
                    phase(glsConfig(), new ChangeMoveSelectorConfig())
                        .withLocalSearchType(LocalSearchType.HILL_CLIMBING),
                    "NONE"))
        .hasMessageContaining("GUIDED_LOCAL_SEARCH");
    assertThatThrownBy(
            () ->
                build(
                    phase(
                        glsConfig().withFeatureProviderClass(PrivateConstructorProvider.class),
                        new ChangeMoveSelectorConfig()),
                    "NONE"))
        .hasMessageContaining("public no-arg constructor");
  }

  @Test
  void exhaustiveValidationRejectsFiniteSamplingWrappersAndNestedRandomness() {
    assertThatThrownBy(
            () ->
                GuidedLocalSearchExhaustiveValidator.validate(
                    new KOptListMoveSelectorConfig().withSelectedCountLimit(10L)))
        .hasMessageContaining("EXHAUSTIVE");
    assertThatThrownBy(
            () ->
                GuidedLocalSearchExhaustiveValidator.validate(
                    new ChangeMoveSelectorConfig().withSelectedCountLimit(10L)))
        .hasMessageContaining("selectedCountLimit");
    assertThatThrownBy(
            () ->
                GuidedLocalSearchExhaustiveValidator.validate(
                    new ChangeMoveSelectorConfig()
                        .withEntitySelectorConfig(
                            new EntitySelectorConfig().withSelectionOrder(SelectionOrder.RANDOM))))
        .hasMessageContaining("RANDOM");
    assertThatThrownBy(() -> GuidedLocalSearchExhaustiveValidator.validate(null))
        .hasMessageContaining("explicit");
    GuidedLocalSearchExhaustiveValidator.validate(
        new UnionMoveSelectorConfig()
            .withMoveSelectors(
                new ListChangeMoveSelectorConfig(), new ListSwapMoveSelectorConfig()));
  }

  private static GuidedLocalSearchConfig glsConfig() {
    return new GuidedLocalSearchConfig()
        .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
        .withFeatureProviderClass(EmptyProvider.class);
  }

  private static LocalSearchPhaseConfig phase(
      GuidedLocalSearchConfig gls, MoveSelectorConfig<?> moves) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
        .withGuidedLocalSearchConfig(gls)
        .withMoveSelectorConfig(moves)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  private static DefaultSolver<TestdataSolution> build(
      LocalSearchPhaseConfig phase, String threads) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ZeroScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static GuidedLocalSearchDecider<TestdataSolution> decider(
      DefaultSolver<TestdataSolution> solver) {
    return (GuidedLocalSearchDecider<TestdataSolution>)
        ((DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst()).getDecider();
  }

  private static AtomicReference<LocalSearchStepScope<TestdataSolution>> observe(
      DefaultSolver<TestdataSolution> solver, AtomicInteger started, AtomicInteger ended) {
    var last = new AtomicReference<LocalSearchStepScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            started.incrementAndGet();
            last.set((LocalSearchStepScope<TestdataSolution>) scope);
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            ended.incrementAndGet();
          }
        });
    return last;
  }

  public static class ZeroScore implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class RejectAll implements SelectionFilter<TestdataSolution, Object> {
    @Override
    public boolean accept(ScoreDirector<TestdataSolution> scoreDirector, Object selection) {
      return false;
    }
  }

  public static class EmptyProvider
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, String> {
    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {}

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(TestdataSolution solution) {}

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {}
      };
    }
  }

  public static class ConstantFeatures extends EmptyProvider {
    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      consumer.accept("constant", 1L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private boolean reset;

        @Override
        public void resetWorkingSolution(TestdataSolution solution) {
          reset = true;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          if (reset) updater.accept("constant", 1L);
          reset = false;
        }
      };
    }
  }

  public static class PrivateConstructorProvider extends EmptyProvider {
    private PrivateConstructorProvider() {}
  }

  public static class RecoveryScore
      implements EasyScoreCalculator<TestdataHardSoftScoreSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataHardSoftScoreSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "A" -> HardSoftScore.ZERO;
        case "B" -> HardSoftScore.ofSoft(-1);
        case "C" -> HardSoftScore.ofHard(-1);
        default -> throw new IllegalStateException();
      };
    }
  }

  public static class RecoveryMoveFactory
      implements MoveIteratorFactory<
          TestdataHardSoftScoreSolution, Move<TestdataHardSoftScoreSolution>> {
    private int round;

    @Override
    public void phaseStarted(ScoreDirector<TestdataHardSoftScoreSolution> director) {
      round = 0;
    }

    @Override
    public long getSize(ScoreDirector<TestdataHardSoftScoreSolution> director) {
      return 4;
    }

    @Override
    public Iterator<Move<TestdataHardSoftScoreSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataHardSoftScoreSolution> director) {
      var solution = director.getWorkingSolution();
      var descriptor =
          ((InnerScoreDirector<TestdataHardSoftScoreSolution, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      Move<TestdataHardSoftScoreSolution> move =
          new ChangeMove<>(
              descriptor,
              solution.getEntityList().getFirst(),
              solution.getValueList().get(nextValueIndex()));
      return Collections.nCopies(4, move).iterator();
    }

    protected int nextValueIndex() {
      return round++ == 0 ? 1 : 2;
    }

    @Override
    public Iterator<Move<TestdataHardSoftScoreSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataHardSoftScoreSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  public static class RepeatedRecoveryMoveFactory extends RecoveryMoveFactory {
    @Override
    protected int nextValueIndex() {
      return 1;
    }
  }

  public static class NonDoableMoveFactory
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    private static final ThreadLocal<AtomicInteger> GENERATED =
        ThreadLocal.withInitial(AtomicInteger::new);

    @Override
    public void phaseStarted(ScoreDirector<TestdataSolution> director) {
      GENERATED.get().set(0);
    }

    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return Long.MAX_VALUE;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      throw new UnsupportedOperationException(
          "This test factory only generates random candidates.");
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Move<TestdataSolution> next() {
          GENERATED.get().incrementAndGet();
          return SelectorBasedNoChangeMove.getInstance();
        }
      };
    }
  }

  public static class RecoveryFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataHardSoftScoreSolution, String> {
    @Override
    public void extractFeatures(
        TestdataHardSoftScoreSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      var key = solution.getEntityList().getFirst().getValue().getCode();
      consumer.accept(key, key.equals("A") ? 10L : 1L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataHardSoftScoreSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private TestdataHardSoftScoreSolution solution;
        private String previous;
        private boolean dirty;

        @Override
        public void resetWorkingSolution(TestdataHardSoftScoreSolution solution) {
          this.solution = solution;
          previous = null;
          dirty = true;
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          dirty = true;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          if (!dirty) return;
          if (previous != null) updater.remove(previous);
          previous = solution.getEntityList().getFirst().getValue().getCode();
          updater.accept(previous, previous.equals("A") ? 10L : 1L);
          dirty = false;
        }
      };
    }
  }
}
