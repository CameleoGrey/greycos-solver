package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.controller;
import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;

import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

/**
 * Fresh-JVM smoke/qualification entry point. Timings are observations, not an adoption gate.
 * Invocation and accounting limitations are documented in resumable-search-benchmark.md.
 */
public final class ResumableSearchBenchmark {
  private ResumableSearchBenchmark() {}

  enum Mode {
    SERIAL,
    MOVE_THREADS,
    ISLANDS,
    INDEPENDENT_ISLANDS,
    SESSIONS,
    COOPERATIVE
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 10 || args.length > 12)
      throw new IllegalArgumentException(
          "Usage: <basic|list|timetable> <mode> <la|dla|gls> <seed> <size>"
              + " <workers> <logicalWidth> <quantum> <steps|moves|scores|seconds> <budget> [warmupSeconds] [trace]");
    var options =
        new Options(
            args[0],
            Mode.valueOf(args[1]),
            args[2],
            Long.parseLong(args[3]),
            Integer.parseInt(args[4]),
            Integer.parseInt(args[5]),
            Integer.parseInt(args[6]),
            Integer.parseInt(args[7]),
            args[8],
            Long.parseLong(args[9]),
            args.length > 10 ? Integer.parseInt(args[10]) : 1,
            args.length > 11 && Boolean.parseBoolean(args[11]));
    if (options.workers < 1
        || options.width < 1
        || options.quantum < 1
        || options.budget < 1
        || options.warmupSeconds < 0)
      throw new IllegalArgumentException(
          "Workers, width, quantum and budget must be positive; warmup nonnegative.");
    if (options.trace && (options.mode == Mode.ISLANDS || options.mode == Mode.INDEPENDENT_ISLANDS))
      throw new IllegalArgumentException(
          "Legacy island trajectories are not exposed by this trace observer.");
    var workload = ResumableSearchWorkloads.named(options.workload);
    if (options.warmupSeconds > 0)
      run(workload, options.withBudget("seconds", options.warmupSeconds, false));
    var result = run(workload, options);
    System.out.println(
        "workload,mode,controller,seed,size,workers,logical_width,quantum,budget_unit,budget,"
            + "warmup_seconds,trace_enabled,setup_ms,solve_ms,cpu_ms,completed_steps,consumed_moves,"
            + "reported_score_calculations,budget_overshoot,physical_scores,physical_score_categories,best_score,initial_state_sha256,state_sha256,trace_sha256,"
            + "config_sha256,gc_count,gc_ms,used_heap_bytes,max_heap_bytes,processors,java_version,os_arch,"
            + "revision,core_location,validation");
    System.out.printf(
        Locale.ROOT,
        "%s,%s,%s,%d,%d,%d,%d,%d,%s,%d,%d,%s,%.3f,%.3f,%.3f,%d,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%s,%s,%s,%s,passed%n",
        options.workload,
        options.mode,
        options.controller,
        options.seed,
        options.size,
        options.effectiveWorkers(),
        options.effectiveWidth(),
        options.quantum,
        options.unit,
        options.budget,
        options.warmupSeconds,
        options.trace,
        result.setupNanos / 1_000_000.0,
        result.solveNanos / 1_000_000.0,
        result.cpuNanos < 0 ? -1.0 : result.cpuNanos / 1_000_000.0,
        result.steps,
        result.moves,
        result.calculations,
        result.overshoot,
        result.physicalScores,
        csv(result.physicalCategories),
        csv(result.score),
        result.inputHash,
        result.stateHash,
        result.traceHash,
        result.configHash,
        result.gcCount,
        result.gcMillis,
        result.usedHeap,
        Runtime.getRuntime().maxMemory(),
        Runtime.getRuntime().availableProcessors(),
        csv(System.getProperty("java.version")),
        csv(System.getProperty("os.name") + "/" + System.getProperty("os.arch")),
        csv(System.getProperty("greycos.benchmark.revision", "unrecorded")),
        csv(DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation().toString()));
  }

  private static <Solution_> Result run(Workload<Solution_> workload, Options options)
      throws Exception {
    long setupStart = System.nanoTime();
    var input = workload.createProblem(options.size);
    String inputHash = MoveThreadingWorkload.fingerprint(workload.state(input));
    var config = config(workload, options);
    var configWriter = new StringWriter();
    new SolverConfigIO().write(config, configWriter);
    String configHash = MoveThreadingWorkload.fingerprint(configWriter.toString());
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    long[] ordinarySteps = {0};
    long[] ordinaryPhaseWork = {-1, -1};
    var trace = new StringBuilder();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> step) {
            if (step instanceof LocalSearchStepScope<Solution_> local) {
              ordinarySteps[0]++;
              if (options.trace)
                trace
                    .append(local.getStepIndex())
                    .append(':')
                    .append(ResumableSearchTestSupport.move(local.getStep()))
                    .append(':')
                    .append(local.getScore())
                    .append(':')
                    .append(local.getSelectedMoveCount())
                    .append(':')
                    .append(local.getAcceptedMoveCount())
                    .append(':')
                    .append(workload.state(step.getWorkingSolution()))
                    .append('\n');
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<Solution_> phase) {
            if (phase instanceof LocalSearchPhaseScope<?>) {
              ordinaryPhaseWork[0] = phase.getPhaseMoveEvaluationCount();
              ordinaryPhaseWork[1] = phase.getPhaseScoreCalculationCount();
            }
          }
        });
    boolean sessions = options.mode == Mode.SESSIONS || options.mode == Mode.COOPERATIVE;
    var completion = new AtomicReference<ResumableSearchRuntime.CompletionSnapshot<?>>();
    AutoCloseable completed =
        sessions ? ResumableSearchRuntime.observeCompletion(completion::set) : () -> {};
    AutoCloseable observation =
        sessions && options.trace
            ? ResumableSearchRuntime.observeRounds(
                round -> {
                  trace
                      .append("round:")
                      .append(round.round())
                      .append(':')
                      .append(round.budgetConsumption())
                      .append('\n');
                  for (var session : round.sessions()) {
                    trace
                        .append(session.path())
                        .append(':')
                        .append(session.episode())
                        .append(':')
                        .append(session.completedSteps())
                        .append(':')
                        .append(session.moveEvaluations())
                        .append(':')
                        .append(session.scoreCalculations())
                        .append(':')
                        .append(session.bestScore())
                        .append(':')
                        .append(session.finished())
                        .append('\n');
                    for (var step : session.steps()) {
                      var solution = (Solution_) step.solution();
                      var replay = workload.recompute(solution);
                      if (!step.score().raw().equals(replay))
                        throw new IllegalStateException(
                            "Independent replay disagrees with a completed-step score.");
                      trace
                          .append(step.phaseIndex())
                          .append(':')
                          .append(step.stepIndex())
                          .append(':')
                          .append(step.moveDescription())
                          .append(':')
                          .append(step.score())
                          .append(':')
                          .append(replay)
                          .append(':')
                          .append(workload.state(solution))
                          .append('\n');
                    }
                  }
                },
                options.trace)
            : () -> {};
    var deadline =
        options.unit.equals("seconds") ? Executors.newSingleThreadScheduledExecutor() : null;
    long setupNanos = System.nanoTime() - setupStart;
    long gcBefore = gcCount();
    long gcMillisBefore = gcMillis();
    long cpuBefore = cpuNanos();
    long solveStart = System.nanoTime();
    Solution_ result;
    try (observation;
        completed) {
      if (deadline != null)
        deadline.schedule(
            () -> {
              solver.terminateEarly();
            },
            options.budget,
            TimeUnit.SECONDS);
      result = solver.solve(input);
    } finally {
      if (deadline != null) {
        deadline.shutdownNow();
        if (!deadline.awaitTermination(5, TimeUnit.SECONDS))
          throw new IllegalStateException("Benchmark deadline scheduler did not terminate.");
      }
    }
    long solveNanos = System.nanoTime() - solveStart;
    long cpuAfter = cpuNanos();
    var replay = workload.recompute(result);
    if (!replay.equals(workload.score(result)))
      throw new IllegalStateException("Independent replay disagrees with the returned score.");
    if (sessions && completion.get() == null)
      throw new IllegalStateException("The resumable phase did not report final work accounting.");
    long steps =
        sessions
            ? completion.get().budgetConsumption().completedSteps()
            : options.mode == Mode.SERIAL || options.mode == Mode.MOVE_THREADS
                ? ordinarySteps[0]
                : -1;
    long moves = solver.getMoveEvaluationCount();
    long calculations = solver.getScoreCalculationCount();
    long consumed =
        switch (options.unit) {
          case "steps" -> steps;
          case "moves" ->
              sessions
                  ? completion.get().budgetConsumption().moveEvaluations()
                  : ordinaryPhaseWork[0];
          case "scores" ->
              sessions
                  ? completion.get().budgetConsumption().scoreCalculations()
                  : ordinaryPhaseWork[1];
          default -> -1;
        };
    long physicalScores =
        sessions ? completion.get().physicalWork().totalScoreCalculationCount() : -1;
    String categories =
        sessions
            ? completion.get().physicalWork().scoreCalculationCountsByCategory().toString()
            : "unavailable";
    return new Result(
        setupNanos,
        solveNanos,
        cpuBefore < 0 || cpuAfter < 0 ? -1 : cpuAfter - cpuBefore,
        steps,
        moves,
        calculations,
        consumed < 0 ? -1 : Math.max(0, consumed - options.budget),
        physicalScores,
        categories,
        replay.toString(),
        inputHash,
        MoveThreadingWorkload.fingerprint(workload.state(result)),
        options.trace ? MoveThreadingWorkload.fingerprint(trace.toString()) : "",
        configHash,
        gcCount() - gcBefore,
        gcMillis() - gcMillisBefore,
        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
  }

  private static SolverConfig config(Workload<?> workload, Options options) {
    var config =
        workload.solverConfig(
            "NONE", options.seed, 1, new TerminationConfig(), EnvironmentMode.NO_ASSERT);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    controller(config, search, options.controller);
    var limit =
        switch (options.unit) {
          case "steps" ->
              new TerminationConfig().withStepCountLimit(Math.toIntExact(options.budget));
          case "moves" -> new TerminationConfig().withMoveCountLimit(options.budget);
          case "scores" -> new TerminationConfig().withScoreCalculationCountLimit(options.budget);
          case "seconds" -> null; // One external deadline includes session/worker initialization.
          default ->
              throw new IllegalArgumentException("Unknown budget unit (" + options.unit + ").");
        };
    search.setTerminationConfig(null);
    switch (options.mode) {
      case SERIAL -> search.setTerminationConfig(limit);
      case MOVE_THREADS -> {
        config.setMoveThreadCount(Integer.toString(options.workers));
        search.setTerminationConfig(limit);
      }
      case ISLANDS, INDEPENDENT_ISLANDS -> {
        if (options.unit.equals("steps")) {
          if (options.budget % options.width != 0)
            throw new IllegalArgumentException(
                "Fixed-step legacy-island budgets must divide evenly by logical width.");
          search.setTerminationConfig(
              new TerminationConfig()
                  .withStepCountLimit(Math.toIntExact(options.budget / options.width)));
        } else if (!options.unit.equals("seconds")) {
          throw new IllegalArgumentException(
              "Use steps or seconds for legacy-island comparisons; its move/score quotas differ.");
        }
        config.withPhases(
            new IslandModelPhaseConfig()
                .withIslandCount(options.width)
                .withMoveThreadCount("NONE")
                .withMigrationFrequency(Integer.MAX_VALUE)
                .withCompareGlobalEnabled(options.mode == Mode.ISLANDS)
                .withReceiveGlobalUpdateFrequency(400)
                .withPhaseConfigList(List.of(search)));
      }
      case SESSIONS, COOPERATIVE -> {
        var phase =
            sessions(
                search,
                options.width,
                options.workers,
                options.quantum,
                options.mode == Mode.COOPERATIVE
                    ? ResumableSearchCooperationPolicy.RESTART_WEAKEST
                    : ResumableSearchCooperationPolicy.NONE);
        phase.setTerminationConfig(limit);
        config.withPhases(phase);
      }
    }
    return config;
  }

  private static long cpuNanos() {
    return ProcessHandle.current().info().totalCpuDuration().map(Duration::toNanos).orElse(-1L);
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

  private static String csv(String value) {
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private record Options(
      String workload,
      Mode mode,
      String controller,
      long seed,
      int size,
      int workers,
      int width,
      int quantum,
      String unit,
      long budget,
      int warmupSeconds,
      boolean trace) {
    Options withBudget(String unit, long budget, boolean trace) {
      return new Options(
          workload, mode, controller, seed, size, workers, width, quantum, unit, budget, 0, trace);
    }

    int effectiveWidth() {
      return mode == Mode.SERIAL || mode == Mode.MOVE_THREADS ? 1 : width;
    }

    int effectiveWorkers() {
      return switch (mode) {
        case SERIAL -> 1;
        case ISLANDS, INDEPENDENT_ISLANDS -> width;
        default -> workers;
      };
    }
  }

  private record Result(
      long setupNanos,
      long solveNanos,
      long cpuNanos,
      long steps,
      long moves,
      long calculations,
      long overshoot,
      long physicalScores,
      String physicalCategories,
      String score,
      String inputHash,
      String stateHash,
      String traceHash,
      String configHash,
      long gcCount,
      long gcMillis,
      long usedHeap) {}
}
