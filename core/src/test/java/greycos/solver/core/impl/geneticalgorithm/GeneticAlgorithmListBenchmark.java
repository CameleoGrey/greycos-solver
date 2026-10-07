package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.ConstraintProvider;
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
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Assignment;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Input;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;

/**
 * Bounded correctness-first list/mixed evidence. No timing or solution-quality threshold is
 * asserted.
 */
public final class GeneticAlgorithmListBenchmark {
  private GeneticAlgorithmListBenchmark() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 6) {
      throw new IllegalArgumentException(
          "Usage: [<tasks> <candidates> <searchMillis> <scoreCallLimit> <seedsCsv> <outputDirectory>]");
    }
    int size = args.length == 0 ? 80 : Integer.parseInt(args[0]);
    int count = args.length == 0 ? 256 : Integer.parseInt(args[1]);
    long millis = args.length == 0 ? 500L : Long.parseLong(args[2]);
    long calls = args.length == 0 ? 2000L : Long.parseLong(args[3]);
    String[] seeds = (args.length == 0 ? "11,37,83" : args[4]).split(",");
    Path output = Path.of(args.length == 0 ? "target/genetic-algorithm/list-benchmark" : args[5]);
    if (count < 1
        || count > 100000
        || millis < 1
        || millis > 60000
        || calls < 2
        || calls > 10000000
        || seeds.length > 32
        || (long) size * count > 10000000) {
      throw new IllegalArgumentException("Benchmark options exceed bounded harness limits.");
    }
    GeneticAlgorithmSequenceDomain.input(size, false);
    Files.createDirectories(output);
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
            "seeds=" + String.join(",", seeds),
            "source_revision="
                + System.getProperty("greycos.gaBenchmark.revision", git("rev-parse", "HEAD")),
            "source_dirty=" + !git("status", "--porcelain").isEmpty(),
            "source_note=HEAD plus accompanying qualification diff identifies the tested source",
            "core_location="
                + DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation(),
            "warmup=one fully replayed evaluator pass per mode/shape and one 200-score-call search per algorithm/model/seed",
            "evaluator_input=identical pre-generated candidate sequence for retained and replacement paths",
            "evaluator_verification=every score; every exact assignment in prepass; final exact assignment in measured pass",
            "evaluator_score_calls_exclude_one_initial_setup_call=true",
            "search_score_calls_include_solver_initialization=true",
            "search_session_builds=observed distinct sessions at phase and completed-step boundaries",
            "search_start=identical initialized round-robin assignment and mode zero for each algorithm",
            "distance=task owner/index differences plus processing-mode differences",
            "changed_list_count=evaluator sums changed owner sequences between consecutive candidates; search compares initial to returned best",
            "allocated_bytes=current solver thread; -1 means unsupported",
            "verification_and_assignment_export_excluded_from_work_ns=true",
            "work_limit=physical score-call threshold; algorithm boundary overshoot may differ",
            "target=feasible and soft >= initial soft * 9 / 10 using integer arithmetic, prescribed before search",
            "time_to_target=first initialized feasible best event crossing target; zero if input meets target; -1 if unreached",
            "timing=single-process bounded evidence; no speedup, superiority or significance claim"));
    var rows = new ArrayList<String>();
    rows.add(
        "category,algorithm,model,shape,seed,tasks,candidates,budget_kind,budget,verified,complete,feasible,"
            + "initial_score,result_score,assignment_sha256,score_calls,session_builds,session_count_scope,"
            + "assignment_distance,changed_list_count,setup_ns,work_ns,allocated_bytes,gc_count,gc_ms,moves,verification_ns,"
            + "target_score,target_status,time_to_target_ns");
    run(listModel(), size, count, millis, calls, seeds, output, rows);
    run(mixedModel(), size, count, millis, calls, seeds, output, rows);
    System.out.println(
        "Correctness and independent replay: PASS; evidence: " + output.toAbsolutePath());
  }

  private static <S> void run(
      Model<S> model,
      int size,
      int count,
      long millis,
      long calls,
      String[] seeds,
      Path output,
      List<String> rows)
      throws IOException {
    for (String seedText : seeds) {
      long seed = Long.parseLong(seedText);
      for (String shape : List.of("sparse", "broad")) {
        long started = System.nanoTime();
        var input = GeneticAlgorithmSequenceDomain.input(size, model.mixed);
        var candidates = candidates(input, count, seed, shape);
        var expected =
            Arrays.stream(candidates)
                .map(
                    candidate ->
                        GeneticAlgorithmSequenceDomain.replay(input, candidate, model.mixed))
                .toArray(HardSoftScore[]::new);
        long preparationNanos = System.nanoTime() - started;
        for (boolean retained : new boolean[] {false, true}) {
          evaluate(model, size, candidates, expected, retained, true);
        }
        var order = new ArrayList<>(List.of(false, true));
        Collections.shuffle(order, new Random(seed));
        for (boolean retained : order) {
          var result = evaluate(model, size, candidates, expected, retained, false);
          rows.add(
              row(
                  "evaluator",
                  retained ? "retained_delta" : "full_replacement",
                  model.name(),
                  shape,
                  seed,
                  size,
                  count,
                  "candidates",
                  count,
                  result,
                  preparationNanos));
        }
      }
      var algorithms = new ArrayList<>(List.of("GA", "LS", "ALNS"));
      Collections.shuffle(algorithms, new Random(seed));
      for (var algorithm : algorithms)
        search(model, size, seed, algorithm, "score_calls", 200, null);
      for (var kind : List.of("score_calls", "milliseconds")) {
        long budget = kind.equals("score_calls") ? calls : millis;
        for (var algorithm : algorithms) {
          var result =
              search(
                  model,
                  size,
                  seed,
                  algorithm,
                  kind,
                  budget,
                  output.resolve(
                      model.name() + "-" + algorithm + "-" + seed + "-" + kind + ".csv"));
          rows.add(
              row(
                  "search",
                  algorithm,
                  model.name(),
                  "ordered_tasks",
                  seed,
                  size,
                  0,
                  kind,
                  budget,
                  result,
                  0));
        }
      }
      Files.write(output.resolve("results.csv"), rows);
    }
  }

  static Assignment[] candidates(Input input, int count, long seed, String shape) {
    if (!shape.equals("sparse") && !shape.equals("broad"))
      throw new IllegalArgumentException("Unknown candidate shape: " + shape);
    var random = new Random(seed);
    var current = GeneticAlgorithmSequenceDomain.initial(input);
    var result = new Assignment[count];
    for (int c = 0; c < count; c++) {
      var rows = new ArrayList<List<Integer>>();
      for (var row : current.sequences()) {
        var list = new ArrayList<Integer>();
        for (int id : row) list.add(id);
        rows.add(list);
      }
      int[] modes = current.modes();
      if (shape.equals("sparse")) {
        int source;
        do {
          source = random.nextInt(rows.size());
        } while (rows.get(source).isEmpty());
        int id = rows.get(source).remove(random.nextInt(rows.get(source).size()));
        var destination = rows.get(random.nextInt(rows.size()));
        destination.add(random.nextInt(destination.size() + 1), id);
        if (input.modes().size() > 1) modes[id] = random.nextInt(input.modes().size());
      } else {
        for (var row : rows) row.clear();
        var permutation = new ArrayList<Integer>();
        for (int id = 0; id < modes.length; id++) permutation.add(id);
        Collections.shuffle(permutation, random);
        for (int id : permutation) {
          rows.get(random.nextInt(rows.size())).add(id);
          modes[id] = random.nextInt(input.modes().size());
        }
      }
      current =
          new Assignment(
              rows.stream()
                  .map(row -> row.stream().mapToInt(Integer::intValue).toArray())
                  .toArray(int[][]::new),
              modes);
      result[c] = current;
    }
    return result;
  }

  static <S> Result evaluate(
      Model<S> model,
      int size,
      Assignment[] candidates,
      HardSoftScore[] expected,
      boolean retained,
      boolean verifyEveryCandidate) {
    long setupStarted = System.nanoTime();
    var descriptor = model.descriptor();
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<S, HardSoftScore>(
            descriptor, model.constraints, EnvironmentMode.NO_ASSERT);
    try (var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.DISABLED)
            .build()) {
      var initial = model.problem.apply(size);
      director.setWorkingSolution(initial);
      var initialScore = director.calculateScore();
      var workspace = new GeneticAlgorithmWorkspace<S, HardSoftScore>(director, initialScore);
      var genomes = new GeneticAlgorithmGenome[candidates.length];
      long distance = 0;
      long changedLists = 0;
      var previous = model.snapshot.apply(initial);
      for (int i = 0; i < candidates.length; i++) {
        genomes[i] = genome(model, initial, workspace, candidates[i]);
        distance += GeneticAlgorithmSequenceDomain.distance(previous, candidates[i]);
        changedLists += changedLists(previous, candidates[i]);
        previous = candidates[i];
      }
      var actual = new HardSoftScore[candidates.length];
      long setupNanos = System.nanoTime() - setupStarted;
      long callsBefore = director.getCalculationCount();
      Object previousSession = director.getSession();
      long sessions = 1;
      var before = Resources.capture();
      long start = System.nanoTime();
      for (int i = 0; i < candidates.length; i++) {
        if (retained) {
          if (!workspace.transition(genomes[i]).valid())
            throw new IllegalStateException("Rejected a valid candidate.");
        } else {
          var replacement = descriptor.getSolutionCloner().cloneSolution(initial);
          model.assign.accept(replacement, candidates[i]);
          director.setWorkingSolution(replacement);
        }
        var score = director.calculateScore();
        if (retained) workspace.scored(score);
        actual[i] = score.raw();
        Object session = director.getSession();
        if (session != previousSession) sessions++;
        previousSession = session;
        if (verifyEveryCandidate
            && (!candidates[i].equals(model.snapshot.apply(director.getWorkingSolution()))
                || !score.raw().equals(model.replay(director.getWorkingSolution())))) {
          throw new IllegalStateException(
              "Evaluator prepass changed exact sequence/modes or replay score.");
        }
      }
      long elapsed = System.nanoTime() - start;
      var resources = Resources.capture().minus(before);
      long verificationStarted = System.nanoTime();
      if (!Arrays.equals(actual, expected))
        throw new IllegalStateException("Evaluator score differs from independent replay.");
      if (sessions != (retained ? 1L : candidates.length + 1L))
        throw new IllegalStateException("Unexpected Bavet session count: " + sessions);
      long scoreCalls = director.getCalculationCount() - callsBefore;
      if (scoreCalls != candidates.length)
        throw new IllegalStateException("Expected one physical score call per candidate.");
      var last = director.getWorkingSolution();
      var assignment = model.snapshot.apply(last);
      if (!assignment.equals(candidates[candidates.length - 1]))
        throw new IllegalStateException("Final exact assignment differs.");
      var replayed = model.replay(last);
      return new Result(
          initialScore.raw(),
          replayed,
          assignment.fingerprint(),
          scoreCalls,
          sessions,
          "exact_including_setup",
          distance,
          changedLists,
          setupNanos,
          elapsed,
          resources,
          candidates.length,
          System.nanoTime() - verificationStarted,
          null,
          -1);
    }
  }

  private static <S> GeneticAlgorithmGenome genome(
      Model<S> model,
      S initial,
      GeneticAlgorithmWorkspace<S, HardSoftScore> workspace,
      Assignment assignment) {
    var listModel = workspace.listModel();
    if (listModel == null) throw new IllegalStateException("Expected a list workspace.");
    int[] taskIdToValueId = new int[listModel.valueCount()];
    for (int id = 0; id < taskIdToValueId.length; id++)
      taskIdToValueId[taskId(listModel.value(id))] = id;
    var sequences = assignment.sequences();
    int[][] lists = new int[listModel.ownerCount()][];
    for (int id = 0; id < lists.length; id++) {
      int[] tasks = sequences[ownerId(listModel.owner(id))];
      lists[id] = Arrays.stream(tasks).map(task -> taskIdToValueId[task]).toArray();
    }
    var input = model.input.apply(initial);
    var modes = assignment.modes();
    Object[] values = new Object[workspace.slots().size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = input.modes().get(modes[taskId(workspace.slots().get(i).entity())]);
    }
    return new GeneticAlgorithmGenome(values, lists);
  }

  private static int taskId(Object object) {
    return object instanceof GeneticAlgorithmListExample.Task task
        ? task.getId()
        : ((GeneticAlgorithmMixedExample.Task) object).getId();
  }

  private static int ownerId(Object object) {
    return object instanceof GeneticAlgorithmListExample.Machine machine
        ? machine.getId()
        : ((GeneticAlgorithmMixedExample.Machine) object).getId();
  }

  static <S> Result search(
      Model<S> model,
      int size,
      long seed,
      String algorithm,
      String budgetKind,
      long budget,
      Path output)
      throws IOException {
    long setupStarted = System.nanoTime();
    var input = model.problem.apply(size);
    var initial = model.replay(input);
    var startAssignments = model.snapshot.apply(input);
    var moves = new ArrayList<MoveSelectorConfig>();
    moves.add(new ListChangeMoveSelectorConfig());
    moves.add(new ListSwapMoveSelectorConfig());
    if (model.mixed)
      moves.add(
          new ChangeMoveSelectorConfig()
              .withEntitySelectorConfig(
                  new EntitySelectorConfig(GeneticAlgorithmMixedExample.Task.class))
              .withValueSelectorConfig(new ValueSelectorConfig("mode")));
    PhaseConfig<?> phase =
        switch (algorithm) {
          case "GA" -> new GeneticAlgorithmPhaseConfig().withPopulationSize(32);
          case "LS" ->
              new LocalSearchPhaseConfig()
                  .withMoveSelectorConfig(new UnionMoveSelectorConfig().withMoveSelectorList(moves))
                  .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
                  .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
          case "ALNS" ->
              new AlnsPhaseConfig()
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
          default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        };
    var termination =
        budgetKind.equals("milliseconds")
            ? new TerminationConfig().withSpentLimit(Duration.ofMillis(budget))
            : new TerminationConfig()
                .withScoreCalculationCountLimit(budget)
                .withSpentLimit(Duration.ofSeconds(60));
    var solver =
        (DefaultSolver<S>)
            SolverFactory.<S>create(
                    model.config.apply(seed).withPhases(phase).withTerminationConfig(termination))
                .buildSolver();
    var sessions = new SessionRecorder<S>();
    solver.addPhaseLifecycleListener(sessions);
    var target = HardSoftScore.of(0L, initial.softScore() * 9 / 10);
    long[] targetTime = {initial.isFeasible() && initial.compareTo(target) >= 0 ? 0L : -1L};
    long[] solveStarted = {0L};
    solver.addEventListener(
        event -> {
          var score = model.score.apply(event.getNewBestSolution());
          if (targetTime[0] < 0
              && event.isNewBestSolutionInitialized()
              && score.isFeasible()
              && score.compareTo(target) >= 0) {
            targetTime[0] = System.nanoTime() - solveStarted[0];
          }
        });
    long setupNanos = System.nanoTime() - setupStarted;
    var before = Resources.capture();
    long start = System.nanoTime();
    solveStarted[0] = start;
    var result = solver.solve(input);
    long elapsed = System.nanoTime() - start;
    var resources = Resources.capture().minus(before);
    long verificationStarted = System.nanoTime();
    var replayed = model.replay(result);
    if (!replayed.equals(model.score.apply(result)))
      throw new IllegalStateException("Independent business score mismatch.");
    if (replayed.compareTo(initial) < 0)
      throw new IllegalStateException("Lost initialized starting best.");
    var assignment = model.snapshot.apply(result);
    if (output != null) {
      GeneticAlgorithmSequenceDomain.write(
          model.input.apply(result), assignment, model.mixed, output);
      var restored = model.problem.apply(size);
      model.assign.accept(
          restored,
          GeneticAlgorithmSequenceDomain.read(model.input.apply(restored), model.mixed, output));
      if (!assignment.equals(model.snapshot.apply(restored))
          || !replayed.equals(model.replay(restored))) {
        throw new IllegalStateException("Export/reload changed exact assignments or score.");
      }
    }
    return new Result(
        initial,
        replayed,
        assignment.fingerprint(),
        solver.getScoreCalculationCount(),
        sessions.builds,
        "observed_at_phase_and_step_boundaries",
        GeneticAlgorithmSequenceDomain.distance(startAssignments, assignment),
        changedLists(startAssignments, assignment),
        setupNanos,
        elapsed,
        resources,
        solver.getSolverScope().getMoveEvaluationCount(),
        System.nanoTime() - verificationStarted,
        target,
        targetTime[0]);
  }

  static Model<GeneticAlgorithmListExample.SequenceSolution> listModel() {
    return new Model<>(
        false,
        GeneticAlgorithmListExample.SequenceSolution.class,
        new Class<?>[] {
          GeneticAlgorithmListExample.Machine.class, GeneticAlgorithmListExample.Task.class
        },
        new GeneticAlgorithmListExample.SequenceConstraints(),
        GeneticAlgorithmListExample::config,
        GeneticAlgorithmListExample::problem,
        GeneticAlgorithmListExample::assignments,
        GeneticAlgorithmListExample::materializeValidated,
        GeneticAlgorithmListExample.SequenceSolution::getInput,
        GeneticAlgorithmListExample.SequenceSolution::getScore);
  }

  static Model<GeneticAlgorithmMixedExample.SequenceSolution> mixedModel() {
    return new Model<>(
        true,
        GeneticAlgorithmMixedExample.SequenceSolution.class,
        new Class<?>[] {
          GeneticAlgorithmMixedExample.Machine.class, GeneticAlgorithmMixedExample.Task.class
        },
        new GeneticAlgorithmMixedExample.SequenceConstraints(),
        GeneticAlgorithmMixedExample::config,
        GeneticAlgorithmMixedExample::problem,
        GeneticAlgorithmMixedExample::assignments,
        GeneticAlgorithmMixedExample::materializeValidated,
        GeneticAlgorithmMixedExample.SequenceSolution::getInput,
        GeneticAlgorithmMixedExample.SequenceSolution::getScore);
  }

  record Model<S>(
      boolean mixed,
      Class<S> solutionClass,
      Class<?>[] entityClasses,
      ConstraintProvider constraints,
      LongFunction<SolverConfig> config,
      IntFunction<S> problem,
      Function<S, Assignment> snapshot,
      BiConsumer<S, Assignment> assign,
      Function<S, Input> input,
      Function<S, HardSoftScore> score) {
    String name() {
      return mixed ? "mixed" : "list_only";
    }

    SolutionDescriptor<S> descriptor() {
      return SolutionDescriptor.buildSolutionDescriptor(solutionClass, entityClasses);
    }

    HardSoftScore replay(S solution) {
      return GeneticAlgorithmSequenceDomain.replay(
          input.apply(solution), snapshot.apply(solution), mixed);
    }
  }

  private static long changedLists(Assignment previous, Assignment next) {
    var left = previous.sequences();
    var right = next.sequences();
    long changed = 0;
    for (int i = 0; i < left.length; i++) if (!Arrays.equals(left[i], right[i])) changed++;
    return changed;
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
      String result =
          new String(
                  process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .strip();
      return process.exitValue() == 0 ? result : "unavailable";
    } catch (IOException exception) {
      return "unavailable";
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return "unavailable";
    }
  }

  private static String row(
      String category,
      String algorithm,
      String model,
      String shape,
      long seed,
      int size,
      int count,
      String budgetKind,
      long budget,
      Result result,
      long preparationNanos) {
    return String.join(
        ",",
        category,
        algorithm,
        model,
        shape,
        Long.toString(seed),
        Integer.toString(size),
        Integer.toString(count),
        budgetKind,
        Long.toString(budget),
        "true",
        "true",
        Boolean.toString(result.score.isFeasible()),
        result.initial.toString(),
        result.score.toString(),
        result.fingerprint,
        Long.toString(result.scoreCalls),
        Long.toString(result.sessionBuilds),
        result.sessionScope,
        Long.toString(result.distance),
        Long.toString(result.changedLists),
        Long.toString(result.setupNanos + preparationNanos),
        Long.toString(result.workNanos),
        Long.toString(result.resources.allocatedBytes),
        Long.toString(result.resources.gcCount),
        Long.toString(result.resources.gcMillis),
        Long.toString(result.moves),
        Long.toString(result.verificationNanos),
        result.target == null ? "" : result.target.toString(),
        result.target == null
            ? "not_applicable"
            : result.timeToTargetNanos < 0 ? "unreached" : "reached",
        Long.toString(result.timeToTargetNanos));
  }

  record Result(
      HardSoftScore initial,
      HardSoftScore score,
      String fingerprint,
      long scoreCalls,
      long sessionBuilds,
      String sessionScope,
      long distance,
      long changedLists,
      long setupNanos,
      long workNanos,
      Resources resources,
      long moves,
      long verificationNanos,
      HardSoftScore target,
      long timeToTargetNanos) {}

  record Resources(long allocatedBytes, long gcCount, long gcMillis) {
    static Resources capture() {
      long allocated = -1L;
      if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
          && bean.isThreadAllocatedMemorySupported()) {
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      }
      long count = 0;
      long millis = 0;
      for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
        count += Math.max(0, bean.getCollectionCount());
        millis += Math.max(0, bean.getCollectionTime());
      }
      return new Resources(allocated, count, millis);
    }

    Resources minus(Resources before) {
      return new Resources(
          allocatedBytes < 0 || before.allocatedBytes < 0
              ? -1
              : allocatedBytes - before.allocatedBytes,
          gcCount - before.gcCount,
          gcMillis - before.gcMillis);
    }
  }

  private static final class SessionRecorder<S> extends PhaseLifecycleListenerAdapter<S> {
    private Object previous;
    private long builds;

    private void observe(AbstractPhaseScope<S> scope) {
      Object session =
          ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
      if (session != previous) {
        builds++;
        previous = session;
      }
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<S> scope) {
      observe(scope);
    }

    @Override
    public void stepEnded(AbstractStepScope<S> scope) {
      observe(scope.getPhaseScope());
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<S> scope) {
      observe(scope);
    }
  }
}
