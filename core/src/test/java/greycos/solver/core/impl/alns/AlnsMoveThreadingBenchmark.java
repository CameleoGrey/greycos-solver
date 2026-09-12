package greycos.solver.core.impl.alns;

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
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

/** Standalone fixed-work/equal-time benchmark; also compiles against the sequential baseline. */
public final class AlnsMoveThreadingBenchmark {
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
  private static final String[] ATTEMPT_DIAGNOSTIC_NAMES = {
    "started",
    "completed",
    "incomplete",
    "discarded",
    "selectedAttemptIndex",
    "creditedQueries",
    "workingCopies"
  };

  private AlnsMoveThreadingBenchmark() {}

  public static void main(String[] args) {
    if (args.length == 3 && args[0].equals("--validate")) {
      for (String seed : args[2].split(",")) {
        for (String threads : args[1].split(",")) {
          for (String shape : new String[] {"basic", "list", "mixed"}) {
            for (boolean large : new boolean[] {false, true}) {
              int size =
                  large
                      ? switch (shape) {
                        case "basic" -> 400;
                        case "list" -> 500;
                        default -> 240;
                      }
                      : (shape.equals("basic") ? 80 : 60);
              main(
                  new String[] {
                    shape,
                    threads,
                    seed,
                    Integer.toString(size),
                    "6",
                    "GREEDY",
                    "0",
                    "1000",
                    "8",
                    "true"
                  });
              if (large && !shape.equals("basic")) {
                main(
                    new String[] {
                      shape,
                      threads,
                      seed,
                      Integer.toString(size),
                      "6",
                      "REGRET_2",
                      "0",
                      "1000",
                      "8",
                      "true"
                    });
              }
            }
          }
        }
      }
      return;
    }
    if (args.length != 10) {
      throw new IllegalArgumentException(
          "Usage: <basic|list|mixed> <NONE|threads> <seed> <size> <destroyedCount>"
              + " <repairType> <warmupMillis> <measureMillis> <trialLimit> <trace>");
    }
    String name = args[0];
    String threads = args[1];
    long seed = Long.parseLong(args[2]);
    int size = Integer.parseInt(args[3]);
    int destroyed = Integer.parseInt(args[4]);
    var repair = AlnsRepairOperatorType.valueOf(args[5]);
    long warmupMillis = Long.parseLong(args[6]);
    long measureMillis = Long.parseLong(args[7]);
    int trials = Integer.parseInt(args[8]);
    boolean trace = Boolean.parseBoolean(args[9]);
    var options = Options.read();
    if (warmupMillis < 0 || measureMillis < 1 || trials < 0 || destroyed < 1) {
      throw new IllegalArgumentException("Invalid benchmark budget or destruction count.");
    }
    var workload = AlnsMoveThreadingWorkload.named(name);
    if (warmupMillis > 0) {
      if (trials > 0) {
        long warmupStarted = System.nanoTime();
        do {
          run(
              workload,
              threads,
              seed,
              size,
              destroyed,
              repair,
              warmupMillis,
              trials,
              false,
              EnvironmentMode.NO_ASSERT);
        } while (System.nanoTime() - warmupStarted < warmupMillis * 1_000_000L);
      } else {
        run(
            workload,
            threads,
            seed,
            size,
            destroyed,
            repair,
            warmupMillis,
            0,
            false,
            EnvironmentMode.NO_ASSERT);
      }
    }
    var result =
        run(
            workload,
            threads,
            seed,
            size,
            destroyed,
            repair,
            measureMillis,
            trials,
            trace,
            EnvironmentMode.NO_ASSERT);
    System.out.println(
        "workload,threads,seed,size,destroyed,repair,warmup_ms,budget_ms,trial_limit,"
            + "processors,max_heap_bytes,setup_ms,solve_ms,trials,useful_probes,probes_per_second,"
            + "score_calculations,initial_score,best_score,state_sha256,trace_sha256,gc_count,gc_ms,"
            + "used_heap_bytes,process_cpu_ms,core_location,java_version,diagnostic_timing_enabled,"
            + Arrays.stream(DIAGNOSTIC_NAMES)
                .map(namePart -> "pipeline_" + namePart)
                .collect(Collectors.joining(","))
            + ",warmup_termination,termination_mode,phase_ms,gc_boundary,verification_gc_count,"
            + "verification_gc_ms,repair_attempts_mode,repair_attempt_count,move_thread_buffer_size,"
            + "external_deadline_fired,termination_requested_ms,"
            + Arrays.stream(ATTEMPT_DIAGNOSTIC_NAMES)
                .map(namePart -> "attempt_" + namePart)
                .collect(Collectors.joining(",")));
    System.out.printf(
        Locale.ROOT,
        "%s,%s,%d,%d,%d,%s,%d,%d,%d,%d,%d,%.3f,%.3f,%d,%d,%.3f,%d,%d,%d,%s,%s,%d,%d,%d,%.3f,%s,%s,%s,%s,%s,%s,%.3f,solve,%d,%d,%s,%d,%d,%s,%.3f,%s%n",
        name,
        threads,
        seed,
        size,
        destroyed,
        repair,
        warmupMillis,
        measureMillis,
        trials,
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().maxMemory(),
        result.setupNanos / 1_000_000.0,
        result.solveNanos / 1_000_000.0,
        result.trials,
        result.probes,
        result.probes * 1_000_000_000.0 / result.solveNanos,
        result.scoreCalculations,
        result.initialScore,
        result.bestScore,
        result.stateFingerprint,
        result.traceFingerprint,
        result.gcCount,
        result.gcMillis,
        result.usedHeapBytes,
        result.cpuNanos / 1_000_000.0,
        DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation(),
        System.getProperty("java.version"),
        Boolean.getBoolean("greycos.solver.moveThreadDiagnostics"),
        result.diagnostics,
        warmupMillis == 0 ? "none" : options.termination(trials),
        options.termination(trials),
        result.phaseNanos / 1_000_000.0,
        result.verificationGcCount,
        result.verificationGcMillis,
        options.moveThreadingMode,
        options.repairAttemptCount,
        options.bufferSize,
        result.externalDeadlineFired,
        result.terminationRequestedNanos < 0 ? -1 : result.terminationRequestedNanos / 1_000_000.0,
        result.attemptDiagnostics);
  }

  static <S> Result run(
      Workload<S> workload,
      String threads,
      long seed,
      int size,
      int destroyed,
      AlnsRepairOperatorType repair,
      long millis,
      int trials,
      boolean trace,
      EnvironmentMode environment) {
    long setupStart = System.nanoTime();
    var options = Options.read();
    var problem = workload.createProblem(size);
    long initialScore = workload.recompute(problem).score();
    var termination =
        trials > 0
            ? new TerminationConfig().withStepCountLimit(trials)
            : options.externalDeadline
                ? new TerminationConfig()
                : new TerminationConfig().withSpentLimit(Duration.ofMillis(millis));
    var config =
        AlnsMoveThreadingWorkload.config(
            workload, threads, seed, destroyed, repair, termination, environment);
    config.withMoveThreadBufferSize(options.bufferSize);
    options.configureAttempts(config.getPhaseConfigList().getFirst());
    var solver = (DefaultSolver<S>) SolverFactory.<S>create(config).buildSolver();
    var recorder =
        new TrialRecorder<>(
            workload, trace, solver, options.externalDeadline && trials == 0, millis);
    solver.addPhaseLifecycleListener(recorder);
    long setupNanos = System.nanoTime() - setupStart;
    long gcCountBefore = gcCount();
    long gcMillisBefore = gcMillis();
    long cpuBefore = processCpuNanos();
    long started = System.nanoTime();
    S solution;
    try {
      solution = solver.solve(problem);
    } finally {
      recorder.closeDeadline();
    }
    long elapsed = System.nanoTime() - started;
    long cpuNanos = processCpuNanos() - cpuBefore;
    long usedHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long gcCountAfterSolve = gcCount();
    long gcMillisAfterSolve = gcMillis();
    var independentlyComputed = workload.recompute(solution);
    if (!independentlyComputed.equals(workload.score(solution))) {
      throw new IllegalStateException(
          "Independent score mismatch: "
              + independentlyComputed
              + " != "
              + workload.score(solution));
    }
    if (trials > 0 && recorder.trials != trials) {
      throw new IllegalStateException("Expected " + trials + " trials, got " + recorder.trials);
    }
    String stateFingerprint = MoveThreadingWorkload.fingerprint(workload.state(solution));
    String traceFingerprint =
        trace ? MoveThreadingWorkload.fingerprint(recorder.trace.toString()) : "";
    return new Result(
        setupNanos,
        elapsed,
        recorder.trials,
        recorder.probes,
        solver.getScoreCalculationCount(),
        initialScore,
        independentlyComputed.score(),
        stateFingerprint,
        traceFingerprint,
        gcCountAfterSolve - gcCountBefore,
        gcMillisAfterSolve - gcMillisBefore,
        usedHeap,
        cpuNanos,
        diagnostics(solver),
        recorder.phaseNanos,
        gcCount() - gcCountAfterSolve,
        gcMillis() - gcMillisAfterSolve,
        recorder.externalDeadlineFired,
        recorder.terminationRequestedNanos,
        diagnostics(solver, "getRepairAttemptDiagnostics", ATTEMPT_DIAGNOSTIC_NAMES));
  }

  /** Optional settings keep the original ten positional arguments and old runtimes usable. */
  private record Options(
      boolean externalDeadline, String moveThreadingMode, int repairAttemptCount, int bufferSize) {
    private static final String PREFIX = "greycos.solver.alnsBenchmark.";

    static Options read() {
      String termination = System.getProperty(PREFIX + "termination", "internal");
      if (!termination.equals("internal") && !termination.equals("external")) {
        throw new IllegalArgumentException("Benchmark termination must be internal or external.");
      }
      String mode = System.getProperty(PREFIX + "moveThreadingMode", "PROBES");
      int attempts = Integer.parseInt(System.getProperty(PREFIX + "repairAttemptCount", "1"));
      int buffer = Integer.parseInt(System.getProperty(PREFIX + "bufferSize", "10"));
      if ((!mode.equals("PROBES") && !mode.equals("REPAIR_ATTEMPTS"))
          || buffer < 1
          || (mode.equals("REPAIR_ATTEMPTS") ? attempts < 2 : attempts != 1)) {
        throw new IllegalArgumentException(
            "Use PROBES with one attempt or REPAIR_ATTEMPTS with at least two; buffer must be positive.");
      }
      return new Options(termination.equals("external"), mode, attempts, buffer);
    }

    String termination(int trials) {
      return trials > 0 ? "fixed" : externalDeadline ? "external" : "time";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void configureAttempts(Object phase) {
      if (moveThreadingMode.equals("PROBES")) return;
      try {
        Class modeClass = Class.forName("greycos.solver.core.config.alns.AlnsMoveThreadingMode");
        phase
            .getClass()
            .getMethod("setMoveThreadingMode", modeClass)
            .invoke(phase, Enum.valueOf(modeClass, moveThreadingMode));
        try {
          phase
              .getClass()
              .getMethod("setRepairAttemptCount", int.class)
              .invoke(phase, repairAttemptCount);
        } catch (NoSuchMethodException ignored) {
          phase
              .getClass()
              .getMethod("setRepairAttemptCount", Integer.class)
              .invoke(phase, repairAttemptCount);
        }
      } catch (ReflectiveOperationException exception) {
        throw new IllegalArgumentException(
            "This runtime does not support the requested repair-attempt benchmark configuration.",
            exception);
      }
    }
  }

  private static String diagnostics(DefaultSolver<?> solver) {
    return diagnostics(solver, "getMoveEvaluationDiagnostics", DIAGNOSTIC_NAMES);
  }

  private static String diagnostics(DefaultSolver<?> solver, String accessor, String[] names) {
    try {
      var phase = solver.getPhaseList().getFirst();
      var snapshot = phase.getClass().getMethod(accessor).invoke(phase);
      if (snapshot == null) return unavailableDiagnostics(names);
      var values = new StringBuilder();
      for (String name : names) {
        if (!values.isEmpty()) values.append(',');
        values.append(snapshot.getClass().getMethod(name).invoke(snapshot));
      }
      return values.toString();
    } catch (NoSuchMethodException exception) {
      return unavailableDiagnostics(names);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to read ALNS worker diagnostics.", exception);
    }
  }

  private static String unavailableDiagnostics(String[] names) {
    return Arrays.stream(names).map(ignored -> "-1").collect(Collectors.joining(","));
  }

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionCount()))
        .sum();
  }

  private static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionTime()))
        .sum();
  }

  private static long processCpuNanos() {
    return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
        .getProcessCpuTime();
  }

  private static final class TrialRecorder<S> extends PhaseLifecycleListenerAdapter<S> {
    private final Workload<S> workload;
    private final boolean enabled;
    private final StringBuilder trace = new StringBuilder();
    private long trials;
    private long probes;
    private final DefaultSolver<S> solver;
    private final long budgetNanos;
    private final ScheduledExecutorService deadlineExecutor;
    private ScheduledFuture<?> deadlineTask;
    private long phaseStartedNanos;
    private long phaseNanos;
    private volatile boolean externalDeadlineFired;
    private volatile long terminationRequestedNanos = -1;

    TrialRecorder(
        Workload<S> workload,
        boolean enabled,
        DefaultSolver<S> solver,
        boolean externalDeadline,
        long millis) {
      this.workload = workload;
      this.enabled = enabled;
      this.solver = solver;
      budgetNanos = TimeUnit.MILLISECONDS.toNanos(millis);
      deadlineExecutor =
          externalDeadline
              ? Executors.newSingleThreadScheduledExecutor(
                  runnable -> {
                    var thread = new Thread(runnable, "alns-benchmark-deadline");
                    thread.setDaemon(true);
                    return thread;
                  })
              : null;
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<S> phaseScope) {
      phaseStartedNanos = System.nanoTime();
      if (deadlineExecutor != null) {
        // ScheduledExecutorService uses relative monotonic delays; no solver clock is changed.
        deadlineTask =
            deadlineExecutor.schedule(
                () -> {
                  terminationRequestedNanos = System.nanoTime() - phaseStartedNanos;
                  externalDeadlineFired = true;
                  solver.terminateEarly();
                },
                Math.max(0, budgetNanos - (System.nanoTime() - phaseStartedNanos)),
                TimeUnit.NANOSECONDS);
      }
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<S> phaseScope) {
      phaseNanos = System.nanoTime() - phaseStartedNanos;
      if (deadlineTask != null) deadlineTask.cancel(false);
    }

    void closeDeadline() {
      if (deadlineExecutor == null) return;
      deadlineExecutor.shutdownNow();
      boolean interrupted = Thread.interrupted();
      try {
        // The timer only calls terminateEarly(); join it before sampling final process metrics.
        while (!deadlineExecutor.isTerminated()) {
          try {
            if (!deadlineExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Benchmark deadline thread failed to stop.");
            }
          } catch (InterruptedException exception) {
            interrupted = true;
          }
        }
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    }

    @Override
    public void stepEnded(AbstractStepScope<S> stepScope) {
      if (stepScope instanceof AlnsStepScope<S> alnsStep) {
        var result = alnsStep.getTrialResult();
        trials++;
        probes += result.probeCount();
        if (enabled) {
          var independentScore = workload.recompute(stepScope.getWorkingSolution());
          if (!independentScore.equals(result.afterScore())) {
            throw new IllegalStateException("Trial independent score mismatch.");
          }
          trace
              .append(result.trialIndex())
              .append(':')
              .append(result.destroyId())
              .append(':')
              .append(result.repairId())
              .append(':')
              .append(result.outcome())
              .append(':')
              .append(result.beforeScore())
              .append(':')
              .append(result.candidateScore())
              .append(':')
              .append(result.afterScore())
              .append(':')
              .append(result.bestBeforeScore())
              .append(':')
              .append(result.bestAfterScore())
              .append(':')
              .append(result.destroyedCount())
              .append(':')
              .append(result.recoveryCount())
              .append(':')
              .append(result.probeCount())
              .append(':')
              .append(
                  MoveThreadingWorkload.fingerprint(workload.state(stepScope.getWorkingSolution())))
              .append('\n');
        }
      }
    }
  }

  record Result(
      long setupNanos,
      long solveNanos,
      long trials,
      long probes,
      long scoreCalculations,
      long initialScore,
      long bestScore,
      String stateFingerprint,
      String traceFingerprint,
      long gcCount,
      long gcMillis,
      long usedHeapBytes,
      long cpuNanos,
      String diagnostics,
      long phaseNanos,
      long verificationGcCount,
      long verificationGcMillis,
      boolean externalDeadlineFired,
      long terminationRequestedNanos,
      String attemptDiagnostics) {}
}
