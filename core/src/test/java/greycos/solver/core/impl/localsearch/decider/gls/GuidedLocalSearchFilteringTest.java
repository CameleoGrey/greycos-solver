package greycos.solver.core.impl.localsearch.decider.gls;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase.DefaultConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.placer.EntityPlacer;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt.SelectorBasedTwoOptListMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(20)
class GuidedLocalSearchFilteringTest {

  static Stream<Arguments> phaseTypesAndWorkers() {
    return Stream.of(LocalSearchType.GUIDED_LOCAL_SEARCH, LocalSearchType.HILL_CLIMBING)
        .flatMap(type -> Stream.of("NONE", "2").map(threads -> Arguments.of(type, threads)));
  }

  @ParameterizedTest
  @MethodSource("phaseTypesAndWorkers")
  void typedKOptUserFilterNeverReceivesNonDoablePlaceholder(LocalSearchType type, String threads) {
    var phase =
        new LocalSearchPhaseConfig()
            .withLocalSearchType(type)
            .withMoveSelectorConfig(
                new KOptListMoveSelectorConfig()
                    .withOriginSelectorConfig(
                        new ValueSelectorConfig().withFilterClass(RejectOrigins.class))
                    .withFilterClass(TypedTwoOptFilter.class))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
    if (type == LocalSearchType.GUIDED_LOCAL_SEARCH) {
      phase.withGuidedLocalSearchConfig(
          new GuidedLocalSearchConfig()
              .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
              .withFeatureProviderClass(EmptyListFeatures.class)
              .withSampleSize(4)
              .withMaxUnproductiveRounds(3));
    }
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataListSolution.class)
            .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(ZeroListScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    var solver =
        (DefaultSolver<TestdataListSolution>)
            SolverFactory.<TestdataListSolution>create(config).buildSolver();
    TypedTwoOptFilter.CALLS.get().set(0);
    try {
      var result = solver.solve(TestdataListSolution.generateInitializedSolution(4, 1));

      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(TypedTwoOptFilter.CALLS.get()).hasValue(0);
      if (type == LocalSearchType.GUIDED_LOCAL_SEARCH) {
        var decider =
            (GuidedLocalSearchDecider<TestdataListSolution>)
                ((DefaultLocalSearchPhase<TestdataListSolution>) solver.getPhaseList().getFirst())
                    .getDecider();
        assertThat(decider.getStatistics().emptyRounds()).isEqualTo(3);
        assertThat(decider.getStatistics().decisionRounds()).isEqualTo(3);
      }
    } finally {
      TypedTwoOptFilter.CALLS.remove();
    }
  }

  @Test
  void retainedNonDoableCandidatesBypassUserFilterButDoableCandidatesStillUseIt() {
    var policy =
        buildHeuristicConfigPolicy()
            .cloneBuilder()
            .withNonDoableCandidateRetentionEnabled(true)
            .build();
    CountingRejectMoveFilter.CALLS.get().set(0);
    try {
      var noChange = SelectorBasedNoChangeMove.<TestdataSolution>getInstance();
      var retained = filtered(policy, noChange).iterator();
      assertThat(retained.hasNext()).isTrue();
      assertThat(retained.next()).isSameAs(noChange);
      assertThat(CountingRejectMoveFilter.CALLS.get()).hasValue(0);

      AbstractSelectorBasedMove<TestdataSolution> doable = mock(AbstractSelectorBasedMove.class);
      when(doable.isMoveDoable(any())).thenReturn(true);
      assertThat(filtered(policy, doable).iterator().hasNext()).isFalse();
      assertThat(CountingRejectMoveFilter.CALLS.get()).hasValue(1);
    } finally {
      CountingRejectMoveFilter.CALLS.remove();
    }
  }

  @Test
  void policyCopiesPreserveGlsFilteringButNestedConstructionRestoresItsOwnSemantics() {
    var outerPolicy =
        buildHeuristicConfigPolicy()
            .cloneBuilder()
            .withInitializingScoreTrend(
                InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
            .withNonDoableCandidateRetentionEnabled(true)
            .build();
    assertThat(outerPolicy.cloneBuilder().build().isNonDoableCandidateRetentionEnabled()).isTrue();
    assertThat(outerPolicy.copyConfigPolicy().isNonDoableCandidateRetentionEnabled()).isTrue();
    assertThat(
            outerPolicy
                .copyConfigPolicyWithoutNearbySetting()
                .isNonDoableCandidateRetentionEnabled())
        .isTrue();
    assertThat(outerPolicy.copyChildThreadConfigPolicy().isNonDoableCandidateRetentionEnabled())
        .isTrue();
    var captured = new AtomicReference<HeuristicConfigPolicy<TestdataSolution>>();
    var constructionFactory =
        new DefaultConstructionHeuristicPhaseFactory<TestdataSolution>(
            new ConstructionHeuristicPhaseConfig()) {
          @Override
          protected DefaultConstructionHeuristicPhaseBuilder<TestdataSolution> createBuilder(
              HeuristicConfigPolicy<TestdataSolution> policy,
              SolverTermination<TestdataSolution> termination,
              int phaseIndex,
              boolean lastInitializingPhase,
              EntityPlacer<TestdataSolution> placer) {
            captured.set(policy);
            return null;
          }
        };
    constructionFactory.getBuilder(0, false, outerPolicy, new BasicPlumbingTermination<>(false));
    assertThat(captured.get().isNonDoableCandidateRetentionEnabled()).isFalse();
    CountingRejectMoveFilter.CALLS.get().set(0);
    try {
      // Construction heuristics deliberately offer no-op assignments to a user's filter.
      assertThat(
              filtered(captured.get(), SelectorBasedNoChangeMove.getInstance())
                  .iterator()
                  .hasNext())
          .isFalse();
      assertThat(CountingRejectMoveFilter.CALLS.get()).hasValue(1);
    } finally {
      CountingRejectMoveFilter.CALLS.remove();
    }
  }

  private static MoveSelector<TestdataSolution> filtered(
      HeuristicConfigPolicy<TestdataSolution> policy, Move<TestdataSolution> move) {
    var factory =
        new AbstractMoveSelectorFactory<TestdataSolution, ChangeMoveSelectorConfig>(
            new ChangeMoveSelectorConfig().withFilterClass(CountingRejectMoveFilter.class)) {
          @Override
          protected MoveSelector<TestdataSolution> buildBaseMoveSelector(
              HeuristicConfigPolicy<TestdataSolution> policy,
              SelectionCacheType cacheType,
              boolean randomSelection) {
            MoveSelector<TestdataSolution> selector = mock(MoveSelector.class);
            when(selector.iterator()).thenAnswer(ignored -> List.of(move).iterator());
            when(selector.getSize()).thenReturn(1L);
            when(selector.getCacheType()).thenReturn(SelectionCacheType.JUST_IN_TIME);
            return selector;
          }
        };
    return factory.buildMoveSelector(
        policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
  }

  public static class CountingRejectMoveFilter
      implements SelectionFilter<TestdataSolution, AbstractSelectorBasedMove<TestdataSolution>> {
    private static final ThreadLocal<AtomicInteger> CALLS =
        ThreadLocal.withInitial(AtomicInteger::new);

    @Override
    public boolean accept(
        ScoreDirector<TestdataSolution> director,
        AbstractSelectorBasedMove<TestdataSolution> move) {
      CALLS.get().incrementAndGet();
      return false;
    }
  }

  public static class TypedTwoOptFilter
      implements SelectionFilter<
          TestdataListSolution, SelectorBasedTwoOptListMove<TestdataListSolution>> {
    private static final ThreadLocal<AtomicInteger> CALLS =
        ThreadLocal.withInitial(AtomicInteger::new);

    @Override
    public boolean accept(
        ScoreDirector<TestdataListSolution> director,
        SelectorBasedTwoOptListMove<TestdataListSolution> move) {
      CALLS.get().incrementAndGet();
      return true;
    }
  }

  public static class RejectOrigins
      implements SelectionFilter<TestdataListSolution, TestdataListValue> {
    @Override
    public boolean accept(ScoreDirector<TestdataListSolution> director, TestdataListValue value) {
      return false;
    }
  }

  public static class ZeroListScore
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class EmptyListFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataListSolution, String> {
    @Override
    public void extractFeatures(
        TestdataListSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {}

    @Override
    public GuidedLocalSearchFeatureSession<TestdataListSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(TestdataListSolution solution) {}

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {}
      };
    }
  }
}
