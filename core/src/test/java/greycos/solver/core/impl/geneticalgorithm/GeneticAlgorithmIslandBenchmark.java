package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.islandmodel.DefaultIslandModelPhase;
import greycos.solver.core.impl.islandmodel.IslandGeneticAlgorithmDiagnostics;
import greycos.solver.core.impl.islandmodel.IslandRunDiagnostics;
import greycos.solver.core.impl.solver.DefaultSolver;

/** Bounded asynchronous-island comparison; native scores and exported assignments are replayed. */
public final class GeneticAlgorithmIslandBenchmark {
  static final List<String> MODELS = List.of("basic", "list_only", "mixed");
  static final List<String> BUDGETS = List.of("score_calls", "milliseconds");
  static final double DEFAULT_MIGRATION_RATE = 0.00001;
  static final int MIGRATION_FREQUENCY = 10;
  static final String HEADER =
      "algorithm,model,seed,tasks,islands,island_model,migration_rate,"
          + "migration_frequency,local_improvement_limit,budget_kind,budget,per_island_call_target,"
          + "allocated_call_target,unallocated_call_remainder,verified,export_reload,complete,feasible,"
          + "initial_score,result_score,assignment_sha256,score_calls,root_score_calls,island_score_calls,"
          + "score_call_overshoot,setup_ns,work_ns,verification_ns,process_cpu_ns,allocated_bytes,gc_count,gc_ms,"
          + "moves,target_score,target_status,time_to_target_ns,per_island_score_calls,per_island_moves,"
          + "completed_generations,bavet_session_count,bavet_session_count_scope,exported_batches,exported_entries,"
          + "received_batches,received_entries,evaluated_entries,admitted_entries,rejected_entries,committed_batches";

  private GeneticAlgorithmIslandBenchmark() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 6) {
      throw new IllegalArgumentException(
          "Usage: [<basic|list_only|mixed> <tasks> <searchMillis> <aggregateScoreCalls> <seedsCsv> <outputDirectory>]");
    }
    String model = args.length == 0 ? "basic" : args[0];
    int size = args.length == 0 ? 80 : Integer.parseInt(args[1]);
    long millis = args.length == 0 ? 2000L : Long.parseLong(args[2]);
    long calls = args.length == 0 ? 20000L : Long.parseLong(args[3]);
    String[] seeds = (args.length == 0 ? "11,37,83" : args[4]).split(",", -1);
    Path output = Path.of(args.length == 0 ? "target/genetic-algorithm/island-benchmark" : args[5]);
    if (!MODELS.contains(model)
        || size < 8
        || size > 100000
        || millis < 1
        || millis > 60000
        || calls < 8
        || calls > 10000000
        || seeds.length < 1
        || seeds.length > 32) {
      throw new IllegalArgumentException("Benchmark options exceed bounded harness limits.");
    }
    for (var seed : seeds) Long.parseLong(seed);
    var variants = selectedVariants();
    Files.createDirectories(output);
    writeMetadata(output, model, seeds, variants);
    var rows = new ArrayList<String>();
    rows.add(HEADER);
    for (int ordinal = 0; ordinal < seeds.length; ordinal++) {
      long seed = Long.parseLong(seeds[ordinal]);
      int orderIndex = ordinal + MODELS.indexOf(model);
      for (var variant : order(variants, orderIndex, false)) {
        search(model, size, seed, variant, "score_calls", 200, 0, null);
      }
      for (String kind : BUDGETS) {
        long budget = kind.equals("score_calls") ? calls : millis;
        for (var variant : order(variants, orderIndex, kind.equals("milliseconds"))) {
          var result =
              search(
                  model,
                  size,
                  seed,
                  variant,
                  kind,
                  budget,
                  0,
                  output.resolve(variant.name() + "-" + seed + "-" + kind + ".csv"));
          rows.add(row(variant, model, seed, size, kind, budget, result));
          Files.write(output.resolve("results.csv"), rows);
        }
      }
    }
    System.out.println(
        "Independent replay and exact export/reload: PASS; evidence: " + output.toAbsolutePath());
  }

  static List<Variant> variants() {
    var result = new ArrayList<Variant>();
    result.add(new Variant("GA_SERIAL", "GA", 1, false, 0));
    for (int count : new int[] {1, 2, 4}) {
      result.add(new Variant("GA_ISLAND_" + count + "_OFF", "GA", count, true, 0));
      result.add(
          new Variant("GA_ISLAND_" + count + "_ON", "GA", count, true, DEFAULT_MIGRATION_RATE));
    }
    result.add(new Variant("LA_SERIAL", "LA", 1, false, 0));
    for (int count : new int[] {1, 2, 4})
      result.add(new Variant("LA_ISLAND_" + count, "LA", count, true, 0));
    result.add(new Variant("ALNS_SERIAL", "ALNS", 1, false, 0));
    return List.copyOf(result);
  }

  private static List<Variant> selectedVariants() {
    String filter = System.getProperty("greycos.gaIslandBenchmark.algorithms");
    if (filter == null) return variants();
    var selected = Arrays.asList(filter.split(",", -1));
    var result =
        selected.stream()
            .map(
                name ->
                    variants().stream()
                        .filter(variant -> variant.name().equals(name))
                        .findFirst()
                        .orElseThrow(
                            () -> new IllegalArgumentException("Unknown algorithm: " + name)))
            .toList();
    if (result.stream().distinct().count() != result.size())
      throw new IllegalArgumentException("Duplicate algorithm selection: " + filter);
    return result;
  }

  static List<Variant> order(List<Variant> variants, int ordinal, boolean reverseBudget) {
    var result = new ArrayList<>(variants);
    Collections.rotate(result, -Math.floorMod(ordinal, result.size()));
    if (reverseBudget ^ Boolean.getBoolean("greycos.gaIslandBenchmark.reverseOrder"))
      Collections.reverse(result);
    return result;
  }

  record Variant(
      String name, String algorithm, int islands, boolean islandModel, double migrationRate) {
    Variant {
      if (islands < 1
          || islands > 64
          || (!islandModel && islands != 1)
          || !Double.isFinite(migrationRate)
          || migrationRate < 0
          || migrationRate > 1
          || !List.of("GA", "LA", "ALNS").contains(algorithm)) {
        throw new IllegalArgumentException("Invalid island benchmark variant: " + name);
      }
    }
  }

  static SolverConfig config(
      String model, long seed, Variant variant, String kind, long budget, long probes) {
    if (!BUDGETS.contains(kind)
        || budget < 1
        || probes < 0
        || (kind.equals("score_calls") && budget / variant.islands() < 2))
      throw new IllegalArgumentException("Invalid budget or local-improvement limit.");
    SolverConfig config =
        switch (model) {
          case "basic" -> GeneticAlgorithmExample.config(seed);
          case "list_only" -> GeneticAlgorithmListExample.config(seed);
          case "mixed" -> GeneticAlgorithmMixedExample.config(seed);
          default -> throw new IllegalArgumentException("Unknown model: " + model);
        };
    PhaseConfig<?> phase = phase(model, variant, probes);
    if (variant.islandModel()) {
      phase =
          new IslandModelPhaseConfig()
              .withIslandCount(variant.islands())
              .withMoveThreadCount("NONE")
              .withMigrationFrequency(
                  variant.algorithm().equals("GA") ? MIGRATION_FREQUENCY : Integer.MAX_VALUE)
              .withCompareGlobalEnabled(false)
              .withPhaseConfigList(List.of(phase));
    }
    phase.setTerminationConfig(
        kind.equals("milliseconds")
            ? new TerminationConfig().withSpentLimit(Duration.ofMillis(budget))
            : new TerminationConfig().withScoreCalculationCountLimit(budget / variant.islands()));
    return config
        .withPhases(phase)
        .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(60)));
  }

  private static PhaseConfig<?> phase(String model, Variant variant, long probes) {
    if (variant.algorithm().equals("GA"))
      return new GeneticAlgorithmPhaseConfig()
          .withPopulationSize(32)
          .withMigrationRate(variant.migrationRate())
          .withLocalImprovementMoveCountLimit(probes);
    if (variant.algorithm().equals("ALNS"))
      return new AlnsPhaseConfig()
          .withAcceptanceType(AlnsAcceptanceType.LATE_ACCEPTANCE)
          .withLateAcceptanceSize(64)
          .withDestroyOperators(
              new AlnsDestroyOperatorConfig()
                  .withId("random")
                  .withType(AlnsDestroyOperatorType.RANDOM)
                  .withMinimumDestroyedCount(3)
                  .withMaximumDestroyedCount(3))
          .withRepairOperators(
              new AlnsRepairOperatorConfig()
                  .withId("greedy")
                  .withType(AlnsRepairOperatorType.GREEDY));
    var moves = new ArrayList<MoveSelectorConfig>();
    if (model.equals("basic")) {
      moves.add(new ChangeMoveSelectorConfig());
      moves.add(new SwapMoveSelectorConfig());
    } else {
      moves.add(new ListChangeMoveSelectorConfig());
      moves.add(new ListSwapMoveSelectorConfig());
      if (model.equals("mixed"))
        moves.add(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(GeneticAlgorithmMixedExample.Task.class))
                .withValueSelectorConfig(new ValueSelectorConfig("mode")));
    }
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectorList(moves))
        .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
        .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
  }

  static Result search(
      String model,
      int size,
      long seed,
      Variant variant,
      String kind,
      long budget,
      long probes,
      Path output)
      throws IOException {
    return switch (model) {
      case "basic" ->
          search(basicModel(), model, size, seed, variant, kind, budget, probes, output);
      case "list_only" ->
          search(
              sequenceModel(GeneticAlgorithmListBenchmark.listModel()),
              model,
              size,
              seed,
              variant,
              kind,
              budget,
              probes,
              output);
      case "mixed" ->
          search(
              sequenceModel(GeneticAlgorithmListBenchmark.mixedModel()),
              model,
              size,
              seed,
              variant,
              kind,
              budget,
              probes,
              output);
      default -> throw new IllegalArgumentException("Unknown model: " + model);
    };
  }

  private static <S> Result search(
      Model<S> model,
      String modelName,
      int size,
      long seed,
      Variant variant,
      String kind,
      long budget,
      long probes,
      Path output)
      throws IOException {
    long setupStarted = System.nanoTime();
    S input = model.problem().apply(size);
    var initial = model.replay().apply(input);
    var solver =
        (DefaultSolver<S>)
            SolverFactory.<S>create(config(modelName, seed, variant, kind, budget, probes))
                .buildSolver();
    var target =
        input instanceof GeneticAlgorithmExample.TaskMachineSolution basic
            ? basicTarget(basic)
            : HardSoftScore.of(0, initial.softScore() * 9 / 10);
    var targetTime =
        new AtomicLong(initial.isFeasible() && initial.compareTo(target) >= 0 ? 0 : -1);
    var started = new AtomicLong();
    solver.addEventListener(
        event -> {
          var score = model.score().apply(event.getNewBestSolution());
          if (event.isNewBestSolutionInitialized()
              && score.isFeasible()
              && score.compareTo(target) >= 0)
            targetTime.compareAndSet(-1, System.nanoTime() - started.get());
        });
    long setupNanos = System.nanoTime() - setupStarted;
    var before = Resources.capture();
    started.set(System.nanoTime());
    S result = solver.solve(input);
    long elapsed = System.nanoTime() - started.get();
    var resources = Resources.capture().minus(before);
    long verificationStarted = System.nanoTime();
    var replayed = model.replay().apply(result);
    if (!replayed.equals(model.score().apply(result)) || replayed.compareTo(initial) < 0)
      throw new IllegalStateException("Independent replay differs or solve lost initialized best.");
    if (output != null) model.export().writeAndVerify(result, size, output);
    var scope = solver.getSolverScope();
    return new Result(
        initial,
        replayed,
        model.fingerprint().apply(result),
        scope.getReportedScoreCalculationCount(),
        scope.getScoreCalculationCount(),
        scope.getIslandWorkAccounting().snapshot().scoreCalculationCount(),
        setupNanos,
        elapsed,
        System.nanoTime() - verificationStarted,
        resources,
        scope.getReportedMoveEvaluationCount(),
        target,
        targetTime.get(),
        Diagnostics.capture(solver));
  }

  private static HardSoftScore basicTarget(GeneticAlgorithmExample.TaskMachineSolution input) {
    long totalLoad = 0, preferencePenalty = 0;
    for (var task : input.getTasks()) {
      totalLoad += task.getUnits();
      preferencePenalty +=
          (long) Math.abs(task.getMachine().id() - task.getPreferredMachine()) * task.getUnits();
    }
    long count = input.getMachines().size(),
        base = totalLoad / count,
        remainder = totalLoad % count;
    long idealSquaredLoad = (count - remainder) * base * base + remainder * (base + 1) * (base + 1);
    return HardSoftScore.of(0, -(idealSquaredLoad + preferencePenalty / 2));
  }

  @FunctionalInterface
  private interface Export<S> {
    void writeAndVerify(S solution, int size, Path output) throws IOException;
  }

  private record Model<S>(
      IntFunction<S> problem,
      Function<S, HardSoftScore> replay,
      Function<S, HardSoftScore> score,
      Function<S, String> fingerprint,
      Export<S> export) {}

  private static Model<GeneticAlgorithmExample.TaskMachineSolution> basicModel() {
    return new Model<>(
        GeneticAlgorithmExample::problem,
        GeneticAlgorithmExample::replay,
        GeneticAlgorithmExample.TaskMachineSolution::getScore,
        solution ->
            MoveThreadingWorkload.fingerprint(
                Arrays.toString(GeneticAlgorithmExample.assignments(solution))),
        (solution, size, output) -> {
          GeneticAlgorithmExample.writeAssignments(solution, output);
          var restored = GeneticAlgorithmExample.readAssignments(size, output);
          if (!Arrays.equals(
                  GeneticAlgorithmExample.assignments(solution),
                  GeneticAlgorithmExample.assignments(restored))
              || !solution.getScore().equals(GeneticAlgorithmExample.replay(restored)))
            throw new IllegalStateException("Export/reload changed exact assignments or score.");
        });
  }

  private static <S> Model<S> sequenceModel(GeneticAlgorithmListBenchmark.Model<S> model) {
    return new Model<>(
        model.problem(),
        model::replay,
        model.score(),
        solution -> model.snapshot().apply(solution).fingerprint(),
        (solution, size, output) -> {
          var assignment = model.snapshot().apply(solution);
          GeneticAlgorithmSequenceDomain.write(
              model.input().apply(solution), assignment, model.mixed(), output);
          var restored = model.problem().apply(size);
          model
              .assign()
              .accept(
                  restored,
                  GeneticAlgorithmSequenceDomain.read(
                      model.input().apply(restored), model.mixed(), output));
          if (!assignment.equals(model.snapshot().apply(restored))
              || !model.score().apply(solution).equals(model.replay(restored)))
            throw new IllegalStateException(
                "Export/reload changed sequences, modes or replay score.");
        });
  }

  record Result(
      HardSoftScore initial,
      HardSoftScore score,
      String fingerprint,
      long calls,
      long rootCalls,
      long islandCalls,
      long setupNanos,
      long workNanos,
      long verificationNanos,
      Resources resources,
      long moves,
      HardSoftScore target,
      long targetNanos,
      Diagnostics diagnostics) {}

  record Diagnostics(
      List<IslandRunDiagnostics> islands, List<IslandGeneticAlgorithmDiagnostics> migration) {
    static Diagnostics capture(DefaultSolver<?> solver) {
      var islands = new ArrayList<IslandRunDiagnostics>();
      var migration = new ArrayList<IslandGeneticAlgorithmDiagnostics>();
      for (var phase : solver.getPhaseList()) {
        if (phase instanceof DefaultIslandModelPhase<?> island) {
          islands.addAll(island.getIslandDiagnostics());
          migration.addAll(island.getGeneticAlgorithmMigrationDiagnostics());
        }
      }
      if (islands.stream().mapToLong(IslandRunDiagnostics::physicalScoreCalculationCount).sum()
          != solver.getSolverScope().getIslandWorkAccounting().snapshot().scoreCalculationCount())
        throw new IllegalStateException(
            "Per-island diagnostic calls differ from finalized reported physical work.");
      return new Diagnostics(List.copyOf(islands), List.copyOf(migration));
    }

    String columns() {
      String calls =
          islands.stream()
              .map(island -> island.islandId() + ":" + island.physicalScoreCalculationCount())
              .collect(Collectors.joining("|"));
      String moves =
          islands.stream()
              .map(island -> island.islandId() + ":" + island.moveEvaluationCount())
              .collect(Collectors.joining("|"));
      long generations =
          migration.stream()
              .mapToLong(IslandGeneticAlgorithmDiagnostics::completedGenerations)
              .sum();
      long sessions =
          islands.isEmpty() || islands.stream().anyMatch(island -> island.bavetSessionCount() < 0)
              ? -1
              : islands.stream().mapToLong(IslandRunDiagnostics::bavetSessionCount).sum();
      long exportedBatches = 0, exportedEntries = 0, receivedBatches = 0, receivedEntries = 0;
      long evaluatedEntries = 0, admittedEntries = 0, rejectedEntries = 0, committedBatches = 0;
      for (var phase : migration) {
        var counts = phase.migration();
        exportedBatches += counts.exportedBatches();
        exportedEntries += counts.exportedEntries();
        receivedBatches += counts.receivedBatches();
        receivedEntries += counts.receivedEntries();
        evaluatedEntries += counts.evaluatedEntries();
        admittedEntries += counts.admittedEntries();
        rejectedEntries += counts.rejectedEntries();
        committedBatches += counts.committedBatches();
      }
      return String.join(
          ",",
          calls,
          moves,
          "" + generations,
          "" + sessions,
          sessions < 0 ? "not_instrumented" : "observed_island_sessions",
          "" + exportedBatches,
          "" + exportedEntries,
          "" + receivedBatches,
          "" + receivedEntries,
          "" + evaluatedEntries,
          "" + admittedEntries,
          "" + rejectedEntries,
          "" + committedBatches);
    }
  }

  record Resources(long cpuNanos, long allocatedBytes, long gcCount, long gcMillis) {
    static Resources capture() {
      long allocated = -1;
      if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
          && bean.isThreadAllocatedMemorySupported()) {
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        allocated = bean.getTotalThreadAllocatedBytes();
      }
      long cpu =
          ManagementFactory.getOperatingSystemMXBean()
                  instanceof com.sun.management.OperatingSystemMXBean bean
              ? bean.getProcessCpuTime()
              : -1;
      long gcCount = 0, gcMillis = 0;
      for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
        gcCount += Math.max(0, bean.getCollectionCount());
        gcMillis += Math.max(0, bean.getCollectionTime());
      }
      return new Resources(cpu, allocated, gcCount, gcMillis);
    }

    Resources minus(Resources before) {
      return new Resources(
          cpuNanos < 0 || before.cpuNanos < 0 ? -1 : cpuNanos - before.cpuNanos,
          allocatedBytes < 0 || before.allocatedBytes < 0
              ? -1
              : allocatedBytes - before.allocatedBytes,
          gcCount - before.gcCount,
          gcMillis - before.gcMillis);
    }
  }

  private static String row(
      Variant variant, String model, long seed, int size, String kind, long budget, Result result) {
    long perIsland = kind.equals("score_calls") ? budget / variant.islands() : -1;
    long allocated = perIsland < 0 ? -1 : perIsland * variant.islands();
    return String.join(
        ",",
        variant.name(),
        model,
        "" + seed,
        "" + size,
        "" + variant.islands(),
        "" + variant.islandModel(),
        "" + variant.migrationRate(),
        ""
            + (variant.islandModel()
                ? variant.algorithm().equals("GA") ? MIGRATION_FREQUENCY : Integer.MAX_VALUE
                : 0),
        "0",
        kind,
        "" + budget,
        "" + perIsland,
        "" + allocated,
        "" + (allocated < 0 ? -1 : budget - allocated),
        "true",
        "true",
        "true",
        "" + result.score().isFeasible(),
        result.initial().toString(),
        result.score().toString(),
        result.fingerprint(),
        "" + result.calls(),
        "" + result.rootCalls(),
        "" + result.islandCalls(),
        "" + (allocated < 0 ? -1 : Math.max(0, result.calls() - allocated)),
        "" + result.setupNanos(),
        "" + result.workNanos(),
        "" + result.verificationNanos(),
        "" + result.resources().cpuNanos(),
        "" + result.resources().allocatedBytes(),
        "" + result.resources().gcCount(),
        "" + result.resources().gcMillis(),
        "" + result.moves(),
        result.target().toString(),
        result.targetNanos() < 0 ? "unreached" : "reached",
        "" + result.targetNanos(),
        result.diagnostics().columns());
  }

  private static void writeMetadata(
      Path output, String model, String[] seeds, List<Variant> variants) throws IOException {
    Files.write(
        output.resolve("metadata.properties"),
        List.of(
            "java_version=" + System.getProperty("java.version"),
            "java_vm=" + System.getProperty("java.vm.name"),
            "jvm_arguments=" + ManagementFactory.getRuntimeMXBean().getInputArguments(),
            "os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch"),
            "processors=" + Runtime.getRuntime().availableProcessors(),
            "max_heap_bytes=" + Runtime.getRuntime().maxMemory(),
            "move_threads=NONE",
            "environment=NO_ASSERT",
            "population_size=32",
            "local_improvement_move_count_limit=0",
            "migration_rate=" + DEFAULT_MIGRATION_RATE + " enabled; 0 disabled",
            "migration_frequency=10 GA generations",
            "compare_global_enabled=false",
            "model=" + model,
            "seeds=" + String.join(",", seeds),
            "algorithms=" + String.join(",", variants.stream().map(Variant::name).toList()),
            "reverse_order=" + Boolean.getBoolean("greycos.gaIslandBenchmark.reverseOrder"),
            "order_policy=rotate by seed ordinal plus model ordinal; reverse by wall budget and paired repetition",
            "warmup=200 aggregate score-call target per model/seed/variant; same JVM",
            "source_revision="
                + System.getProperty("greycos.gaBenchmark.revision", git("rev-parse", "HEAD")),
            "source_dirty=" + !git("status", "--porcelain").isEmpty(),
            "source_note=dirty source requires accompanying qualification patch and source hashes",
            "core_location="
                + DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation(),
            "score_call_budget=aggregate target divided equally by island count; remainder unallocated; boundary overshoot recorded",
            "score_calls=physical root plus finalized island calculations including initialization and migration scoring",
            "time_budget=phase elapsed limit; 60s outer fallback; work includes worker startup and cleanup",
            "bavet_session_count=-1 means uninstrumented; retained-session correctness is qualified by focused lifecycle tests",
            "resources=process CPU and all-thread allocation including exited threads; -1 unsupported; GC process-wide",
            "la_resource_match=1/2/4 independent LA islands with migration and shared-global import effectively disabled",
            "verification=independent business replay and strict exact export/reload outside work interval",
            "target=basic: minimum integer squared load plus half initial preference penalty; list/mixed: feasible with initial soft times 9/10",
            "asynchrony=repeated seeds do not promise identical scores assignments or work",
            "claims=bounded measurements; no general speedup or optimality claim"));
  }

  private static String git(String... arguments) {
    var command = new ArrayList<String>();
    command.add("git");
    command.addAll(List.of(arguments));
    try {
      var process = new ProcessBuilder(command).redirectErrorStream(true).start();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return "unavailable";
      }
      var result =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
      return process.exitValue() == 0 ? result : "unavailable";
    } catch (IOException exception) {
      return "unavailable";
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return "unavailable";
    }
  }
}
