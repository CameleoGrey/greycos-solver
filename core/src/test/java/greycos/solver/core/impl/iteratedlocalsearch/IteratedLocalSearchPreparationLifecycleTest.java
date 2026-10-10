package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.EmptyMoves;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.LandscapeScoreCalculator;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Exercises the shared shutdown timeout with an intentionally noncooperative replay.")
@Timeout(30)
class IteratedLocalSearchPreparationLifecycleTest {
  private static final int ATTEMPT_LIMIT = 6;
  private static final int ITERATIONS = 30;
  private static final Map<String, Observations> OBSERVATIONS = new ConcurrentHashMap<>();

  @ParameterizedTest
  @CsvSource({
    "NONE,true",
    "1,true",
    "2,true",
    "4,true",
    "NONE,false",
    "1,false",
    "2,false",
    "4,false"
  })
  void freshRequestContextsStayBoundedAcrossAcceptedRejectedAndNoChangeShakes(
      String workers, boolean improving) {
    var observations = new Observations();
    var problem = problem(observations, improving);
    try {
      var solver =
          solver(
              workers,
              new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(FreshRequests.class));
      observe(solver, observations);
      var result = solver.solve(problem);
      assertThat(observations.scope.getCompletedIterations()).isEqualTo(ITERATIONS);
      assertThat(observations.live).hasValue(0);
      assertThat(observations.opened.get()).isGreaterThan(ATTEMPT_LIMIT);
      assertThat(observations.closed).hasValue(observations.opened.get());
      assertThat(observations.maximumLive.get()).isLessThanOrEqualTo(ATTEMPT_LIMIT);
      assertThat(observations.shakes).isEqualTo(ITERATIONS);
      assertThat(observations.selectorStarts).hasValue(1);
      assertThat(observations.selectorEnds).hasValue(1);
      if (improving) assertThat(observations.scope.getAcceptedIterations()).isEqualTo(ITERATIONS);
      else {
        assertThat(observations.scope.getRejectedIterations()).isEqualTo(ITERATIONS / 2);
        assertThat(observations.scope.getNoChangeCount()).isEqualTo(ITERATIONS / 2);
      }
      assertThat(result.getScore())
          .isEqualTo(new LandscapeScoreCalculator().calculateScore(result));
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @Test
  void nativeMultistageReopensPreparationSessionsAndKeepsTheSameOrderedTraceWithWorkers() {
    List<Long> expectedRandom = null;
    for (var workers : List.of("NONE", "1", "2", "4")) {
      var observations = new Observations();
      var problem = problem(observations, true);
      try {
        var selector =
            new MultistageMoveSelectorConfig()
                .withEntityClass(TestdataEntity.class)
                .withVariableName("value")
                .withStageProviderClass(ReopeningStages.class)
                .withCandidateCountLimit(1)
                .withProbeCountLimit(4);
        var solver = solver(workers, selector);
        observe(solver, observations);
        var result = solver.solve(problem);
        assertThat(observations.scope.getAcceptedIterations()).isEqualTo(ITERATIONS);
        assertThat(observations.opened).hasValue(ITERATIONS);
        assertThat(observations.closed).hasValue(ITERATIONS);
        assertThat(observations.live).hasValue(0);
        assertThat(observations.maximumLive).hasValue(1);
        assertThat(observations.committedValues)
            .containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, ITERATIONS).boxed().toList());
        if (expectedRandom == null) expectedRandom = List.copyOf(observations.randomTrace);
        else assertThat(observations.randomTrace).containsExactlyElementsOf(expectedRandom);
        assertThat(result.getScore()).isEqualTo(SimpleScore.of(ITERATIONS));
      } finally {
        OBSERVATIONS.remove(problem.getCode());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void preparationFailureRemainsPrimaryAndCleanupFailureIsSuppressedExactlyOnce(String workers) {
    var observations = new Observations();
    observations.preparationFailure = new IllegalStateException("preparation failed");
    observations.cleanupFailure = new IllegalStateException("preparation cleanup failed");
    var problem = problem(observations, true);
    try {
      var solver =
          solver(
              workers,
              new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(FreshRequests.class));
      assertThatThrownBy(() -> solver.solve(problem))
          .isSameAs(observations.preparationFailure)
          .satisfies(
              failure ->
                  assertThat(failure.getSuppressed()).containsExactly(observations.cleanupFailure));
      assertThat(observations.opened).hasValue(1);
      assertThat(observations.closed).hasValue(1);
      assertThat(observations.live).hasValue(0);
      solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  @Test
  void timedOutReplayRetainsPreparationContextsUntilDeferredCleanupCanJoinWorkers()
      throws Exception {
    var observations = new Observations();
    observations.blockReplay = true;
    var problem = problem(observations, true);
    int previousTimeout = ThreadUtils.getDefaultShutdownTimeout();
    ThreadUtils.setDefaultShutdownTimeout(1);
    var coordinator = Executors.newSingleThreadExecutor();
    var solver =
        solver(
            "2", new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(FreshRequests.class));
    try {
      var solve = coordinator.submit(() -> solver.solve(problem));
      assertThat(observations.replayEntered.await(5, TimeUnit.SECONDS)).isTrue();
      solver.terminateEarly();
      assertThatThrownBy(() -> solve.get(10, TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class);
      assertThat(observations.closed).hasValue(0);
      assertThat(observations.live.get()).isPositive();
      assertThat(observations.selectorEnds).hasValue(0);
      assertThatThrownBy(solver.getSolverScope().getWorkerRegistry()::assertNoActiveWorkers)
          .isInstanceOf(IllegalStateException.class);
      observations.releaseReplay.countDown();
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(5))
          .untilAsserted(solver.getSolverScope().getWorkerRegistry()::assertNoActiveWorkers);
      solver.getSolverScope().getWorkerRegistry().runDeferredCleanup();
      assertThat(observations.closed).hasValue(observations.opened.get());
      assertThat(observations.live).hasValue(0);
      assertThat(observations.selectorEnds).hasValue(1);
    } finally {
      observations.releaseReplay.countDown();
      coordinator.shutdownNow();
      assertThat(coordinator.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
      ThreadUtils.setDefaultShutdownTimeout(previousTimeout);
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  private static DefaultSolver<TestdataSolution> solver(
      String workers, MoveSelectorConfig<?> perturbation) {
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(EmptyMoves.class)))
            .withPerturbationMoveSelectorConfig(perturbation)
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(ATTEMPT_LIMIT)
            .withEpisodeCandidateAttemptLimit(1)
            .withIterationCountLimit(ITERATIONS);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
            .withMoveThreadCount(workers)
            .withRandomSeed(0L)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withThreadFactoryClass(ReplayThreadFactory.class)
            .withPhases(phase);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static TestdataSolution problem(Observations observations, boolean improving) {
    var problem = new TestdataSolution(UUID.randomUUID().toString());
    var values = new ArrayList<TestdataValue>();
    int count = improving ? ITERATIONS + 2 : 2;
    for (int i = 0; i < count; i++) values.add(new TestdataValue(i + ":" + (improving ? i : 0)));
    problem.setValueList(values);
    problem.setEntityList(List.of(new TestdataEntity("entity", values.getFirst())));
    OBSERVATIONS.put(problem.getCode(), observations);
    return problem;
  }

  private static void observe(DefaultSolver<TestdataSolution> solver, Observations observations) {
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            var ils = (IteratedLocalSearchStepScope<TestdataSolution>) step;
            if (ils.getOrigin() == IteratedLocalSearchStepScope.Origin.PERTURBATION
                && ils.getPhaseScope().getCompletedIterations() != observations.lastIteration) {
              assertThat(observations.live).hasValue(0);
              observations.lastIteration = ils.getPhaseScope().getCompletedIterations();
              observations.shakes++;
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            observations.committedValues.add(index(step.getWorkingSolution()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            observations.scope = (IteratedLocalSearchPhaseScope<TestdataSolution>) scope;
          }
        });
  }

  private static int index(TestdataSolution solution) {
    return solution.getValueList().indexOf(solution.getEntityList().getFirst().getValue());
  }

  private static Observations observations(TestdataSolution solution) {
    return OBSERVATIONS.get(solution.getCode());
  }

  private static final class Observations {
    final AtomicInteger opened = new AtomicInteger();
    final AtomicInteger closed = new AtomicInteger();
    final AtomicInteger live = new AtomicInteger();
    final AtomicInteger maximumLive = new AtomicInteger();
    final AtomicInteger selectorStarts = new AtomicInteger();
    final AtomicInteger selectorEnds = new AtomicInteger();
    final List<Integer> committedValues = new ArrayList<>();
    final List<Long> randomTrace = new ArrayList<>();
    final CountDownLatch replayEntered = new CountDownLatch(1);
    final CountDownLatch releaseReplay = new CountDownLatch(1);
    IteratedLocalSearchPhaseScope<TestdataSolution> scope;
    IllegalStateException preparationFailure;
    IllegalStateException cleanupFailure;
    boolean blockReplay;
    long lastIteration = -1;
    int shakes;

    void opened() {
      opened.incrementAndGet();
      maximumLive.accumulateAndGet(live.incrementAndGet(), Math::max);
    }

    void closed() {
      closed.incrementAndGet();
      live.decrementAndGet();
      if (cleanupFailure != null) throw cleanupFailure;
    }
  }

  public static final class FreshRequests
      implements MoveIteratorFactory<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public void phaseStarted(ScoreDirector<TestdataSolution> director) {
      observations(director.getWorkingSolution()).selectorStarts.incrementAndGet();
    }

    @Override
    public void phaseEnded(ScoreDirector<TestdataSolution> director) {
      observations(director.getWorkingSolution()).selectorEnds.incrementAndGet();
    }

    @Override
    public long getSize(ScoreDirector<TestdataSolution> director) {
      return Long.MAX_VALUE;
    }

    @Override
    public Iterator<Move<TestdataSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataSolution> director) {
      return createRandomMoveIterator(director, null);
    }

    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      return new Iterator<>() {
        int attempt;

        @Override
        public boolean hasNext() {
          return true;
        }

        @Override
        public Move<TestdataSolution> next() {
          return new FreshRequest(observations(director.getWorkingSolution()), ++attempt % 3 != 0);
        }
      };
    }
  }

  private static final class FreshRequest implements PreparableMove<TestdataSolution> {
    private final Observations observations;
    private final boolean empty;
    private boolean open;

    FreshRequest(Observations observations, boolean empty) {
      this.observations = observations;
      this.empty = empty;
    }

    @Override
    public <Score_ extends Score<Score_>> PreparedMoveEvaluation<TestdataSolution, Score_> prepare(
        InnerScoreDirector<TestdataSolution, Score_> director,
        Runnable checkTermination,
        boolean assertFromScratch,
        BiConsumer<SolutionView<TestdataSolution>, Move<TestdataSolution>> finalStateConsumer) {
      assertThat(open).isFalse();
      open = true;
      observations.opened();
      if (observations.preparationFailure != null) throw observations.preparationFailure;
      if (empty)
        return new PreparedMoveEvaluation<>(PreparedMoveEvaluation.Status.EMPTY, null, null, 0L);
      var solution = director.getWorkingSolution();
      var variable =
          director
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      Move<TestdataSolution> move =
          new ReplayMove(
              new ChangeMove<>(
                  variable,
                  solution.getEntityList().getFirst(),
                  solution
                      .getValueList()
                      .get((index(solution) + 1) % solution.getValueList().size())),
              observations);
      long before = director.getCalculationCount();
      var score =
          director.executeTemporaryMove(
              move, view -> finalStateConsumer.accept(view, move), assertFromScratch);
      return new PreparedMoveEvaluation<>(
          PreparedMoveEvaluation.Status.EVALUATED,
          move,
          score,
          director.getCalculationCount() - before);
    }

    @Override
    public void closeEvaluationContext(InnerScoreDirector<TestdataSolution, ?> director) {
      assertThat(open).isTrue();
      open = false;
      observations.closed();
    }

    @Override
    public void execute(MutableSolutionView<TestdataSolution> view) {
      throw new UnsupportedOperationException();
    }
  }

  private record ReplayMove(Move<TestdataSolution> delegate, Observations observations)
      implements Move<TestdataSolution> {
    @Override
    public void execute(MutableSolutionView<TestdataSolution> view) {
      boolean interrupted = false;
      try {
        if (observations.blockReplay
            && Thread.currentThread().getName().startsWith("prepared-replay-")) {
          observations.replayEntered.countDown();
          while (observations.releaseReplay.getCount() != 0L) {
            try {
              observations.releaseReplay.await();
            } catch (InterruptedException ignored) {
              interrupted = true;
            }
          }
          assertThat(observations.closed).hasValue(0);
        }
        delegate.execute(view);
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    }

    @Override
    public Move<TestdataSolution> rebase(Lookup lookup) {
      return new ReplayMove(delegate.rebase(lookup), observations);
    }
  }

  public static final class ReopeningStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    private TestdataSolution solution;

    @Override
    public void initialize(TestdataSolution solution) {
      this.solution = solution;
      observations(solution).opened();
    }

    @Override
    public long getCandidateCount() {
      return 1L;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      assertThat(candidateIndex).isZero();
      observations(solution).randomTrace.add(random.nextLong());
      return List.of(
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.assign(
                      solution.getEntityList().getFirst(),
                      solution
                          .getValueList()
                          .get((index(solution) + 1) % solution.getValueList().size()))));
    }

    @Override
    public void phaseEnded() {
      observations(solution).closed();
      solution = null;
    }
  }

  public static final class ReplayThreadFactory implements java.util.concurrent.ThreadFactory {
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, "prepared-replay-" + sequence.incrementAndGet());
    }
  }
}
