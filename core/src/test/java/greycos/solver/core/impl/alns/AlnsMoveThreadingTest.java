package greycos.solver.core.impl.alns;

import static greycos.solver.core.impl.alns.AlnsThreadingTestSupport.assertThreadStopped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Full solver regressions with an independent domain replay after every completed trial. */
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
class AlnsMoveThreadingTest {
  static Stream<Arguments> workloadsAndRepairs() {
    return Stream.of("basic", "list", "mixed")
        .flatMap(
            shape ->
                Stream.of(AlnsRepairOperatorType.values())
                    .map(repair -> Arguments.of(shape, repair)));
  }

  @ParameterizedTest
  @MethodSource("workloadsAndRepairs")
  void fixedWorkMatchesSequentialTrialScoresStatesAndProbeCounts(
      String shape, AlnsRepairOperatorType repair) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    var sequential = run(workload, "NONE", repair);
    for (var threads : List.of("1", "2", "4", "8")) {
      var parallel = run(workload, threads, repair);
      assertThat(parallel.traceFingerprint())
          .as("%s %s threads=%s", shape, repair, threads)
          .isEqualTo(sequential.traceFingerprint());
      assertThat(parallel.stateFingerprint()).isEqualTo(sequential.stateFingerprint());
      assertThat(parallel.bestScore()).isEqualTo(sequential.bestScore());
      assertThat(parallel.probes()).isEqualTo(sequential.probes());
      assertThat(parallel.trials()).isEqualTo(sequential.trials());
    }
  }

  private static <S> AlnsMoveThreadingBenchmark.Result run(
      Workload<S> workload, String threads, AlnsRepairOperatorType repair) {
    return AlnsMoveThreadingBenchmark.run(
        workload, threads, 3L, 24, 3, repair, 1000, 8, true, EnvironmentMode.NO_ASSERT);
  }

  @ParameterizedTest
  @CsvSource({
    "basic,ddec4d1a4f0c130ff67b1bf0f1138b7895e235b9eaf68840d3705bcb8d6dc77b",
    "list,23abddb6214b629d0e90eb91ecbc2c0bbac008443db6f1dc722f7a0c94d9bf52",
    "mixed,b49ce531b7e3a5ae4bae552f9b45a93fc71bcbfbddbe268bdde0bfab8b601f74"
  })
  void sequentialModePreservesTraceCapturedBeforeThreadingImplementation(
      String shape, String baselineFingerprint) {
    var result =
        AlnsMoveThreadingBenchmark.run(
            AlnsMoveThreadingWorkload.named(shape),
            "NONE",
            0,
            60,
            4,
            AlnsRepairOperatorType.GREEDY,
            1000,
            30,
            true,
            EnvironmentMode.NO_ASSERT);
    assertThat(result.traceFingerprint()).isEqualTo(baselineFingerprint);
  }

  @Test
  void enabledWorkersActuallyEvaluateIncrementalConstraintsAndStopAfterSolving() {
    RecordingThreadFactory.threads.clear();
    RecordingConstraints.matches.clear();
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                "2",
                1L,
                4,
                AlnsRepairOperatorType.GREEDY,
                new TerminationConfig().withStepCountLimit(12),
                EnvironmentMode.NO_ASSERT)
            .withConstraintProviderClass(RecordingConstraints.class)
            .withThreadFactoryClass(RecordingThreadFactory.class);
    var solver = SolverFactory.create(config).buildSolver();
    solver.solve(workload.createProblem(80));
    assertThat(RecordingThreadFactory.threads)
        .hasSize(2)
        .allSatisfy(
            thread -> {
              assertThat(
                      RecordingConstraints.matches.getOrDefault(thread, new AtomicInteger()).get())
                  .as("Worker performs scoring beyond initialization")
                  .isGreaterThan(80);
              assertThreadStopped(thread);
            });
  }

  @ParameterizedTest
  @CsvSource({"2,NONE,0", "NONE,2,2", "4,1,1"})
  void phaseWorkerSettingOverridesSolverSetting(
      String solverThreads, String phaseThreads, int expectedWorkers) {
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                solverThreads,
                0,
                4,
                AlnsRepairOperatorType.GREEDY,
                new TerminationConfig().withStepCountLimit(2),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class);
    ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst()).withMoveThreadCount(phaseThreads);
    SolverFactory.<BasicSolution>create(config).buildSolver().solve(workload.createProblem(60));
    assertThat(RecordingThreadFactory.threads)
        .hasSize(expectedWorkers)
        .allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
  }

  @ParameterizedTest
  @CsvSource({"NONE,,2", "2,,6", "2,NONE,2", "NONE,2,6"})
  void islandsDefaultToNoMoveWorkersAndRespectExplicitOverrides(
      String islandThreads, String phaseThreads, int expectedTotalThreads) {
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var phase =
        AlnsMoveThreadingWorkload.phase(
            4, AlnsRepairOperatorType.GREEDY, new TerminationConfig().withStepCountLimit(2));
    if (phaseThreads != null) phase.withMoveThreadCount(phaseThreads);
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                "4",
                0,
                4,
                AlnsRepairOperatorType.GREEDY,
                new TerminationConfig().withStepCountLimit(2),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withMoveThreadCount(islandThreads)
                    .withPhaseConfigList(List.of(phase)));
    var solution =
        SolverFactory.<BasicSolution>create(config).buildSolver().solve(workload.createProblem(60));
    assertThat(workload.recompute(solution)).isEqualTo(solution.getScore());
    assertThat(RecordingThreadFactory.threads)
        .hasSize(expectedTotalThreads)
        .allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
  }

  @ParameterizedTest
  @EnumSource(
      value = AlnsRepairOperatorType.class,
      names = {"GREEDY", "CHEAPEST_INSERTION", "REGRET_K"})
  void cancellationWhileWorkerScoresRestoresIncumbentAndClosesWorkers(AlnsRepairOperatorType repair)
      throws Exception {
    BlockingConstraints.reset(false);
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                "2",
                0,
                4,
                repair,
                new TerminationConfig().withStepCountLimit(2),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withConstraintProviderClass(BlockingConstraints.class);
    var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
    var original = workload.createProblem(20);
    String originalState = workload.state(original);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var future = executor.submit(() -> solver.solve(original));
      try {
        assertThat(BlockingConstraints.entered.await(10, TimeUnit.SECONDS)).isTrue();
        solver.terminateEarly();
      } finally {
        BlockingConstraints.release.countDown();
      }
      var solution = future.get(10, TimeUnit.SECONDS);
      assertThat(workload.state(solution)).isEqualTo(originalState);
      assertThat(RecordingThreadFactory.threads)
          .hasSize(2)
          .allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
    }
  }

  @Test
  void workerScoringFailureRestoresIncumbentAndClosesWorkers() {
    BlockingConstraints.reset(true);
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                "2",
                0,
                4,
                AlnsRepairOperatorType.GREEDY,
                new TerminationConfig().withStepCountLimit(2),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withConstraintProviderClass(BlockingConstraints.class);
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var states = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(
              greycos.solver.core.impl.phase.scope.AbstractPhaseScope<BasicSolution> phase) {
            states.add(workload.state(phase.getWorkingSolution()));
          }
        });
    var original = workload.createProblem(20);
    String originalState = workload.state(original);
    assertThatThrownBy(() -> solver.solve(original))
        .hasStackTraceContaining("Intentional worker failure");
    assertThat(states).containsExactly(originalState);
    assertThat(RecordingThreadFactory.threads)
        .hasSize(2)
        .allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 4, 5, 8, 9, 16, 17, 25})
  void repairScoreBudgetMatchesSequentialAtBatchBoundaries(long limit) {
    for (var repair :
        List.of(
            AlnsRepairOperatorType.GREEDY,
            AlnsRepairOperatorType.CHEAPEST_INSERTION,
            AlnsRepairOperatorType.REGRET_K)) {
      var sequential = budgetTrace("NONE", limit, repair);
      for (var threads : List.of("1", "2", "4")) {
        assertThat(budgetTrace(threads, limit, repair))
            .as("repair=%s limit=%s threads=%s", repair, limit, threads)
            .isEqualTo(sequential);
      }
    }
  }

  private static List<String> budgetTrace(
      String threads, long limit, AlnsRepairOperatorType repair) {
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
            workload,
            threads,
            0L,
            4,
            repair,
            new TerminationConfig().withStepCountLimit(3),
            EnvironmentMode.NO_ASSERT);
    ((AlnsPhaseConfig) config.getPhaseConfigList().getFirst())
        .withRepairScoreCalculationLimit(limit);
    var trace = new ArrayList<String>();
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            var result = ((AlnsStepScope<?>) step).getTrialResult();
            assertThat(workload.recompute(step.getWorkingSolution()))
                .isEqualTo(result.afterScore());
            trace.add(
                result.outcome()
                    + ":"
                    + result.probeCount()
                    + ":"
                    + result.afterScore()
                    + ":"
                    + workload.state(step.getWorkingSolution()));
          }
        });
    solver.solve(workload.createProblem(60));
    return trace;
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    static final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "alns-test-worker-" + threads.size());
      threads.add(thread);
      return thread;
    }
  }

  public static final class RecordingConstraints implements ConstraintProvider {
    static final ConcurrentHashMap<Thread, AtomicInteger> matches = new ConcurrentHashMap<>();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(
                SimpleScore.ONE,
                job -> {
                  matches
                      .computeIfAbsent(Thread.currentThread(), ignored -> new AtomicInteger())
                      .incrementAndGet();
                  return (long) Math.abs(job.getMachine().id() - job.getPreferredMachine())
                      * job.getUnits();
                })
            .asConstraint("Recorded preference")
      };
    }
  }

  public static final class BlockingConstraints implements ConstraintProvider {
    private static final ConcurrentHashMap<Thread, AtomicInteger> matches =
        new ConcurrentHashMap<>();
    private static final AtomicBoolean claimed = new AtomicBoolean();
    private static CountDownLatch entered;
    private static CountDownLatch release;
    private static boolean fail;

    static void reset(boolean shouldFail) {
      matches.clear();
      claimed.set(false);
      entered = new CountDownLatch(1);
      release = new CountDownLatch(1);
      fail = shouldFail;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(
                SimpleScore.ONE,
                job -> {
                  Thread thread = Thread.currentThread();
                  int count =
                      matches
                          .computeIfAbsent(thread, ignored -> new AtomicInteger())
                          .incrementAndGet();
                  if (thread.getName().startsWith("alns-test-worker-")
                      && count > 20
                      && claimed.compareAndSet(false, true)) {
                    if (fail) throw new IllegalStateException("Intentional worker failure");
                    entered.countDown();
                    try {
                      if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Worker release timed out.");
                      }
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                    }
                  }
                  return (long) Math.abs(job.getMachine().id() - job.getPreferredMachine())
                      * job.getUnits();
                })
            .asConstraint("Controlled worker scoring")
      };
    }
  }
}
