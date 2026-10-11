package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.controller;
import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.RecordingForager;
import greycos.solver.core.impl.islandmodel.resumable.ResumableSearchWorkloads.Timetable;
import greycos.solver.core.impl.islandmodel.resumable.ResumableSearchWorkloads.TimetableWorkload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Observes test-created worker threads and custom forager instances.")
@Timeout(60)
class ResumableSearchLifecycleTest {

  @ParameterizedTest
  @CsvSource({
    "island,1",
    "island,2",
    "partition,1",
    "partition,2",
    "islandEnclosure,1",
    "partitionEnclosure,1"
  })
  void nestedContainersFinishWithinTheSharedWorkerBound(String nesting, int workers)
      throws Exception {
    TrackingThreadFactory.THREADS.clear();
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 8)
            .withThreadFactoryClass(TrackingThreadFactory.class);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    PhaseConfig<?> hierarchy;
    if (nesting.endsWith("Enclosure")) {
      var inner = sessions(search, 3, workers, 2, ResumableSearchCooperationPolicy.NONE);
      hierarchy = nesting.equals("islandEnclosure") ? independentIslands(inner) : partition(inner);
    } else {
      var outer = sessions(search, 2, workers, 2, ResumableSearchCooperationPolicy.NONE);
      outer.setPhaseConfigList(
          List.of(nesting.equals("island") ? independentIslands(search) : partition(search)));
      hierarchy = outer;
    }
    config.withPhases(hierarchy);
    var actual = ResumableSearchRuntimeTest.run(workload, config, 24, false);
    assertThat(actual.steps()).isPositive();
    assertThat(actual.rounds()).isNotEmpty();
    assertThat(TrackingThreadFactory.THREADS).hasSizeLessThanOrEqualTo(workers);
    assertThat(TrackingThreadFactory.THREADS)
        .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
  }

  @Test
  void completedRoundCancellationReturnsAValidBestAndClosesOwnersBeforeReturning()
      throws Exception {
    TrackingThreadFactory.THREADS.clear();
    RecordingForager.INSTANCES.clear();
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "dla", 37, 10_000)
            .withThreadFactoryClass(TrackingThreadFactory.class);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    search.getForagerConfig().setForagerClass(RecordingForager.class);
    config.withPhases(sessions(search, 4, 2, 3, ResumableSearchCooperationPolicy.NONE));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var rounds = new AtomicInteger();
    var events = new AtomicInteger();
    solver.addEventListener(event -> events.incrementAndGet());
    BasicSolution result;
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              if (rounds.incrementAndGet() == 2) solver.terminateEarly();
            })) {
      result = solver.solve(workload.createProblem(24));
    }
    assertThat(rounds).hasValue(2);
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    assertThat(TrackingThreadFactory.THREADS)
        .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
    int finalEvents = events.get();
    var finalSizes =
        RecordingForager.INSTANCES.stream().map(forager -> forager.events.size()).toList();
    assertThat(RecordingForager.INSTANCES)
        .allSatisfy(
            forager -> {
              assertThat(forager.phasesStarted).isEqualTo(1);
              assertThat(forager.phasesEnded).isEqualTo(1);
            });
    assertThat(events).hasValue(finalEvents);
    assertThat(RecordingForager.INSTANCES.stream().map(forager -> forager.events.size()).toList())
        .isEqualTo(finalSizes);
  }

  @Test
  void initializationFailurePreservesTheCauseAndClosesAlreadyStartedOwners() {
    TrackingThreadFactory.THREADS.clear();
    LifecycleIncrementalCalculator.resetControls(3, false);
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 8)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(TrackingThreadFactory.class);
    config.setScoreDirectorFactoryConfig(
        new ScoreDirectorFactoryConfig()
            .withIncrementalScoreCalculatorClass(LifecycleIncrementalCalculator.class));
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(sessions(search, 4, 2, 2, ResumableSearchCooperationPolicy.NONE));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    try {
      assertThatThrownBy(() -> solver.solve(workload.createProblem(24)))
          .hasStackTraceContaining("Deliberate session score initialization failure.");
      assertThat(LifecycleIncrementalCalculator.failureTriggered).isTrue();
      assertThat(TrackingThreadFactory.THREADS)
          .isNotEmpty()
          .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
      LifecycleIncrementalCalculator.failAtInstance = Integer.MAX_VALUE;
      var result = solver.solve(workload.createProblem(24));
      assertThat(result.getScore()).isEqualTo(workload.recompute(result));
      assertThat(TrackingThreadFactory.THREADS)
          .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
    } finally {
      LifecycleIncrementalCalculator.release.countDown();
      LifecycleIncrementalCalculator.failAtInstance = Integer.MAX_VALUE;
    }
  }

  @Test
  void interruptingNestedSearchUnwindsBlockedAtomicScoringAndAllOwners() throws Exception {
    TrackingThreadFactory.THREADS.clear();
    LifecycleIncrementalCalculator.resetControls(Integer.MAX_VALUE, true);
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 1000)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(TrackingThreadFactory.class);
    config.setScoreDirectorFactoryConfig(
        new ScoreDirectorFactoryConfig()
            .withIncrementalScoreCalculatorClass(LifecycleIncrementalCalculator.class));
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    var outer = sessions(search, 2, 1, 2, ResumableSearchCooperationPolicy.NONE);
    outer.setPhaseConfigList(List.of(independentIslands(search)));
    config.withPhases(outer);
    var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
    var failure = new AtomicReference<Throwable>();
    var coordinator =
        new Thread(
            () -> {
              try {
                solver.solve(workload.createProblem(24));
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "resumable-test-coordinator");
    coordinator.start();
    try {
      assertThat(LifecycleIncrementalCalculator.entered.await(10, TimeUnit.SECONDS)).isTrue();
      coordinator.interrupt();
      coordinator.join(10_000);
      assertThat(coordinator.isAlive()).isFalse();
      assertThat(failure.get()).isNotNull();
      assertThat(TrackingThreadFactory.THREADS)
          .hasSize(1)
          .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
    } finally {
      LifecycleIncrementalCalculator.release.countDown();
      coordinator.interrupt();
      coordinator.join(10_000);
      LifecycleIncrementalCalculator.blockSearch = false;
    }
  }

  @Test
  void observerFailureClosesEveryOwnerAndTheSameSolverCanBeUsedAgain() throws Exception {
    TrackingThreadFactory.THREADS.clear();
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 8)
            .withThreadFactoryClass(TrackingThreadFactory.class);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(sessions(search, 4, 2, 2, ResumableSearchCooperationPolicy.NONE));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              throw new IllegalStateException("Deliberate round observer failure.");
            })) {
      assertThatThrownBy(() -> solver.solve(workload.createProblem(24)))
          .hasStackTraceContaining("Deliberate round observer failure.");
    }
    assertThat(TrackingThreadFactory.THREADS)
        .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
    var result = solver.solve(workload.createProblem(24));
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    assertThat(TrackingThreadFactory.THREADS)
        .allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
  }

  @Test
  void repeatedSolveStartsWithFreshGlobalWorkBudget() throws Exception {
    var workload = new BasicWorkload();
    var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 1000);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(
        sessions(search, 4, 2, 3, ResumableSearchCooperationPolicy.NONE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(31)));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var traces = new ArrayList<List<String>>();
    for (int run = 0; run < 2; run++) {
      var trace = new ArrayList<String>();
      var completed = new AtomicInteger();
      try (var ignored =
          ResumableSearchRuntime.observeRounds(
              round ->
                  round
                      .sessions()
                      .forEach(
                          session -> {
                            trace.add(
                                session.path()
                                    + ":"
                                    + session.episode()
                                    + ":"
                                    + session.completedSteps()
                                    + ":"
                                    + session.moveEvaluations()
                                    + ":"
                                    + session.bestScore());
                            completed.addAndGet(session.steps().size());
                          }))) {
        var result = solver.solve(workload.createProblem(24));
        assertThat(result.getScore()).isEqualTo(workload.recompute(result));
      }
      assertThat(completed).hasValue(31);
      traces.add(trace);
    }
    assertThat(traces.getLast()).isEqualTo(traces.getFirst());
  }

  @ParameterizedTest
  @ValueSource(strings = {"moves", "scores"})
  void descendantWorkExhaustsTheSolverBudgetBeforeAFollowingOrdinaryPhase(String unit) {
    RecordingForager.INSTANCES.clear();
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "dla", 37, 1000)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    var laterOrdinary = search.copyConfig();
    laterOrdinary.getForagerConfig().setForagerClass(RecordingForager.class);
    var resumable =
        sessions(search, 2, 1, 1, ResumableSearchCooperationPolicy.NONE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(4));
    config
        .withPhases(resumable, laterOrdinary)
        .withTerminationConfig(
            unit.equals("moves")
                ? new TerminationConfig().withMoveCountLimit(7L)
                : new TerminationConfig().withScoreCalculationCountLimit(7L));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var result = solver.solve(workload.createProblem(24));
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    assertThat(
            unit.equals("moves")
                ? solver.getMoveEvaluationCount()
                : solver.getScoreCalculationCount())
        .isGreaterThanOrEqualTo(7);
    assertThat(RecordingForager.INSTANCES)
        .hasSize(1)
        .allSatisfy(
            forager -> {
              assertThat(forager.phasesStarted).isZero();
              assertThat(forager.stepsEnded).isZero();
            });
  }

  @Test
  void sequentialResumablePhasesEachReceiveTheirOwnInheritedPhaseStepQuota() throws Exception {
    var workload = new BasicWorkload();
    var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 1000);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config
        .withPhases(
            sessions(search, 2, 1, 3, ResumableSearchCooperationPolicy.NONE),
            sessions(
                search.copyConfig(), 2, 1, 3, ResumableSearchCooperationPolicy.RESTART_WEAKEST))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(11));
    var byTopPhase = new HashMap<String, Integer>();
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round ->
                round
                    .sessions()
                    .forEach(
                        session -> {
                          String topPhase = session.path().split("/")[0];
                          byTopPhase.merge(topPhase, session.steps().size(), Integer::sum);
                        }))) {
      var result =
          SolverFactory.<BasicSolution>create(config)
              .buildSolver()
              .solve(workload.createProblem(24));
      assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    }
    assertThat(byTopPhase).hasSize(2);
    assertThat(byTopPhase.values()).containsExactlyInAnyOrder(11, 11);
  }

  @Test
  void problemChangeBeforeTheFirstResumablePhasePreservesOrdinarySolverWork() throws Exception {
    RecordingForager.INSTANCES.clear();
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 1)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT);
    var ordinary = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    ordinary.getForagerConfig().setForagerClass(RecordingForager.class);
    var later =
        ordinary
            .copyConfig()
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(100));
    config
        .withPhases(ordinary, sessions(later, 1, 1, 1, ResumableSearchCooperationPolicy.NONE))
        .withTerminationConfig(new TerminationConfig().withMoveCountLimit(2L));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var submitted = new AtomicBoolean();
    var applied = new AtomicInteger();
    var ordinaryRevisions = new ArrayList<Long>();
    var residentRounds = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> scope) {
            if (scope instanceof LocalSearchStepScope<BasicSolution> step
                && scope.getPhaseScope().getSolverScope() == solver.getSolverScope()) {
              ordinaryRevisions.add(solver.getSolverScope().getResumableSearchModelRevision());
              if (submitted.compareAndSet(false, true)) {
                assertThat(step.getSelectedMoveCount()).isEqualTo(1);
                solver.addProblemChange((working, director) -> applied.incrementAndGet());
              }
            }
          }
        });
    BasicSolution result;
    try (var ignored =
        ResumableSearchRuntime.observeRounds(round -> residentRounds.incrementAndGet(), false)) {
      result = solver.solve(workload.createProblem(24));
    }
    assertThat(applied).hasValue(1);
    assertThat(ordinaryRevisions).containsExactly(0L, 1L);
    assertThat(
            RecordingForager.INSTANCES.stream()
                .flatMap(forager -> forager.events.stream())
                .filter(event -> event.startsWith("candidate:"))
                .count())
        .isEqualTo(2);
    var scope = solver.getSolverScope();
    assertThat(scope.getResumableSearchState().solverMoveEvaluationCount(scope)).isEqualTo(2);
    assertThat(residentRounds).hasValue(0);
    assertThat(scope.getBestScore().isFullyAssigned()).isTrue();
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  @Test
  void problemFactWeightChangeRebuildsRevisionWithoutRenewingTheGlobalBudget() throws Exception {
    var workload = new TimetableWorkload();
    var config =
        workload.solverConfig(
            "NONE",
            37,
            1,
            new TerminationConfig().withStepCountLimit(1000),
            EnvironmentMode.FULL_ASSERT);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    controller(config, search, "la");
    config.withPhases(
        sessions(search, 4, 2, 2, ResumableSearchCooperationPolicy.NONE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(47)));
    var solver = (DefaultSolver<Timetable>) SolverFactory.<Timetable>create(config).buildSolver();
    var submitted = new AtomicBoolean();
    var applied = new AtomicBoolean();
    var completed = new AtomicInteger();
    var revisions = new ArrayList<Long>();
    Timetable result;
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              completed.addAndGet(
                  round.sessions().stream().mapToInt(session -> session.steps().size()).sum());
              revisions.add(solver.getSolverScope().getResumableSearchModelRevision());
              if (submitted.compareAndSet(false, true))
                solver.addProblemChange(
                    (working, director) -> {
                      director.changeProblemProperty(
                          working.getPolicy(), policy -> policy.setConflictWeight(137));
                      applied.set(true);
                    });
              if (applied.get())
                round
                    .sessions()
                    .forEach(
                        session -> {
                          if (session.bestSolution() instanceof Timetable snapshot) {
                            assertThat(snapshot.getPolicy().getConflictWeight()).isEqualTo(137);
                            assertThat(snapshot.getScore()).isEqualTo(workload.recompute(snapshot));
                          }
                        });
            })) {
      result = solver.solve(workload.createProblem(24));
    }
    assertThat(applied).isTrue();
    assertThat(completed).hasValue(47);
    assertThat(revisions.stream().distinct().count()).isGreaterThanOrEqualTo(2);
    assertThat(result.getPolicy().getConflictWeight()).isEqualTo(137);
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  private static IslandModelPhaseConfig independentIslands(PhaseConfig<?> child) {
    return new IslandModelPhaseConfig()
        .withIslandCount(2)
        .withMoveThreadCount("NONE")
        .withCompareGlobalEnabled(false)
        .withMigrationFrequency(Integer.MAX_VALUE)
        .withPhaseConfigList(List.of(child));
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> child) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(TwoPartitions.class)
        .withRunnablePartThreadLimit("1")
        .withPhaseConfigList(List.of(child));
  }

  public static final class TwoPartitions implements SolutionPartitioner<BasicSolution> {
    @Override
    public List<BasicSolution> splitWorkingSolution(
        ScoreDirector<BasicSolution> director, Integer limit) {
      var inner = (InnerScoreDirector<BasicSolution, ?>) director;
      var first = inner.cloneWorkingSolution();
      var second = inner.cloneWorkingSolution();
      int middle = first.getJobs().size() / 2;
      first.setJobs(new ArrayList<>(first.getJobs().subList(0, middle)));
      second.setJobs(new ArrayList<>(second.getJobs().subList(middle, second.getJobs().size())));
      first.setScore(null);
      second.setScore(null);
      return List.of(first, second);
    }
  }

  public static final class TrackingThreadFactory implements ThreadFactory {
    static final ConcurrentLinkedQueue<Thread> THREADS = new ConcurrentLinkedQueue<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "resumable-test-owner-" + THREADS.size());
      THREADS.add(thread);
      return thread;
    }
  }

  public static final class LifecycleIncrementalCalculator
      implements IncrementalScoreCalculator<BasicSolution, SimpleScore> {
    static final AtomicInteger INSTANCES = new AtomicInteger();
    static final AtomicBoolean CLAIMED = new AtomicBoolean();
    static final AtomicBoolean failureTriggered = new AtomicBoolean();
    static volatile int failAtInstance = Integer.MAX_VALUE;
    static volatile boolean blockSearch;
    static volatile CountDownLatch entered = new CountDownLatch(1);
    static volatile CountDownLatch release = new CountDownLatch(1);
    private final int instance = INSTANCES.incrementAndGet();
    private final ResumableIncrementalScoreCalculator delegate =
        new ResumableIncrementalScoreCalculator();
    private boolean searchMutation;

    static void resetControls(int failAt, boolean block) {
      INSTANCES.set(0);
      CLAIMED.set(false);
      failureTriggered.set(false);
      failAtInstance = failAt;
      blockSearch = block;
      entered = new CountDownLatch(1);
      release = new CountDownLatch(1);
    }

    @Override
    public void resetWorkingSolution(BasicSolution solution) {
      if (instance == failAtInstance) {
        failureTriggered.set(true);
        throw new IllegalStateException("Deliberate session score initialization failure.");
      }
      delegate.resetWorkingSolution(solution);
      searchMutation = false;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      delegate.beforeVariableChanged(entity, variableName);
      searchMutation = true;
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      delegate.afterVariableChanged(entity, variableName);
    }

    @Override
    public SimpleScore calculateScore() {
      if (blockSearch && searchMutation && CLAIMED.compareAndSet(false, true)) {
        entered.countDown();
        try {
          release.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted test scoring transaction.", interrupted);
        }
      }
      return delegate.calculateScore();
    }
  }
}
