package greycos.solver.core.impl.iteratedlocalsearch;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload.MixedSolution;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * One fresh-JVM run with independent assignment replay. Invoked by the tracked ils-vns runner;
 * deliberately has no JUnit entry point and does not run in the normal test suite.
 */
public final class IteratedLocalSearchBenchmark {
  private IteratedLocalSearchBenchmark() {}

  public interface Adapter<S> {
    S load() throws Exception;

    SolverConfig config();

    Score<?> score(S solution);

    /**
     * Must validate completeness, canonical IDs and shadows, independently of the score director.
     */
    Score<?> replay(S solution);

    String assignments(S solution);

    /** Reconstruct from freshly loaded facts, never from the returned solver graph. */
    S reconstruct(String assignments) throws Exception;

    boolean needsConstruction();

    String temperature(boolean large);
  }

  public record Options(
      String profile,
      long seed,
      int seconds,
      String threads,
      int islands,
      long moves,
      Path output) {
    public static Options parse(String[] args, int offset) {
      if (args.length != offset + 7) {
        throw new IllegalArgumentException(
            "Expected <profile> <seed> <seconds> <NONE|workers> <islands> <move-limit|0>"
                + " <output-dir>.");
      }
      var options =
          new Options(
              args[offset],
              Long.parseLong(args[offset + 1]),
              Integer.parseInt(args[offset + 2]),
              args[offset + 3],
              Integer.parseInt(args[offset + 4]),
              Long.parseLong(args[offset + 5]),
              Path.of(args[offset + 6]));
      if (options.seconds < 1
          || options.seconds > 120
          || options.islands < 1
          || options.islands > 2
          || options.moves < 0
          || (!options.threads.equals("NONE") && Integer.parseInt(options.threads) < 1)) {
        throw new IllegalArgumentException("Invalid bounded benchmark options: " + options);
      }
      return options;
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 8) {
      throw new IllegalArgumentException("Expected <mixed-size> followed by benchmark options.");
    }
    run(new MixedAdapter(Integer.parseInt(args[0])), Options.parse(args, 1));
  }

  public static <S> void run(Adapter<S> adapter, Options options) throws Exception {
    var diagnosticRegistry = diagnosticsEnabled() ? new SimpleMeterRegistry() : null;
    if (diagnosticRegistry != null) {
      Metrics.addRegistry(diagnosticRegistry);
    }
    try {
      run(adapter, options, diagnosticRegistry == null ? null : new FinalMetricSamples());
    } finally {
      if (diagnosticRegistry != null) {
        Metrics.removeRegistry(diagnosticRegistry);
        diagnosticRegistry.close();
      }
    }
  }

  private static <S> void run(Adapter<S> adapter, Options options, FinalMetricSamples finalSamples)
      throws Exception {
    Files.createDirectories(options.output);
    long warmupNanos = warmup(adapter, options);
    long setupStarted = System.nanoTime();
    S problem = adapter.load();
    String initialState = adapter.assignments(problem);
    var config = configure(adapter, options);
    try (var writer = Files.newBufferedWriter(options.output.resolve("solver.xml"))) {
      new SolverConfigIO().write(config, writer);
    }
    var solver = (DefaultSolver<S>) SolverFactory.<S>create(config).buildSolver();
    if (finalSamples != null) {
      solver.getSolverScope().addMetricSampleListener(finalSamples);
    }
    var firstFeasible = new AtomicLong(-1);
    var improvements = new ArrayList<String>();
    solver.addEventListener(
        event -> {
          // No replay, hashing, polling or formatting of complete solutions inside the timed solve.
          if (event.isNewBestSolutionInitialized() && event.getNewBestScore().isFeasible()) {
            firstFeasible.compareAndSet(-1, event.getTimeMillisSpent());
          }
          improvements.add(
              event.getTimeMillisSpent()
                  + "\t"
                  + event.isNewBestSolutionInitialized()
                  + "\t"
                  + event.getNewBestScore());
        });
    if (!adapter.needsConstruction() && adapter.replay(problem).isFeasible()) {
      firstFeasible.set(0);
    }
    var phaseTimes = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private long started;

          @Override
          public void phaseStarted(AbstractPhaseScope<S> scope) {
            started = System.nanoTime();
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<S> scope) {
            phaseTimes.add(scope.getClass().getSimpleName() + "\t" + (System.nanoTime() - started));
          }
        });
    long setupNanos = System.nanoTime() - setupStarted;
    long beforeGc = gcCount();
    long beforeGcMillis = gcMillis();
    long beforeCpu = cpuNanos();
    ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> pool.resetPeakUsage());
    long start = System.nanoTime();
    S result;
    try {
      result = solver.solve(problem);
    } finally {
      if (finalSamples != null) {
        solver.getSolverScope().removeMetricSampleListener(finalSamples);
      }
    }
    long solveNanos = System.nanoTime() - start;
    long cpuNanos = cpuNanos() - beforeCpu;
    long gcCount = gcCount() - beforeGc;
    long gcMillis = gcMillis() - beforeGcMillis;
    long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    long peakHeap =
        ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP)
            .mapToLong(pool -> pool.getPeakUsage().getUsed())
            .sum();
    long verificationStarted = System.nanoTime();
    var originalScore = adapter.score(result);
    var replayScore = adapter.replay(result);
    require(originalScore.equals(replayScore), "Returned score disagrees with independent replay.");
    var assignments = adapter.assignments(result);
    Files.writeString(options.output.resolve("assignments.tsv"), assignments);
    var reconstructed =
        adapter.reconstruct(Files.readString(options.output.resolve("assignments.tsv")));
    require(
        assignments.equals(adapter.assignments(reconstructed)),
        "Persisted assignment round trip differs.");
    require(
        replayScore.equals(adapter.replay(reconstructed)),
        "Reconstructed independent score differs.");
    var recalculated =
        SolutionManager.create(SolverFactory.<S>create(config)).update(reconstructed);
    require(
        replayScore.equals(recalculated),
        "Fresh native score disagrees with persisted independent replay.");
    var metrics = new TreeMap<String, String>();
    metrics.put("profile", options.profile);
    metrics.put("seed", Long.toString(options.seed));
    metrics.put("budget_seconds", Integer.toString(options.seconds));
    metrics.put("move_limit_per_island", Long.toString(options.moves));
    metrics.put("move_workers_per_island", options.threads);
    metrics.put("islands", Integer.toString(options.islands));
    metrics.put(
        "coordinator_and_move_threads",
        Integer.toString(
            options.islands
                * (1 + (options.threads.equals("NONE") ? 0 : Integer.parseInt(options.threads)))));
    metrics.put("setup_nanos", Long.toString(setupNanos));
    metrics.put("warmup_nanos", Long.toString(warmupNanos));
    metrics.put(
        "warmup_policy",
        "2_second_same_profile_seed_separate_problem_and_solver_excluded_from_measurement");
    metrics.put("solve_nanos", Long.toString(solveNanos));
    metrics.put("verification_nanos", Long.toString(System.nanoTime() - verificationStarted));
    metrics.put("process_cpu_nanos", Long.toString(cpuNanos));
    metrics.put("gc_count", Long.toString(gcCount));
    metrics.put("gc_millis", Long.toString(gcMillis));
    metrics.put("heap_end_bytes", Long.toString(heap));
    metrics.put("peak_heap_pool_sum_bytes", Long.toString(peakHeap));
    metrics.put(
        "peak_heap_scope",
        "sum_of_pool_peaks_during_measured_solve_including_retained_warmup_objects");
    metrics.put("first_feasible_millis", Long.toString(firstFeasible.get()));
    metrics.put("complete", "true");
    metrics.put("feasible", Boolean.toString(replayScore.isFeasible()));
    metrics.put("native_score", originalScore.toString());
    metrics.put(
        "score_levels",
        Arrays.stream(originalScore.toLevelNumbers())
            .map(Object::toString)
            .collect(Collectors.joining(",")));
    metrics.put("state_sha256", MoveThreadingWorkload.fingerprint(assignments));
    metrics.put("initial_state_sha256", MoveThreadingWorkload.fingerprint(initialState));
    metrics.put("independent_replay", "passed");
    metrics.put("persistence_replay", "passed");
    metrics.put("move_evaluations", Long.toString(solver.getMoveEvaluationCount()));
    metrics.put("reported_score_calculations", Long.toString(solver.getScoreCalculationCount()));
    metrics.put(
        "coordinator_and_consumed_score_calculations",
        Long.toString(solver.getSolverScope().getScoreDirector().getCalculationCount()));
    metrics.put(
        "additional_child_score_calculations",
        Long.toString(
            solver.getScoreCalculationCount()
                - solver.getSolverScope().getScoreDirector().getCalculationCount()));
    metrics.put("score_calculation_definition", "solver_reported_including_reconciled_child_work");
    metrics.put("java_version", System.getProperty("java.version"));
    metrics.put("processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
    metrics.put("max_heap_bytes", Long.toString(Runtime.getRuntime().maxMemory()));
    metrics.put(
        "core_location",
        DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation().toString());
    metrics.put(
        "diagnostic_timing_enabled",
        Boolean.toString(Boolean.getBoolean("greycos.solver.moveThreadDiagnostics")));
    metrics.put(
        "ils_diagnostic_timing_enabled",
        Boolean.toString(Boolean.getBoolean("greycos.solver.iteratedLocalSearchDiagnostics")));
    for (int i = 0; i < solver.getPhaseList().size(); i++) {
      readDiagnostics(solver.getPhaseList().get(i), "phase." + i, metrics);
    }
    if (finalSamples != null) {
      var rows = finalSamples.rows();
      Files.write(options.output.resolve("diagnostic-final-samples.tsv"), rows);
      metrics.put("diagnostic_final_producer_count", Integer.toString(finalSamples.samples.size()));
      metrics.put("diagnostic_final_ils_gauge_count", Integer.toString(rows.size() - 1));
      metrics.put(
          "diagnostic_metric_instrumentation", "ILS_statistics_gauges_and_FINAL_sample_listener");
    }
    Files.write(options.output.resolve("improvements.tsv"), improvements);
    Files.write(options.output.resolve("phase-times.tsv"), phaseTimes);
    Files.write(
        options.output.resolve("result.properties"),
        metrics.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
    System.out.println(
        "Verified " + options.profile + ": " + originalScore + " " + metrics.get("state_sha256"));
  }

  private static <S> long warmup(Adapter<S> adapter, Options options) throws Exception {
    var warmupOptions =
        new Options(
            options.profile,
            options.seed,
            2,
            options.threads,
            options.islands,
            options.moves,
            options.output);
    long started = System.nanoTime();
    var solver = SolverFactory.<S>create(configure(adapter, warmupOptions)).buildSolver();
    solver.solve(adapter.load());
    return System.nanoTime() - started;
  }

  public static SolverConfig configure(Adapter<?> adapter, Options options) {
    var config =
        adapter
            .config()
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(options.seed)
            .withMoveThreadCount(options.threads)
            .withMoveThreadBufferSize(4)
            .withMonitoringConfig(monitoringConfig(diagnosticsEnabled()))
            .withTerminationConfig(
                new TerminationConfig().withSpentLimit(Duration.ofSeconds(options.seconds)));
    var phases = new ArrayList<PhaseConfig<?>>();
    if (adapter.needsConstruction()) {
      phases.add(new ConstructionHeuristicPhaseConfig());
    }
    var searches = searchPhases(adapter, options);
    if (options.moves > 0) {
      for (var phase : searches) {
        phase.setTerminationConfig(new TerminationConfig().withMoveCountLimit(options.moves));
      }
    }
    if (options.islands > 1) {
      phases.add(
          new IslandModelPhaseConfig()
              .withIslandCount(options.islands)
              .withMoveThreadCount(options.threads)
              .withCompareGlobalEnabled(true)
              .withMigrationFrequency(200)
              .withReceiveGlobalUpdateFrequency(200)
              .withPhaseConfigList(searches));
    } else {
      phases.addAll(searches);
    }
    return config.withPhases(phases.toArray(PhaseConfig[]::new));
  }

  private static boolean diagnosticsEnabled() {
    return Boolean.getBoolean("greycos.solver.iteratedLocalSearchDiagnostics")
        || Boolean.getBoolean("greycos.solver.moveThreadDiagnostics");
  }

  static MonitoringConfig monitoringConfig(boolean diagnostics) {
    return new MonitoringConfig()
        .withSolverMetricList(
            diagnostics ? List.of(SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS) : List.of());
  }

  /**
   * Final immutable publications retain island gauges after their worker resources are released.
   */
  static final class FinalMetricSamples implements Consumer<SolverMetricSample> {
    private final Map<String, SolverMetricSample> samples = new ConcurrentHashMap<>();

    @Override
    public void accept(SolverMetricSample sample) {
      if (sample.kind() == SolverMetricSample.Kind.FINAL) {
        samples.put(sample.source(), sample);
      }
    }

    List<String> rows() {
      var rows = new ArrayList<String>();
      rows.add("producer\ttime_millis\tmetric\ttags\tvalue");
      var prefix = SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS.getMeterId() + ".";
      for (var sample : new TreeMap<>(samples).values()) {
        sample.measurements().entrySet().stream()
            .filter(entry -> entry.getKey().getName().startsWith(prefix))
            .sorted(
                java.util.Comparator.comparing(
                    entry -> entry.getKey().getName() + entry.getKey().getTags()))
            .forEach(
                entry ->
                    rows.add(
                        tsvCell(sample.source())
                            + "\t"
                            + sample.timeMillisSpent()
                            + "\t"
                            + tsvCell(entry.getKey().getName())
                            + "\t"
                            + tsvCell(entry.getKey().getTags().toString())
                            + "\t"
                            + entry.getValue()));
      }
      return rows;
    }

    private static String tsvCell(String value) {
      return value
          .replace("\\", "\\\\")
          .replace("\t", "\\t")
          .replace("\n", "\\n")
          .replace("\r", "\\r");
    }
  }

  private static List<PhaseConfig<?>> searchPhases(Adapter<?> adapter, Options options) {
    String method = options.profile.replaceFirst("-(small|large|deep|gentle)$", "");
    boolean deep = options.profile.endsWith("-deep");
    boolean gentle = options.profile.endsWith("-gentle");
    if ((deep || gentle) && !method.equals("ils") && !method.equals("vns")) {
      throw new IllegalArgumentException("Unknown profile: " + options.profile);
    }
    boolean large = options.profile.endsWith("-large") || deep;
    var ls = new LocalSearchPhaseConfig();
    var acceptor = new LocalSearchAcceptorConfig();
    var forager = new LocalSearchForagerConfig().withAcceptedCountLimit(1);
    switch (method) {
      case "la", "ils", "vns", "restarts", "shake" ->
          acceptor.withLateAcceptanceSize(large ? 256 : 64);
      case "sa" -> acceptor.withSimulatedAnnealingStartingTemperature(adapter.temperature(large));
      case "tabu" -> {
        acceptor.withEntityTabuRatio(large ? 0.10 : 0.03);
        forager.withAcceptedCountLimit(large ? 256 : 64);
      }
      case "gls" -> {
        return List.of(
            ls.withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
                .withGuidedLocalSearchConfig(
                    new GuidedLocalSearchConfig().withSampleSize(large ? 128 : 32)));
      }
      case "vnd" -> {
        return List.of(vndPhase(adapter.config()));
      }
      case "alns" -> {
        return List.of(
            AlnsMoveThreadingWorkload.phase(
                large ? 8 : 3, AlnsRepairOperatorType.GREEDY, new TerminationConfig()));
      }
      default -> throw new IllegalArgumentException("Unknown profile: " + options.profile);
    }
    ls.withAcceptorConfig(acceptor).withForagerConfig(forager);
    if (method.equals("ils") || method.equals("vns") || method.equals("shake")) {
      // Fixed and scheduled variants have exactly the same inner search and budgets.
      ls.withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64));
      if (method.equals("shake")) {
        ls.withTerminationConfig(new TerminationConfig().withStepCountLimit(0));
      }
      return List.of(
          new IteratedLocalSearchPhaseConfig()
              .withLocalSearch(ls)
              .withPerturbationStrengths(
                  method.equals("vns")
                      ? (large ? new int[] {2, 4, 8} : new int[] {1, 2, 4})
                      : new int[] {large ? 4 : 2})
              .withPerturbationAttemptLimit(1000L)
              .withEpisodeCandidateAttemptLimit(deep || gentle ? 40_000L : large ? 4000L : 1000L)
              .withMoveThreadCount(options.threads));
    }
    if (method.equals("restarts")) {
      var phases = new ArrayList<PhaseConfig<?>>();
      for (int i = 0; i < 128; i++) {
        phases.add(
            ls.copyConfig()
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1000L)));
      }
      return phases;
    }
    return List.of(ls);
  }

  static LocalSearchPhaseConfig vndPhase(SolverConfig config) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            config.getSolutionClass(), config.getEntityClassList());
    var moves = new ArrayList<MoveSelectorConfig>();
    if (!descriptor.getBasicVariableDescriptorList().isEmpty()) {
      moves.add(new ChangeMoveSelectorConfig());
      moves.add(new SwapMoveSelectorConfig());
    }
    if (descriptor.hasListVariable()) {
      moves.add(new ListChangeMoveSelectorConfig());
      moves.add(new ListSwapMoveSelectorConfig());
    }
    // K-opt has a never-ending iterator and cannot support finite VND exhaustion.
    // Generic selectors unfold against this model while preserving ORIGINAL enumeration.
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.VARIABLE_NEIGHBORHOOD_DESCENT)
        .withMoveSelectorConfig(
            new UnionMoveSelectorConfig()
                .withSelectionOrder(SelectionOrder.ORIGINAL)
                .withMoveSelectorList(moves));
  }

  private static void readDiagnostics(Object phase, String prefix, Map<String, String> metrics)
      throws ReflectiveOperationException {
    metrics.put(prefix + ".type", phase.getClass().getSimpleName());
    try {
      Object diagnostics = phase.getClass().getMethod("getDiagnostics").invoke(phase);
      if (diagnostics != null && diagnostics.getClass().isRecord()) {
        for (var component : diagnostics.getClass().getRecordComponents()) {
          metrics.put(
              prefix + "." + component.getName(),
              String.valueOf(component.getAccessor().invoke(diagnostics)));
        }
      } else {
        metrics.put(prefix + ".diagnostics", String.valueOf(diagnostics));
      }
    } catch (NoSuchMethodException ignored) {
      metrics.put(prefix + ".diagnostics", "unavailable");
    }
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

  private static long cpuNanos() {
    return ProcessHandle.current().info().totalCpuDuration().orElse(Duration.ZERO).toNanos();
  }

  public static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  public static final class MixedAdapter implements Adapter<MixedSolution> {
    private final int size;
    private final AlnsMoveThreadingWorkload.MixedWorkload workload =
        new AlnsMoveThreadingWorkload.MixedWorkload();

    public MixedAdapter(int size) {
      this.size = size;
    }

    @Override
    public MixedSolution load() {
      return workload.createProblem(size);
    }

    @Override
    public SolverConfig config() {
      return workload.solverConfig(
          "NONE", 11, 1, new TerminationConfig(), EnvironmentMode.NO_ASSERT);
    }

    @Override
    public Score<?> score(MixedSolution solution) {
      return solution.getScore();
    }

    @Override
    public Score<?> replay(MixedSolution solution) {
      return workload.recompute(solution);
    }

    @Override
    public String assignments(MixedSolution solution) {
      var out = new StringBuilder();
      for (var job : solution.getJobs()) {
        out.append("job\t")
            .append(job.getId())
            .append('\t')
            .append(job.getMachine().id())
            .append('\n');
      }
      for (var route : solution.getRoutes()) {
        out.append("route\t").append(route.getId());
        for (var visit : route.getVisits()) {
          out.append('\t').append(visit.getId());
        }
        out.append('\n');
      }
      return out.toString();
    }

    @Override
    public MixedSolution reconstruct(String assignments) {
      var solution = load();
      solution.getRoutes().forEach(route -> route.getVisits().clear());
      var jobs = new HashSet<Integer>();
      var routes = new HashSet<Integer>();
      var visits = new HashSet<Integer>();
      for (String line : assignments.lines().toList()) {
        String[] values = line.split("\t");
        int id = Integer.parseInt(values[1]);
        if (values[0].equals("job")) {
          require(values.length == 3 && jobs.add(id), "Duplicate or malformed job assignment.");
          solution
              .getJobs()
              .get(id)
              .setMachine(solution.getMachines().get(Integer.parseInt(values[2])));
        } else {
          require(
              values[0].equals("route") && routes.add(id),
              "Duplicate or unknown route assignment.");
          var route = solution.getRoutes().get(id);
          for (int i = 2; i < values.length; i++) {
            int visitId = Integer.parseInt(values[i]);
            require(visits.add(visitId), "Duplicate visit assignment.");
            route.getVisits().add(solution.getVisits().get(visitId));
          }
        }
      }
      require(
          jobs.size() == solution.getJobs().size()
              && routes.size() == solution.getRoutes().size()
              && visits.size() == solution.getVisits().size(),
          "Missing assignment rows.");
      SolutionManager.updateShadowVariables(solution);
      return solution;
    }

    @Override
    public boolean needsConstruction() {
      return false;
    }

    @Override
    public String temperature(boolean large) {
      return large ? "10000" : "1000";
    }
  }
}
