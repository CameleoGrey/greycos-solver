package greycos.solver.core.impl.heuristic.thread;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

/**
 * Opt-in, fresh-JVM qualification harness. See docs/constraint-profiling/README.md. Candidate-only
 * profile access uses reflection so the identical harness also compiles against the pinned
 * baseline.
 */
public final class ConstraintProfilingBenchmark {

  private ConstraintProfilingBenchmark() {}

  public static void main(String[] args) throws ReflectiveOperationException {
    if (args.length != 7) {
      throw new IllegalArgumentException(
          "Usage: <basic|list> <size> <seed> <profiling> <warmupSeconds> <measureSeconds> <fixedMoves>");
    }
    String name = args[0];
    int size = Integer.parseInt(args[1]);
    long seed = Long.parseLong(args[2]);
    if (!args[3].equals("true") && !args[3].equals("false")) {
      throw new IllegalArgumentException("Profiling (" + args[3] + ") must be true or false.");
    }
    boolean profiling = Boolean.parseBoolean(args[3]);
    int warmupSeconds = Integer.parseInt(args[4]);
    int measureSeconds = Integer.parseInt(args[5]);
    int fixedMoves = Integer.parseInt(args[6]);
    if (size < 4 || warmupSeconds < 1 || measureSeconds < 1 || fixedMoves < 1) {
      throw new IllegalArgumentException(
          "Size must be at least 4; durations and fixedMoves must be positive.");
    }
    var context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.reset();
    context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
    var workload = MoveThreadingWorkload.named(name);
    var result = new LinkedHashMap<String, Object>();
    result.put("schema_version", 1);
    result.put("workload", name);
    result.put("size", size);
    result.put("seed", seed);
    result.put("profiling", profiling);
    result.put("accepted_count", 32);
    result.put("move_thread_count", "NONE");
    result.put("environment_mode", "NO_ASSERT");
    result.put("warmup_seconds", warmupSeconds);
    result.put("measure_seconds", measureSeconds);
    result.put("termination_guard_millis", 10);
    result.put("fixed_move_limit", fixedMoves);
    result.put("java_version", System.getProperty("java.runtime.version"));
    result.put("java_home", System.getProperty("java.home"));
    result.put("jvm_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
    result.put("available_processors", Runtime.getRuntime().availableProcessors());
    result.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
    result.put(
        "core_location",
        DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation().toString());
    result.put("warmup", run(workload, size, seed, profiling, warmupSeconds, 0, false));
    System.err.println("Warmup solve and replay completed.");
    result.put("measurement", run(workload, size, seed, profiling, measureSeconds, 0, true));
    System.err.println("Measurement solve, replay and rendering diagnostics completed.");
    result.put("fixed_work", run(workload, size, seed, profiling, 0, fixedMoves, true));
    System.err.println("Fixed-work solve, trace, replay and rendering diagnostics completed.");
    System.out.println(json(result));
  }

  private static <Solution_> Map<String, Object> run(
      Workload<Solution_> workload,
      int size,
      long seed,
      boolean profiling,
      int seconds,
      int fixedMoves,
      boolean diagnostics)
      throws ReflectiveOperationException {
    var problem = workload.createProblem(size);
    long initialScore = workload.recompute(problem).score();
    var termination = new TerminationConfig();
    if (fixedMoves > 0) {
      termination.setMoveCountLimit((long) fixedMoves);
    } else {
      // Phase termination has millisecond resolution; retain a guard over the requested minimum.
      termination.setSpentLimit(Duration.ofMillis(seconds * 1000L + 10));
    }
    var config = workload.solverConfig("NONE", seed, 32, termination, EnvironmentMode.NO_ASSERT);
    config.getScoreDirectorFactoryConfig().setConstraintStreamProfilingEnabled(profiling);
    long factoryStart = System.nanoTime();
    var factory = SolverFactory.<Solution_>create(config);
    long factoryNanos = System.nanoTime() - factoryStart;
    long buildStart = System.nanoTime();
    var solver = (DefaultSolver<Solution_>) factory.buildSolver();
    long buildNanos = System.nanoTime() - buildStart;
    var recorder = new Recorder<Solution_>(workload, fixedMoves > 0);
    solver.addPhaseLifecycleListener(recorder);
    long beforeGcCount = gcCount();
    long beforeGcMillis = gcMillis();
    long beforeHeap = usedHeap();
    long beforeAllocated = allocatedBytes();
    long start = System.nanoTime();
    var solution = solver.solve(problem);
    long solveNanos = System.nanoTime() - start;
    long afterAllocated = allocatedBytes();
    long afterHeap = usedHeap();
    long gcCount = gcCount() - beforeGcCount;
    long gcMillis = gcMillis() - beforeGcMillis;
    var independentScore = workload.recompute(solution);
    if (!independentScore.equals(workload.score(solution))) {
      throw new IllegalStateException(
          "Independent score "
              + independentScore
              + " differs from solver score "
              + workload.score(solution));
    }
    if (fixedMoves > 0 && solver.getMoveEvaluationCount() != fixedMoves) {
      throw new IllegalStateException(
          "Expected fixedMoves="
              + fixedMoves
              + " but evaluated moves="
              + solver.getMoveEvaluationCount());
    }
    Object profile = optionalProfile(solver);
    if (profiling && profile == null) {
      throw new IllegalStateException("Profiling enabled but no finalized profile was published.");
    }
    if (!profiling && profile != null) {
      throw new IllegalStateException("Profiling disabled but a profile was published.");
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("factory_nanos", factoryNanos);
    result.put("solver_build_nanos", buildNanos);
    result.put("solve_nanos", solveNanos);
    result.put("solve_to_phase_start_nanos", recorder.phaseStart - start);
    result.put("score_calculations", solver.getScoreCalculationCount());
    result.put("move_evaluations", solver.getMoveEvaluationCount());
    result.put(
        "score_calculations_per_second",
        solver.getScoreCalculationCount() * 1_000_000_000.0 / solveNanos);
    result.put("moves_per_second", solver.getMoveEvaluationCount() * 1_000_000_000.0 / solveNanos);
    result.put("steps", recorder.steps);
    result.put("initial_score", initialScore);
    result.put("best_score", independentScore.score());
    result.put("independent_replay_passed", true);
    result.put("state", workload.state(solution));
    result.put("state_sha256", MoveThreadingWorkload.fingerprint(workload.state(solution)));
    result.put(
        "trace_sha256",
        fixedMoves > 0 ? MoveThreadingWorkload.fingerprint(recorder.trace.toString()) : null);
    result.put("trace", fixedMoves > 0 ? recorder.trace.toString() : null);
    result.put(
        "allocated_bytes",
        beforeAllocated < 0 || afterAllocated < 0 ? null : afterAllocated - beforeAllocated);
    result.put("heap_before_bytes", beforeHeap);
    result.put("heap_after_bytes", afterHeap);
    result.put("gc_count", gcCount);
    result.put("gc_millis", gcMillis);
    result.put(
        "profile_freeze_nanos", optionalDiagnostic(solver, "getConstraintProfileFreezeNanos"));
    result.put(
        "profile_report_dispatch_nanos",
        optionalDiagnostic(solver, "getConstraintProfileReportNanos"));
    result.put("profile", profile);
    if (diagnostics && profile != null) {
      result.put("rendering", render(profile));
    }
    return result;
  }

  private static Object optionalProfile(DefaultSolver<?> solver)
      throws ReflectiveOperationException {
    try {
      return ((Optional<?>) solver.getClass().getMethod("getConstraintProfile").invoke(solver))
          .orElse(null);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static Object optionalDiagnostic(DefaultSolver<?> solver, String name)
      throws ReflectiveOperationException {
    try {
      return solver.getClass().getMethod(name).invoke(solver);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static Map<String, Object> render(Object profile) throws ReflectiveOperationException {
    var renderer = Class.forName("greycos.solver.core.impl.bavet.common.ConstraintProfileRenderer");
    var report = renderer.getDeclaredMethod("report", profile.getClass(), Logger.class);
    report.setAccessible(true);
    var result = new LinkedHashMap<String, Object>();
    for (String level : List.of("INFO", "DEBUG", "TRACE")) {
      var sink = new ByteArrayOutputStream();
      var logger =
          (Logger)
              Proxy.newProxyInstance(
                  Logger.class.getClassLoader(),
                  new Class<?>[] {Logger.class},
                  (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getName")) {
                      return "constraint-profiling-benchmark-counting-sink";
                    }
                    if (name.startsWith("is")) {
                      if (name.startsWith("isTrace")) {
                        return level.equals("TRACE");
                      }
                      return !name.startsWith("isDebug") || !level.equals("INFO");
                    }
                    if (args != null && args.length > 0 && args[0] instanceof String pattern) {
                      Object[] parameters =
                          args.length == 2 && args[1] instanceof Object[] array
                              ? array
                              : java.util.Arrays.copyOfRange(args, 1, args.length);
                      sink.writeBytes(
                          MessageFormatter.arrayFormat(pattern, parameters)
                              .getMessage()
                              .getBytes(StandardCharsets.UTF_8));
                      sink.write('\n');
                    }
                    return null;
                  });
      // Warm formatting separately; this work never enters the solve-throughput interval.
      report.invoke(null, profile, logger);
      sink.reset();
      long start = System.nanoTime();
      report.invoke(null, profile, logger);
      long nanos = System.nanoTime() - start;
      result.put(
          level,
          Map.of(
              "nanos",
              nanos,
              "utf8_bytes",
              sink.size(),
              "text",
              sink.toString(StandardCharsets.UTF_8)));
    }
    return result;
  }

  private static long allocatedBytes() {
    if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
        && bean.isThreadAllocatedMemorySupported()) {
      if (!bean.isThreadAllocatedMemoryEnabled()) {
        bean.setThreadAllocatedMemoryEnabled(true);
      }
      return bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }
    return -1;
  }

  private static long usedHeap() {
    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
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

  private static String json(Object value) throws ReflectiveOperationException {
    if (value == null) {
      return "null";
    }
    if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }
    if (value instanceof CharSequence || value instanceof Enum<?>) {
      var out = new StringBuilder("\"");
      for (char character : value.toString().toCharArray()) {
        switch (character) {
          case '\\' -> out.append("\\\\");
          case '"' -> out.append("\\\"");
          case '\n' -> out.append("\\n");
          case '\r' -> out.append("\\r");
          case '\t' -> out.append("\\t");
          default -> {
            if (character < 0x20) {
              out.append(String.format("\\u%04x", (int) character));
            } else {
              out.append(character);
            }
          }
        }
      }
      return out.append('"').toString();
    }
    if (value.getClass().isRecord()) {
      var fields = new LinkedHashMap<String, Object>();
      for (var component : value.getClass().getRecordComponents()) {
        fields.put(component.getName(), component.getAccessor().invoke(value));
      }
      return json(fields);
    }
    if (value instanceof Map<?, ?> map) {
      var out = new StringBuilder("{");
      for (var entry : map.entrySet()) {
        if (out.length() > 1) {
          out.append(',');
        }
        out.append(json(entry.getKey().toString())).append(':').append(json(entry.getValue()));
      }
      return out.append('}').toString();
    }
    if (value instanceof Iterable<?> iterable) {
      var out = new StringBuilder("[");
      for (Object element : iterable) {
        if (out.length() > 1) {
          out.append(',');
        }
        out.append(json(element));
      }
      return out.append(']').toString();
    }
    if (value.getClass().isArray()) {
      var out = new StringBuilder("[");
      for (int i = 0; i < Array.getLength(value); i++) {
        if (i > 0) {
          out.append(',');
        }
        out.append(json(Array.get(value, i)));
      }
      return out.append(']').toString();
    }
    throw new IllegalArgumentException(
        "Unsupported benchmark JSON value type " + value.getClass().getName());
  }

  private static final class Recorder<Solution_> extends PhaseLifecycleListenerAdapter<Solution_> {
    private final Workload<Solution_> workload;
    private final boolean recordTrace;
    private final StringBuilder trace = new StringBuilder();
    private long phaseStart;
    private long steps;

    private Recorder(Workload<Solution_> workload, boolean recordTrace) {
      this.workload = workload;
      this.recordTrace = recordTrace;
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
      phaseStart = System.nanoTime();
    }

    @Override
    public void stepEnded(AbstractStepScope<Solution_> stepScope) {
      if (stepScope instanceof LocalSearchStepScope<Solution_> step) {
        steps++;
        if (recordTrace) {
          trace
              .append(step.getStepIndex())
              .append(':')
              .append(step.getStep())
              .append(':')
              .append(step.getScore().raw())
              .append(':')
              .append(step.getSelectedMoveCount())
              .append(':')
              .append(step.getAcceptedMoveCount())
              .append(':')
              .append(MoveThreadingWorkload.fingerprint(workload.state(step.getWorkingSolution())))
              .append('\n');
        }
      }
    }
  }
}
