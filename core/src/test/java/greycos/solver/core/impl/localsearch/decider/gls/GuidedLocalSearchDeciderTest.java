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
  void sampledEscapeReevaluatesRetainedMoveWhenFreshProbesViolatePrefix(
      String threads, int sampleSize) {
    var gls =
        new GuidedLocalSearchConfig()
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
    var selected = new AtomicReference<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataHardSoftScoreSolution> scope) {
            trace.add((HardSoftScore) scope.getScore().raw());
            selected.set(
                ((LocalSearchStepScope<TestdataHardSoftScoreSolution>) scope)
                    .getSelectedMoveCount());
          }
        });
    var solution = new TestdataHardSoftScoreSolution("recovery");
    var a = new TestdataValue("A");
    solution.setValueList(List.of(a, new TestdataValue("B"), new TestdataValue("C")));
    solution.setEntityList(List.of(new TestdataEntity("entity", a)));

    var best = solver.solve(solution);

    assertThat(trace).containsExactly(HardSoftScore.ofSoft(-1));
    assertThat(best.getScore()).isEqualTo(HardSoftScore.ZERO);
    assertThat(best.getEntityList().getFirst().getValue().getCode()).isEqualTo("A");
    assertThat(selected.get()).isEqualTo(2L * sampleSize);
    var controller =
        (GuidedLocalSearchDecider<TestdataHardSoftScoreSolution>)
            ((DefaultLocalSearchPhase<TestdataHardSoftScoreSolution>)
                    solver.getPhaseList().getFirst())
                .getDecider();
    assertThat(controller.getStatistics().penaltyUpdates()).isEqualTo(1);
    assertThat(controller.getStatistics().decisionRounds()).isEqualTo(2);
    assertThat(controller.getStatistics().recoveryMoves()).isEqualTo(1);
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
                build(phase(new GuidedLocalSearchConfig(), new ChangeMoveSelectorConfig()), "NONE"))
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
  }

  @Test
  void invalidSettingsFailAtBuildTime() {
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
    return new GuidedLocalSearchConfig().withFeatureProviderClass(EmptyProvider.class);
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
              solution.getValueList().get(round++ == 0 ? 1 : 2));
      return Collections.nCopies(4, move).iterator();
    }

    @Override
    public Iterator<Move<TestdataHardSoftScoreSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataHardSoftScoreSolution> director, RandomGenerator random) {
      return createOriginalMoveIterator(director);
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
