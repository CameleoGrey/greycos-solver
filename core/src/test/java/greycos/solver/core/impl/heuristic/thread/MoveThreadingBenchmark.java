package greycos.solver.core.impl.heuristic.thread;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

/** See move-threading-benchmark.md next to this source for usage and measurement limitations. */
public final class MoveThreadingBenchmark {

  private static final String[] DIAGNOSTIC_NAMES = {
    "generated",
    "evaluated",
    "scored",
    "consumed",
    "consumedDoable",
    "discarded",
    "steps",
    "samples",
    "sampledRebaseNanos",
    "sampledEvaluationNanos",
    "replayNanos",
    "idleNanos",
    "orderedWaitNanos",
    "replayWaitNanos"
  };

  private MoveThreadingBenchmark() {}

  public static void main(String[] args) {
    if (args.length < 7 || args.length > 11) {
      throw new IllegalArgumentException(
          "Usage: <basic|list> <NONE|threads> <seed> <size> <acceptedCount>"
              + " <warmupSeconds> <measureSeconds> [stepLimit] [trace] [bufferPerThread]"
              + " [internal|external]");
    }
    String name = args[0];
    String threads = args[1];
    if (!"NONE".equals(threads) && Integer.parseInt(threads) < 1) {
      throw new IllegalArgumentException("Thread count must be NONE or a positive integer.");
    }
    long seed = Long.parseLong(args[2]);
    int size = Integer.parseInt(args[3]);
    int acceptedCount = Integer.parseInt(args[4]);
    int warmupSeconds = Integer.parseInt(args[5]);
    int measureSeconds = Integer.parseInt(args[6]);
    int stepLimit = args.length >= 8 ? Integer.parseInt(args[7]) : 0;
    boolean trace = args.length >= 9 && Boolean.parseBoolean(args[8]);
    int bufferPerThread = args.length >= 10 ? Integer.parseInt(args[9]) : 10;
    String terminationMode = args.length == 11 ? args[10] : "internal";
    if (!"internal".equals(terminationMode) && !"external".equals(terminationMode)) {
      throw new IllegalArgumentException("Termination mode must be internal or external.");
    }
    if ("external".equals(terminationMode) && stepLimit > 0) {
      throw new IllegalArgumentException("External termination requires stepLimit=0.");
    }
    if (acceptedCount < 1
        || warmupSeconds < 0
        || measureSeconds < 1
        || stepLimit < 0
        || bufferPerThread < 1
        || (trace && stepLimit == 0)) {
      throw new IllegalArgumentException(
          "Positive acceptance/measurement/buffer, nonnegative warmup/steps required; trace needs steps.");
    }
    var workload = MoveThreadingWorkload.named(name);
    if (warmupSeconds > 0) {
      run(
          workload,
          threads,
          seed,
          size,
          acceptedCount,
          warmupSeconds,
          0,
          false,
          bufferPerThread,
          terminationMode);
    }
    var result =
        run(
            workload,
            threads,
            seed,
            size,
            acceptedCount,
            measureSeconds,
            stepLimit,
            trace,
            bufferPerThread,
            terminationMode);
    System.out.println(
        "workload,threads,seed,size,accepted_count,buffer_per_thread,warmup_seconds,measure_seconds,"
            + "step_limit,termination_mode,processors,max_heap_bytes,setup_ms,solve_ms,useful_moves,moves_per_second,"
            + "steps,initial_score,best_score,state_sha256,trace_sha256,gc_count,gc_ms,"
            + "used_heap_bytes,core_location,java_version,diagnostic_timing_enabled,"
            + Arrays.stream(DIAGNOSTIC_NAMES)
                .map(namePart -> "pipeline_" + namePart)
                .collect(Collectors.joining(",")));
    System.out.printf(
        Locale.ROOT,
        "%s,%s,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%.3f,%.3f,%d,%.3f,%d,%d,%d,%s,%s,%d,%d,%d,%s,%s,%s,%s%n",
        name,
        threads,
        seed,
        size,
        acceptedCount,
        bufferPerThread,
        warmupSeconds,
        measureSeconds,
        stepLimit,
        terminationMode,
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory(),
        result.setupNanos / 1_000_000.0,
        result.solveNanos / 1_000_000.0,
        result.usefulMoves,
        result.usefulMoves * 1_000_000_000.0 / result.solveNanos,
        result.steps,
        result.initialScore,
        result.bestScore,
        result.stateFingerprint,
        result.traceFingerprint,
        result.gcCount,
        result.gcMillis,
        result.usedHeapBytes,
        csv(DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation().toString()),
        csv(System.getProperty("java.version")),
        Boolean.getBoolean("greycos.solver.moveThreadDiagnostics"),
        result.diagnostics);
  }

  private static <Solution_> Result run(
      Workload<Solution_> workload,
      String threads,
      long seed,
      int size,
      int acceptedCount,
      int seconds,
      int stepLimit,
      boolean trace,
      int bufferPerThread,
      String terminationMode) {
    long setupStart = System.nanoTime();
    var problem = workload.createProblem(size);
    long initialScore = workload.recompute(problem).score();
    var termination = new TerminationConfig();
    if (stepLimit > 0) {
      termination.setStepCountLimit(stepLimit);
    } else if ("internal".equals(terminationMode)) {
      termination.setSpentLimit(Duration.ofSeconds(seconds));
    }
    var config =
        workload
            .solverConfig(threads, seed, acceptedCount, termination, EnvironmentMode.NO_ASSERT)
            .withMoveThreadBufferSize(bufferPerThread);
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    var listener = new StepRecorder<Solution_>(workload, trace);
    solver.addPhaseLifecycleListener(listener);
    var externalDeadline =
        "external".equals(terminationMode) ? new ExternalDeadline<>(solver, seconds) : null;
    if (externalDeadline != null) {
      solver.addPhaseLifecycleListener(externalDeadline);
    }
    long setupNanos = System.nanoTime() - setupStart;
    long beforeGcCount = gcCount();
    long beforeGcMillis = gcMillis();
    long start = System.nanoTime();
    Solution_ solution;
    long solveNanos;
    try {
      solution = solver.solve(problem);
      solveNanos = System.nanoTime() - start;
    } finally {
      if (externalDeadline != null) {
        externalDeadline.close();
      }
    }
    long gcCount = gcCount() - beforeGcCount;
    long gcMillis = gcMillis() - beforeGcMillis;
    long usedHeapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    var independentScore = workload.recompute(solution);
    if (!independentScore.equals(workload.score(solution))) {
      throw new IllegalStateException(
          "Score mismatch: " + workload.score(solution) + " != " + independentScore);
    }
    if (stepLimit > 0 && listener.steps != stepLimit) {
      throw new IllegalStateException(
          "Expected " + stepLimit + " steps; completed " + listener.steps);
    }
    return new Result(
        setupNanos,
        solveNanos,
        solver.getMoveEvaluationCount(),
        listener.steps,
        initialScore,
        independentScore.score(),
        MoveThreadingWorkload.fingerprint(workload.state(solution)),
        trace ? MoveThreadingWorkload.fingerprint(listener.trace.toString()) : "",
        gcCount,
        gcMillis,
        usedHeapBytes,
        diagnostics(solver));
  }

  /**
   * Read new implementation counters after phase shutdown without coupling the baseline build to
   * its API.
   */
  private static String diagnostics(DefaultSolver<?> solver) {
    var phase = solver.getPhaseList().get(0);
    try {
      var deciderField = phase.getClass().getDeclaredField("decider");
      deciderField.setAccessible(true);
      Object decider = deciderField.get(phase);
      Object snapshot;
      try {
        snapshot = decider.getClass().getMethod("getMoveEvaluationDiagnostics").invoke(decider);
      } catch (NoSuchMethodException e) {
        // NONE and baseline implementations do not expose pipeline diagnostics.
        return Arrays.stream(DIAGNOSTIC_NAMES)
            .map(ignored -> "-1")
            .collect(Collectors.joining(","));
      }
      var out = new StringBuilder();
      for (String name : DIAGNOSTIC_NAMES) {
        if (!out.isEmpty()) {
          out.append(',');
        }
        out.append(snapshot.getClass().getMethod(name).invoke(snapshot));
      }
      return out.toString();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to read move pipeline diagnostics after solve.", e);
    }
  }

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionCount()))
        .sum();
  }

  private static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionTime()))
        .sum();
  }

  private static String csv(String value) {
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static final class ExternalDeadline<Solution_>
      extends PhaseLifecycleListenerAdapter<Solution_> implements AutoCloseable {
    private final DefaultSolver<Solution_> solver;
    private final int seconds;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              var thread = new Thread(runnable, "move-threading-benchmark-deadline");
              thread.setDaemon(true);
              return thread;
            });
    private ScheduledFuture<?> scheduledTermination;

    private ExternalDeadline(DefaultSolver<Solution_> solver, int seconds) {
      this.solver = solver;
      this.seconds = seconds;
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
      if (phaseScope instanceof LocalSearchPhaseScope<?> && scheduledTermination == null) {
        // This callback follows phaseScope.startingNow() and precedes move-worker setup,
        // matching the normal phase spent-limit boundary without polling a clock per move.
        scheduledTermination =
            scheduler.schedule(
                () -> {
                  solver.terminateEarly();
                },
                seconds,
                TimeUnit.SECONDS);
      }
    }

    @Override
    public void close() {
      if (scheduledTermination != null) {
        scheduledTermination.cancel(false);
      }
      scheduler.shutdownNow();
    }
  }

  private static final class StepRecorder<Solution_>
      extends PhaseLifecycleListenerAdapter<Solution_> {
    private final Workload<Solution_> workload;
    private final boolean recordTrace;
    private final StringBuilder trace = new StringBuilder();
    private long steps;

    private StepRecorder(Workload<Solution_> workload, boolean recordTrace) {
      this.workload = workload;
      this.recordTrace = recordTrace;
    }

    @Override
    public void stepEnded(AbstractStepScope<Solution_> stepScope) {
      if (stepScope instanceof LocalSearchStepScope<Solution_> localStep) {
        steps++;
        if (recordTrace) {
          trace
              .append(localStep.getStepIndex())
              .append(':')
              .append(localStep.getStep())
              .append(':')
              .append(localStep.getScore().raw())
              .append(':')
              .append(localStep.getSelectedMoveCount())
              .append(':')
              .append(localStep.getAcceptedMoveCount())
              .append(':')
              .append(
                  MoveThreadingWorkload.fingerprint(workload.state(stepScope.getWorkingSolution())))
              .append('\n');
        }
      }
    }
  }

  private record Result(
      long setupNanos,
      long solveNanos,
      long usefulMoves,
      long steps,
      long initialScore,
      long bestScore,
      String stateFingerprint,
      String traceFingerprint,
      long gcCount,
      long gcMillis,
      long usedHeapBytes,
      String diagnostics) {}
}
