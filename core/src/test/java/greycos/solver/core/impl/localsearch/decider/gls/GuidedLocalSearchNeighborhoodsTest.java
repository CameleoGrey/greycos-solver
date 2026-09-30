package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.OrCompositeTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.neighborhood.Neighborhood;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;
import greycos.solver.core.preview.api.neighborhood.stream.joiner.NeighborhoodsJoiners;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GuidedLocalSearchNeighborhoodsTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void temporaryCandidatesDoNotRefreshCachedFiltersButEachCommittedMoveDoes(String threads) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ZeroScore.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount(threads)
            .withRandomSeed(0L)
            .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                    .withGuidedLocalSearchConfig(
                        new GuidedLocalSearchConfig()
                            .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                            .withFeatureProviderClass(ConstantFeatures.class)
                            .withSampleSize(1000))
                    .withMoveProviderClass(CachedNeighborhood.class)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var filterCounts = new ArrayList<Integer>();
    var changedCounts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            if (scope.getStepIndex() > 0) {
              filterCounts.add(CachedNeighborhood.FILTER_CALLS.get().get());
            }
            CachedNeighborhood.FILTER_CALLS.get().set(0);
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            changedCounts.add(
                scope.getWorkingSolution().getEntityList().stream()
                    .filter(entity -> entity.getValue().getCode().equals("changed"))
                    .count());
            assertThat(((LocalSearchStepScope<TestdataSolution>) scope).getSelectedMoveCount())
                .isEqualTo(1001L);
            assertThat(scope.getScore().raw()).isEqualTo(SimpleScore.ZERO);
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            filterCounts.add(CachedNeighborhood.FILTER_CALLS.get().get());
          }
        });

    try {
      var best = solver.solve(problem(100));

      assertThat(filterCounts).containsExactly(1, 1, 1);
      assertThat(changedCounts).containsExactly(1L, 2L, 3L);
      assertThat(best.getScore()).isEqualTo(SimpleScore.ZERO);
      var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
      var statistics =
          ((GuidedLocalSearchDecider<TestdataSolution>) phase.getDecider()).getStatistics();
      assertThat(statistics.penaltyUpdates()).isEqualTo(3);
      assertThat(statistics.decisionRounds()).isEqualTo(6);
    } finally {
      CachedNeighborhood.FILTER_CALLS.remove();
    }
  }

  @Test
  void pendingMoveIsTemporaryUntilTheDecisionReturns() {
    try (var context = new DecisionContext()) {
      var move = context.change();
      context.solverScope.setPendingMove(move);

      context.decider.decideNextStep(context.stepScope);

      assertThat(context.stepScope.getStep()).isSameAs(move);
      assertThat(context.stepScope.getSelectedMoveCount()).isEqualTo(1L);
      assertThat(context.stepScope.getAcceptedMoveCount()).isEqualTo(1L);
      assertThat(context.stepScope.getScore().raw()).isEqualTo(SimpleScore.ZERO);
      context.assertUndoneThenCommitIsTracked();
    }
  }

  @Test
  void earlyTerminationRestoresCommittedChangeTracking() {
    try (var context = new DecisionContext()) {
      when(context.termination.isPhaseTerminated(context.phaseScope))
          .thenAnswer(
              ignored -> {
                // Exercise notification suppression even when no candidate is evaluated.
                context.director.getNeighborhoodNotifier().accept(context.entity());
                return true;
              });

      context.decider.decideNextStep(context.stepScope);

      assertThat(context.stepScope.getStep()).isNull();
      assertThat(context.stepScope.getNoStepReason())
          .isEqualTo(LocalSearchStepScope.NoStepReason.TERMINATED);
      assertThat(context.stepScope.getSelectedMoveCount()).isZero();
      context.assertUndoneThenCommitIsTracked();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failingEvaluationRestoresTrackingAndPreservesFailure(boolean pending) {
    try (var context = new DecisionContext()) {
      var failure = new IllegalStateException("candidate evaluation failed");
      context.scoreFailure = failure;
      var failingMove = context.change();
      if (pending) {
        context.solverScope.setPendingMove(failingMove);
      } else {
        when(context.repository.iterator()).thenReturn(List.of(failingMove).iterator());
      }

      assertThatThrownBy(() -> context.decider.decideNextStep(context.stepScope)).isSameAs(failure);

      context.scoreFailure = null;
      assertThat(context.stepScope.getStep()).isNull();
      context.assertUndoneThenCommitIsTracked();
    }
  }

  private static TestdataSolution problem(int entityCount) {
    var solution = TestdataSolution.generateSolution(2, entityCount);
    solution.getValueList().getFirst().setCode("eligible");
    solution.getValueList().getLast().setCode("changed");
    solution.getEntityList().forEach(entity -> entity.setValue(solution.getValueList().getFirst()));
    return solution;
  }

  private static final class DecisionContext implements AutoCloseable {
    private RuntimeException scoreFailure;
    private final InnerScoreDirector<TestdataSolution, SimpleScore> director =
        new EasyScoreDirectorFactory<>(
                TestdataSolution.buildSolutionDescriptor(),
                (EasyScoreCalculator<TestdataSolution, SimpleScore>)
                    solution -> {
                      if (scoreFailure != null) {
                        throw scoreFailure;
                      }
                      return SimpleScore.ZERO;
                    },
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector();

    @SuppressWarnings("unchecked")
    private final NeighborhoodsBasedMoveRepository<TestdataSolution> repository =
        mock(NeighborhoodsBasedMoveRepository.class);

    @SuppressWarnings("unchecked")
    private final PhaseTermination<TestdataSolution> termination =
        mock(OrCompositeTermination.class);

    private final SolverScope<TestdataSolution> solverScope = new SolverScope<>();
    private final LocalSearchPhaseScope<TestdataSolution> phaseScope =
        new LocalSearchPhaseScope<>(solverScope, 0);
    private final LocalSearchStepScope<TestdataSolution> stepScope =
        new LocalSearchStepScope<>(phaseScope);
    private final GuidedLocalSearchDecider<TestdataSolution> decider =
        new GuidedLocalSearchDecider<>(
            "",
            termination,
            repository,
            new ConstantFeatures(),
            GuidedLocalSearchGuidanceMode.FIXED_TARGET,
            BigDecimal.ONE,
            0,
            List.of(),
            100,
            100,
            GuidedLocalSearchSearchMode.SAMPLED,
            1,
            1,
            false,
            Thread::new,
            0,
            1);

    private DecisionContext() {
      director.setWorkingSolution(problem(1));
      director.setMoveRepository(repository);
      director.setAllChangesWillBeUndoneBeforeStepEnds(false);
      solverScope.setScoreDirector(director);
      var score = director.calculateScore();
      solverScope.setBestScore(score);
      phaseScope.getLastCompletedStepScope().setScore(score);
      decider.solvingStarted(solverScope);
      decider.phaseStarted(phaseScope);
      decider.stepStarted(stepScope);
    }

    private TestdataEntity entity() {
      return director.getWorkingSolution().getEntityList().getFirst();
    }

    private Move<TestdataSolution> change() {
      var variable =
          director
              .getSolutionDescriptor()
              .getMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      return Moves.change(
          variable, entity(), director.getWorkingSolution().getValueList().getLast());
    }

    private void assertUndoneThenCommitIsTracked() {
      assertThat(entity().getValue().getCode()).isEqualTo("eligible");
      assertThat(director.getWorkingSolution().getScore()).isEqualTo(SimpleScore.ZERO);
      verify(repository, never()).update(any());

      director.executeMove(change());

      assertThat(entity().getValue().getCode()).isEqualTo("changed");
      verify(repository).update(entity());
    }

    @Override
    public void close() {
      try {
        decider.phaseEnded(phaseScope);
      } finally {
        director.close();
      }
    }
  }

  public static class CachedNeighborhood implements NeighborhoodProvider<TestdataSolution> {
    private static final ThreadLocal<AtomicInteger> FILTER_CALLS =
        ThreadLocal.withInitial(AtomicInteger::new);

    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<TestdataSolution> builder) {
      var variable =
          builder
              .getSolutionMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      return builder
          .add(
              factory -> {
                var entities =
                    factory
                        .forEach(TestdataEntity.class, false)
                        .filter(
                            (view, entity) -> {
                              FILTER_CALLS.get().incrementAndGet();
                              return entity.getValue().getCode().equals("eligible");
                            });
                return factory
                    .pick(entities)
                    .pick(
                        factory.forEach(TestdataValue.class, false),
                        NeighborhoodsJoiners.filtering(
                            (view, entity, value) -> entity.getValue() != value))
                    .asMove((view, entity, value) -> Moves.change(variable, entity, value));
              })
          .build();
    }
  }

  public static class ZeroScore implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  /** A constant feature deliberately forces a full sample and a penalty update on every step. */
  public static class ConstantFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, String> {
    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {
      consumer.accept("constant", 1L);
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, String> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        @Override
        public void resetWorkingSolution(TestdataSolution solution) {}

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {
          updater.accept("constant", 1L);
        }
      };
    }
  }
}
