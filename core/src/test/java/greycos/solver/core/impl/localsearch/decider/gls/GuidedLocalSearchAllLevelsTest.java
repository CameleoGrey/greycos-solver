package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchLevelScaleConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
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
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GuidedLocalSearchAllLevelsTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void implicitAllLevelsCrossesAnEarlierHardBarrierAndThenImprovesSoft(String threads) {
    var solver = build(null, BridgeScore.class, 3, threads);
    var trace = new ArrayList<BendableScore>();
    var focuses = observe(solver, trace);
    var best = solver.solve(solution("A", "B", "C", "D"));
    assertThat(trace).containsExactly(score(-1, -2, -8), score(0, 0, -5), score(0, 0, 0));
    assertThat(best.getScore()).isEqualTo(score(0, 0, 0));
    assertThat(focuses).containsExactly(1, 0, 2);
    assertThat(decider(solver).getControllerDiagnostics().excursionsStarted()).isEqualTo(1);
    assertThat(decider(solver).getControllerDiagnostics().excursionsRecovered()).isEqualTo(1);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void originalPrefixProgressRenewsTheSameFocus(String threads) {
    var solver =
        build(new GuidedLocalSearchConfig().withFocusStepLimit(1), ProgressScore.class, 4, threads);
    var trace = new ArrayList<BendableScore>();
    var focuses = observe(solver, trace);

    var best = solver.solve(solution("A", "B", "C", "D", "E"));

    assertThat(trace)
        .containsExactly(score(0, 0, -3), score(0, 0, -2), score(0, 0, -1), score(0, 0, 0));
    assertThat(best.getScore()).isEqualTo(score(0, 0, 0));
    assertThat(focuses).containsOnly(2);
    assertThat(decider(solver).getFocusSwitchCount()).isZero();
    assertThat(decider(solver).getControllerDiagnostics().excursionsStarted()).isZero();
    var diagnostics = decider(solver).getControllerDiagnostics();
    assertThat(diagnostics.attemptedCandidates()).isEqualTo(4);
    assertThat(diagnostics.doableCandidates()).isEqualTo(4);
    assertThat(diagnostics.admissibleCandidates()).isEqualTo(4);
    assertThat(diagnostics.committedMovesByFocusLevel()).containsExactly(0L, 0L, 4L);
    assertThat(diagnostics.committedMovesByReason()).containsExactly(4L, 0L, 0L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void stagnantCommittedEpochEscalatesAndExcursionHasItsOwnCommitBound(String threads) {
    var gls =
        new GuidedLocalSearchConfig()
            .withFocusStepLimit(2)
            .withFocusPenaltyUpdateLimit(100)
            .withExcursionStepLimit(3)
            .withLevelScaleList(
                List.of(
                    new GuidedLocalSearchLevelScaleConfig()
                        .withScoreLevelIndex(0)
                        .withScale(new BigDecimal("0.0125"))));
    var solver = build(gls, FlatScore.class, 6, threads);
    var focuses = observe(solver, new ArrayList<>());

    solver.solve(solution("A", "B"));

    assertThat(focuses).containsExactly(2, 2, 1, 1, 0, 2);
    assertThat(decider(solver).getControllerDiagnostics().excursionSteps()).isEqualTo(3);
    assertThat(decider(solver).getControllerDiagnostics().committedMovesByFocusLevel())
        .containsExactly(1L, 2L, 3L);
    assertThat(decider(solver).getControllerDiagnostics().committedMovesByState())
        .containsExactly(3L, 3L, 0L);
    assertThat(
            decider(solver)
                .getAutomaticScale(0)
                .compareTo(GuidedLocalSearchScale.of(new BigDecimal("0.0125"))))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void partialRepairDoesNotRenewItsCommitBudgetAndRetainsTheFeasibleBest(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withPenaltyFactor(BigDecimal.TEN)
                .withExcursionStepLimit(1)
                .withExcursionRepairStepLimit(1),
            RepairScore.class,
            100,
            threads);
    var trace = new ArrayList<BendableScore>();
    observe(solver, trace);
    var last = observeLastDecision(solver);

    var best = solver.solve(solution("A", "B", "C", "D", "E"));

    assertThat(trace).containsExactly(score(-3, 0, -9), score(-2, 0, -8));
    assertThat(best.getScore()).isEqualTo(score(0, 0, -10));
    assertThat(best.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.EXCURSION_REPAIR_EXHAUSTED);
    assertThat(decider(solver).getControllerDiagnostics().repairSteps()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void feasibleRepairReturnsToTheLastLevelAndPreservesPenaltyHistory(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withPenaltyFactor(BigDecimal.TEN)
                .withExcursionStepLimit(1)
                .withExcursionRepairStepLimit(8),
            RepairScore.class,
            4,
            threads);
    var trace = new ArrayList<BendableScore>();
    observe(solver, trace);

    var best = solver.solve(solution("A", "B", "C", "D", "E"));

    assertThat(trace)
        .containsExactly(score(-3, 0, -9), score(-2, 0, -8), score(-1, 0, -7), score(0, 0, 0));
    assertThat(best.getScore()).isEqualTo(score(0, 0, 0));
    var diagnostics = decider(solver).getControllerDiagnostics();
    assertThat(diagnostics.state()).isEqualTo(GuidedLocalSearchDecider.SearchState.NORMAL);
    assertThat(diagnostics.focusLevel()).isEqualTo(2);
    assertThat(diagnostics.repairSteps()).isEqualTo(3);
    assertThat(diagnostics.committedMovesByState()).containsExactly(0L, 1L, 3L);
    assertThat(diagnostics.committedMovesByFocusLevel()).containsExactly(4L, 0L, 0L);
    assertThat(diagnostics.excursionsRecovered()).isEqualTo(1);
    assertThat(diagnostics.dormantPenalizedFeatures()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void repairRejectsFurtherHardWorseningAndStopsWithoutAFallbackMove(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withPenaltyFactor(BigDecimal.TEN)
                .withExcursionStepLimit(1),
            UnrepairableScore.class,
            100,
            threads);
    var trace = new ArrayList<BendableScore>();
    observe(solver, trace);
    var last = observeLastDecision(solver);

    var best = solver.solve(solution("A", "B", "C"));

    assertThat(trace).containsExactly(score(-1, 0, -9));
    assertThat(best.getScore()).isEqualTo(score(0, 0, -10));
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.EXCURSION_REPAIR_EXHAUSTED);
    assertThat(last.get().getStep()).isNull();
    assertThat(decider(solver).getControllerDiagnostics().repairSteps()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void repairPlateauExhaustsTheSameBoundedStrictRetryLoop(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                .withTargetScoreLevelIndex(0)
                .withFeatureProviderClass(RepairPlateauFeatures.class)
                .withPenaltyFactor(BigDecimal.ONE)
                .withMaxPenaltyUpdatesPerStep(5)
                .withExcursionStepLimit(1),
            RepairPlateauScore.class,
            100,
            threads);
    var trace = new ArrayList<BendableScore>();
    var focuses = observe(solver, trace);
    var last = observeLastDecision(solver);

    var best = solver.solve(solution("A", "B", "C"));

    assertThat(trace).containsExactly(score(-1, 0, -9));
    assertThat(best.getScore()).isEqualTo(score(0, 0, -10));
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.EXCURSION_REPAIR_EXHAUSTED);
    assertThat(focuses).containsOnly(0);
    assertThat(decider(solver).getControllerDiagnostics().retryExhaustions()).isEqualTo(1);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(6);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void originalBestAspirationCannotLeaveFeasibilityOutsideAnExcursion(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC)
                .withTargetScoreLevelIndex(2),
            RewardedHardScore.class,
            100,
            threads);
    var trace = new ArrayList<BendableScore>();
    observe(solver, trace);
    var last = observeLastDecision(solver);

    var best = solver.solve(solution("A", "B"));

    // B improves the original lexicographic score, but violates its second hard level.
    assertThat(score(1, -1, 0).compareTo(score(0, 0, -10))).isPositive();
    assertThat(trace).isEmpty();
    assertThat(best.getScore()).isEqualTo(score(0, 0, -10));
    assertThat(best.getScore().isFeasible()).isTrue();
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.SAMPLE_EXHAUSTED);
    assertThat(decider(solver).getControllerDiagnostics().excursionsStarted()).isZero();
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void penaltyBudgetCountsAcrossFocusChangesWithinOneDecision(String threads) {
    var solver =
        build(
            new GuidedLocalSearchConfig()
                .withFeatureComposition(GuidedLocalSearchFeatureComposition.CUSTOM)
                .withFeatureProviderClass(VectorConstantFeatures.class)
                .withMaxPenaltyUpdatesPerStep(5)
                .withFocusPenaltyUpdateLimit(1),
            FlatScore.class,
            100,
            threads);
    var trace = new ArrayList<BendableScore>();
    observe(solver, trace);
    var last = observeLastDecision(solver);

    solver.solve(solution("A", "B"));

    assertThat(trace).isEmpty();
    assertThat(last.get().getNoStepReason())
        .isEqualTo(LocalSearchStepScope.NoStepReason.GUIDED_RETRY_EXHAUSTED);
    assertThat(decider(solver).getStatistics().penaltyUpdates()).isEqualTo(5);
    assertThat(decider(solver).getStatistics().decisionRounds()).isEqualTo(6);
    assertThat(decider(solver).getFocusSwitchCount()).isEqualTo(2);
    assertThat(decider(solver).getControllerDiagnostics().penaltyUpdatesByLevel())
        .containsExactly(3L, 1L, 1L);
    assertThat(decider(solver).getControllerDiagnostics().penalizedFeaturesByLevel())
        .containsExactly(3L, 1L, 1L);
  }

  @Test
  void explicitAllLevelsRejectsTargetAndInvalidScaleOverrides() {
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig()
                        .withGuidanceMode(GuidedLocalSearchGuidanceMode.ALL_LEVELS)
                        .withTargetScoreLevelIndex(0),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("targetScoreLevelIndex");
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig()
                        .withLevelScaleList(
                            List.of(
                                new GuidedLocalSearchLevelScaleConfig()
                                    .withScoreLevelIndex(3)
                                    .withScale(BigDecimal.ONE))),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("scoreLevelIndex");
    var duplicate =
        new GuidedLocalSearchLevelScaleConfig().withScoreLevelIndex(0).withScale(BigDecimal.ONE);
    assertThatThrownBy(
            () ->
                build(
                    new GuidedLocalSearchConfig().withLevelScaleList(List.of(duplicate, duplicate)),
                    FlatScore.class,
                    1,
                    "NONE"))
        .hasMessageContaining("Duplicate");
  }

  private static MultiHardSolution solution(String... codes) {
    var solution = new MultiHardSolution();
    solution.setValueList(java.util.Arrays.stream(codes).map(TestdataValue::new).toList());
    solution.setEntityList(
        List.of(new TestdataEntity("entity", solution.getValueList().getFirst())));
    return solution;
  }

  private static DefaultSolver<MultiHardSolution> build(
      GuidedLocalSearchConfig gls,
      Class<? extends EasyScoreCalculator<MultiHardSolution, BendableScore>> calculator,
      int steps,
      String threads) {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
            .withMoveSelectorConfig(
                new MoveIteratorFactoryConfig()
                    .withMoveIteratorFactoryClass(NextValueMoves.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
    if (gls != null) phase.withGuidedLocalSearchConfig(gls);
    var config =
        new SolverConfig()
            .withSolutionClass(MultiHardSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(calculator)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    return (DefaultSolver<MultiHardSolution>)
        SolverFactory.<MultiHardSolution>create(config).buildSolver();
  }

  @SuppressWarnings("unchecked")
  private static GuidedLocalSearchDecider<MultiHardSolution> decider(
      DefaultSolver<MultiHardSolution> solver) {
    return (GuidedLocalSearchDecider<MultiHardSolution>)
        ((DefaultLocalSearchPhase<MultiHardSolution>) solver.getPhaseList().getFirst())
            .getDecider();
  }

  private static List<Integer> observe(
      DefaultSolver<MultiHardSolution> solver, List<BendableScore> trace) {
    var focuses = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<MultiHardSolution> scope) {
            focuses.add(decider(solver).getFocusScoreLevelIndex());
          }

          @Override
          public void stepEnded(AbstractStepScope<MultiHardSolution> scope) {
            trace.add((BendableScore) scope.getScore().raw());
          }
        });
    return focuses;
  }

  private static AtomicReference<LocalSearchStepScope<MultiHardSolution>> observeLastDecision(
      DefaultSolver<MultiHardSolution> solver) {
    var last = new AtomicReference<LocalSearchStepScope<MultiHardSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<MultiHardSolution> scope) {
            last.set((LocalSearchStepScope<MultiHardSolution>) scope);
          }
        });
    return last;
  }

  private static BendableScore score(long firstHard, long secondHard, long soft) {
    return BendableScore.of(new long[] {firstHard, secondHard}, new long[] {soft});
  }

  public static class BridgeScore implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "A" -> score(0, -1, -9);
        case "B" -> score(-1, -2, -8);
        case "C" -> score(0, 0, -5);
        default -> score(0, 0, 0);
      };
    }
  }

  public static class ProgressScore
      implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return score(0, 0, solution.getEntityList().getFirst().getValue().getCode().charAt(0) - 'E');
    }
  }

  public static class RepairScore implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "A" -> score(0, 0, -10);
        case "B" -> score(-3, 0, -9);
        case "C" -> score(-2, 0, -8);
        case "D" -> score(-1, 0, -7);
        default -> score(0, 0, 0);
      };
    }
  }

  public static class UnrepairableScore
      implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return switch (solution.getEntityList().getFirst().getValue().getCode()) {
        case "A" -> score(0, 0, -10);
        case "B" -> score(-1, 0, -9);
        default -> score(-2, 0, -8);
      };
    }
  }

  public static class RewardedHardScore
      implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return solution.getEntityList().getFirst().getValue().getCode().equals("A")
          ? score(0, 0, -10)
          : score(1, -1, 0);
    }
  }

  public static class RepairPlateauScore
      implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return solution.getEntityList().getFirst().getValue().getCode().equals("A")
          ? score(0, 0, -10)
          : score(-1, 0, -9);
    }
  }

  public static class RepairPlateauFeatures
      implements GuidedLocalSearchFeatureProvider<MultiHardSolution, String> {
    private static String feature(MultiHardSolution solution) {
      return solution.getEntityList().getFirst().getValue().getCode().equals("A") ? "A" : "plateau";
    }

    @Override
    public void extractFeatures(
        MultiHardSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      String key = feature(solution);
      consumer.accept(key, key.equals("A") ? 10L : 1L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<MultiHardSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private MultiHardSolution solution;
        private String previous;
        private boolean dirty;

        @Override
        public void resetWorkingSolution(MultiHardSolution solution) {
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
          previous = feature(solution);
          updater.accept(previous, previous.equals("A") ? 10L : 1L);
          dirty = false;
        }
      };
    }
  }

  public static class VectorConstantFeatures
      implements GuidedLocalSearchFeatureProvider<MultiHardSolution, String> {
    @Override
    public void extractFeatures(
        MultiHardSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      consumer.acceptScore("constant", score(1, 1, 1));
    }

    @Override
    public GuidedLocalSearchFeatureSession<MultiHardSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private boolean dirty;

        @Override
        public void resetWorkingSolution(MultiHardSolution solution) {
          dirty = true;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          if (dirty) updater.acceptScore("constant", score(1, 1, 1));
          dirty = false;
        }
      };
    }
  }

  public static class FlatScore implements EasyScoreCalculator<MultiHardSolution, BendableScore> {
    @Override
    public BendableScore calculateScore(MultiHardSolution solution) {
      return score(0, 0, 0);
    }
  }

  public static class NextValueMoves
      implements MoveIteratorFactory<MultiHardSolution, Move<MultiHardSolution>> {
    @Override
    public long getSize(ScoreDirector<MultiHardSolution> director) {
      return 1;
    }

    @Override
    public Iterator<Move<MultiHardSolution>> createOriginalMoveIterator(
        ScoreDirector<MultiHardSolution> director) {
      var solution = director.getWorkingSolution();
      var entity = solution.getEntityList().getFirst();
      int index = solution.getValueList().indexOf(entity.getValue());
      var target = solution.getValueList().get((index + 1) % solution.getValueList().size());
      var descriptor =
          ((InnerScoreDirector<MultiHardSolution, ?>) director)
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      return List.<Move<MultiHardSolution>>of(new ChangeMove<>(descriptor, entity, target))
          .iterator();
    }

    @Override
    public Iterator<Move<MultiHardSolution>> createRandomMoveIterator(
        ScoreDirector<MultiHardSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
    }
  }

  @PlanningSolution
  public static class MultiHardSolution {
    private List<TestdataValue> valueList;
    private List<TestdataEntity> entityList;
    private BendableScore score;

    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    public List<TestdataValue> getValueList() {
      return valueList;
    }

    public void setValueList(List<TestdataValue> valueList) {
      this.valueList = valueList;
    }

    @PlanningEntityCollectionProperty
    public List<TestdataEntity> getEntityList() {
      return entityList;
    }

    public void setEntityList(List<TestdataEntity> entityList) {
      this.entityList = entityList;
    }

    @PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 1)
    public BendableScore getScore() {
      return score;
    }

    public void setScore(BendableScore score) {
      this.score = score;
    }
  }
}
