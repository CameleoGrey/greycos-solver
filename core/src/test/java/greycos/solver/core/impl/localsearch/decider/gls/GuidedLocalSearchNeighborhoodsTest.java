package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
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
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class GuidedLocalSearchNeighborhoodsTest {

  private static final ThreadLocal<SessionLifecycle> SESSION_LIFECYCLE = new ThreadLocal<>();

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
                            .withFeatureProviderClass(EligibleCountFeatures.class)
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

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void constantFeatureStopsAtRetryLimitWithoutCommittingAnEqualScoreMove(String threads) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ZeroScore.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
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
                            .withSampleSize(4)
                            .withMaxPenaltyUpdatesPerStep(3))
                    .withMoveProviderClass(CachedNeighborhood.class)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var attempt = new AtomicReference<LocalSearchStepScope<TestdataSolution>>();
    var committed = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            attempt.set((LocalSearchStepScope<TestdataSolution>) scope);
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            committed.incrementAndGet();
          }
        });

    try {
      var best = solver.solve(problem(4));

      assertThat(committed).hasValue(0);
      assertThat(attempt.get().getStep()).isNull();
      assertThat(attempt.get().getNoStepReason())
          .isEqualTo(LocalSearchStepScope.NoStepReason.GUIDED_RETRY_EXHAUSTED);
      assertThat(attempt.get().getSelectedMoveCount()).isEqualTo(16L);
      assertThat(attempt.get().getAcceptedMoveCount()).isZero();
      assertThat(best.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue().getCode()).isEqualTo("eligible"));
      assertThat(best.getScore()).isEqualTo(SimpleScore.ZERO);
      var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
      var decider = (GuidedLocalSearchDecider<TestdataSolution>) phase.getDecider();
      assertThat(decider.getStatistics())
          .isEqualTo(new GuidedLocalSearchDecider.Statistics(4, 3, 0, 0));
      assertThat(decider.getControllerDiagnostics().retryExhaustions()).isEqualTo(1);
    } finally {
      CachedNeighborhood.FILTER_CALLS.remove();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void featureFailureClosesEverySessionAndWorkerAndAllowsSolverReuse(String threads)
      throws InterruptedException {
    assertFailureCleanupAndSolverReuse(threads, FailurePoint.CANDIDATE);
  }

  static Stream<Arguments> partialStartupFailures() {
    return Stream.of(
        Arguments.of("NONE", FailurePoint.REPOSITORY_START),
        Arguments.of("2", FailurePoint.REPOSITORY_START),
        Arguments.of("2", FailurePoint.WORKER_START));
  }

  @ParameterizedTest
  @MethodSource("partialStartupFailures")
  void partialPhaseStartupFailureCleansInitializedResourcesAndAllowsSolverReuse(
      String threads, FailurePoint failurePoint) throws InterruptedException {
    assertFailureCleanupAndSolverReuse(threads, failurePoint);
  }

  private static void assertFailureCleanupAndSolverReuse(String threads, FailurePoint failurePoint)
      throws InterruptedException {
    var lifecycle = new SessionLifecycle(failurePoint);
    SESSION_LIFECYCLE.set(lifecycle);
    try {
      var config =
          new SolverConfig()
              .withSolutionClass(TestdataSolution.class)
              .withEntityClasses(TestdataEntity.class)
              .withEasyScoreCalculatorClass(ZeroScore.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withMoveThreadCount(threads)
              .withThreadFactoryClass(RecordingThreadFactory.class)
              .withRandomSeed(0L)
              .withPreviewFeature(PreviewFeature.NEIGHBORHOODS)
              .withPhases(
                  new LocalSearchPhaseConfig()
                      .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                      .withGuidedLocalSearchConfig(
                          new GuidedLocalSearchConfig()
                              .withGuidanceMode(GuidedLocalSearchGuidanceMode.FIXED_TARGET)
                              .withFeatureProviderClass(FailingFeatures.class)
                              .withSampleSize(4))
                      .withMoveProviderClass(FailingNeighborhood.class)
                      .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
      var solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      var committed = new ArrayList<Long>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              committed.add(
                  scope.getWorkingSolution().getEntityList().stream()
                      .filter(entity -> entity.getValue().getCode().equals("changed"))
                      .count());
            }

            @Override
            public void solvingError(SolverScope<TestdataSolution> scope, Throwable failure) {
              if (failurePoint == FailurePoint.REPOSITORY_START) {
                // This failure happens before a feature session exists. A failing listener must
                // not prevent the repository's partially initialized phase from being cleaned up.
                throw lifecycle.cleanupFailure;
              }
            }
          });
      var problem = problem(4);

      var thrown = catchThrowable(() -> solver.solve(problem));

      if (threads.equals("NONE") || failurePoint == FailurePoint.REPOSITORY_START) {
        assertThat(thrown).isSameAs(lifecycle.evaluationFailure);
      } else {
        assertThat(thrown).hasCause(lifecycle.evaluationFailure);
      }
      assertThat(thrown.getSuppressed()).contains(lifecycle.cleanupFailure);
      assertThat(lifecycle.failedOperations).hasPositiveValue();
      assertThat(committed).isEmpty();
      int sessionsPerSolve = threads.equals("NONE") ? 1 : 3;
      int sessionCount =
          switch (failurePoint) {
            case CANDIDATE -> sessionsPerSolve;
            case WORKER_START -> 1;
            case REPOSITORY_START -> 0;
          };
      assertThat(lifecycle.sessionCloseCounts)
          .hasSize(sessionCount)
          .allSatisfy(count -> assertThat(count).hasValue(1));
      int workerCount = threads.equals("NONE") ? 0 : 2;
      int startedWorkerCount = failurePoint == FailurePoint.REPOSITORY_START ? 0 : workerCount;
      assertThat(lifecycle.workers).hasSize(startedWorkerCount);
      assertWorkersStopped(lifecycle.workers);
      assertThat(solver.isSolving()).isFalse();
      assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
      assertThat(problem.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue().getCode()).isEqualTo("eligible"));

      lifecycle.fail.set(false);
      var best = solver.solve(problem(4));

      assertThat(committed).containsExactly(1L);
      assertThat(best.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(lifecycle.sessionCloseCounts)
          .hasSize(sessionCount + sessionsPerSolve)
          .allSatisfy(count -> assertThat(count).hasValue(1));
      assertThat(lifecycle.workers).hasSize(startedWorkerCount + workerCount);
      assertWorkersStopped(lifecycle.workers);
    } finally {
      for (var worker : lifecycle.workers) worker.interrupt();
      for (var worker : lifecycle.workers) worker.join(2000);
      CachedNeighborhood.FILTER_CALLS.remove();
      SESSION_LIFECYCLE.remove();
    }
  }

  private static void assertWorkersStopped(List<Thread> workers) throws InterruptedException {
    for (var worker : workers) {
      // Executor termination may precede the last return from Thread.run().
      worker.join(2000);
      assertThat(worker.isAlive()).as("GLS worker %s", worker.getName()).isFalse();
    }
  }

  @Test
  void failingRepositoryCleanupRunsOnceAndIsSuppressedOnTheOriginalFailure() {
    var context = new DecisionContext();
    var original = new IllegalStateException("candidate evaluation failed");
    var cleanup = new IllegalStateException("repository cleanup failed");
    try (context) {
      doThrow(cleanup).when(context.repository).phaseEnded(context.phaseScope);

      context.decider.solvingError(context.solverScope, original);
      context.decider.solvingError(context.solverScope, original);
      context.decider.phaseEnded(context.phaseScope);
    }

    verify(context.repository, times(1)).phaseEnded(context.phaseScope);
    assertThat(original.getSuppressed()).containsExactly(cleanup);
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
            GuidedLocalSearchFeatureComposition.CUSTOM,
            false,
            BigDecimal.ONE,
            0,
            List.of(),
            100,
            100,
            64,
            8,
            64,
            GuidedLocalSearchSearchMode.SAMPLED,
            1,
            1,
            false,
            null,
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
                              return accepts(entity);
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

    protected boolean accepts(TestdataEntity entity) {
      return entity.getValue().getCode().equals("eligible");
    }
  }

  public static class FailingNeighborhood extends CachedNeighborhood {
    private final SessionLifecycle lifecycle = SESSION_LIFECYCLE.get();

    @Override
    protected boolean accepts(TestdataEntity entity) {
      if (lifecycle.fail.get() && lifecycle.failurePoint == FailurePoint.REPOSITORY_START) {
        lifecycle.failedOperations.incrementAndGet();
        throw lifecycle.evaluationFailure;
      }
      return super.accepts(entity);
    }
  }

  public static class ZeroScore implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  private enum FailurePoint {
    CANDIDATE,
    REPOSITORY_START,
    WORKER_START
  }

  private static final class SessionLifecycle {
    private final FailurePoint failurePoint;
    private final Thread coordinator = Thread.currentThread();
    private final IllegalStateException evaluationFailure =
        new IllegalStateException("feature candidate evaluation failed");
    private final IllegalStateException cleanupFailure =
        new IllegalStateException("feature session cleanup failed");
    private final AtomicBoolean fail = new AtomicBoolean(true);
    private final AtomicInteger failedOperations = new AtomicInteger();
    private final List<AtomicInteger> sessionCloseCounts = new CopyOnWriteArrayList<>();
    private final List<Thread> workers = new CopyOnWriteArrayList<>();

    private SessionLifecycle(FailurePoint failurePoint) {
      this.failurePoint = failurePoint;
    }
  }

  public static class RecordingThreadFactory implements ThreadFactory {
    private final SessionLifecycle lifecycle = SESSION_LIFECYCLE.get();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "gls-feature-lifecycle-worker");
      lifecycle.workers.add(thread);
      return thread;
    }
  }

  public static class FailingFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, Integer> {
    private final SessionLifecycle lifecycle = SESSION_LIFECYCLE.get();
    private final EligibleCountFeatures delegate = new EligibleCountFeatures();

    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<Integer> consumer) {
      delegate.extractFeatures(solution, consumer);
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, Integer> newSession() {
      if (lifecycle.fail.get()
          && lifecycle.failurePoint == FailurePoint.WORKER_START
          && Thread.currentThread() != lifecycle.coordinator) {
        lifecycle.failedOperations.incrementAndGet();
        throw lifecycle.evaluationFailure;
      }
      var session = delegate.newSession();
      var closeCount = new AtomicInteger();
      lifecycle.sessionCloseCounts.add(closeCount);
      boolean coordinatorSession = Thread.currentThread() == lifecycle.coordinator;
      return new GuidedLocalSearchFeatureSession<>() {
        private TestdataSolution solution;

        @Override
        public void resetWorkingSolution(TestdataSolution solution) {
          this.solution = solution;
          session.resetWorkingSolution(solution);
        }

        @Override
        public void beforeVariableChanged(Object entity, String variableName) {
          session.beforeVariableChanged(entity, variableName);
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          session.afterVariableChanged(entity, variableName);
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<Integer> updater) {
          if (lifecycle.fail.get()
              && lifecycle.failurePoint == FailurePoint.CANDIDATE
              && solution.getEntityList().stream()
                  .anyMatch(entity -> !EligibleCountFeatures.isEligible(entity))) {
            lifecycle.failedOperations.incrementAndGet();
            throw lifecycle.evaluationFailure;
          }
          session.flushChanges(updater);
        }

        @Override
        public void close() {
          closeCount.incrementAndGet();
          session.close();
          if (coordinatorSession && lifecycle.fail.get()) throw lifecycle.cleanupFailure;
        }
      };
    }
  }

  /** Every candidate changes the feature key, making the first retry strictly improve guidance. */
  public static class EligibleCountFeatures
      implements GuidedLocalSearchFeatureProvider<TestdataSolution, Integer> {
    @Override
    public void extractFeatures(
        TestdataSolution solution, GuidedLocalSearchFeatureConsumer<Integer> consumer) {
      int eligibleCount =
          (int) solution.getEntityList().stream().filter(EligibleCountFeatures::isEligible).count();
      consumer.accept(eligibleCount, eligibleCount);
    }

    private static boolean isEligible(TestdataEntity entity) {
      return entity.getValue().getCode().equals("eligible");
    }

    @Override
    public GuidedLocalSearchFeatureSession<TestdataSolution, Integer> newSession() {
      return new GuidedLocalSearchFeatureSession<>() {
        private int eligibleCount;
        private Integer emittedCount;

        @Override
        public void resetWorkingSolution(TestdataSolution solution) {
          eligibleCount =
              (int)
                  solution.getEntityList().stream()
                      .filter(EligibleCountFeatures::isEligible)
                      .count();
          emittedCount = null;
        }

        @Override
        public void beforeVariableChanged(Object entity, String variableName) {
          if (isEligible((TestdataEntity) entity)) eligibleCount--;
        }

        @Override
        public void afterVariableChanged(Object entity, String variableName) {
          if (isEligible((TestdataEntity) entity)) eligibleCount++;
        }

        @Override
        public void flushChanges(GuidedLocalSearchFeatureUpdater<Integer> updater) {
          if (emittedCount != null && emittedCount == eligibleCount) return;
          if (emittedCount != null) updater.remove(emittedCount);
          updater.accept(eligibleCount, eligibleCount);
          emittedCount = eligibleCount;
        }
      };
    }
  }

  /** Equal penalties in the incumbent and every candidate cannot justify a committed move. */
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
