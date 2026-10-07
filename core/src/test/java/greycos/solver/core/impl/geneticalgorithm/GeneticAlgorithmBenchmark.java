package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.assign;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.replay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.verify;

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

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.Task;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.TaskMachineConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.TaskMachineSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;

/**
 * Bounded standalone correctness-first evidence harness, not a statistically rigorous
 * microbenchmark. No timing thresholds are asserted. Report rows retain setup, verification and
 * measured work separately.
 */
public final class GeneticAlgorithmBenchmark {
  private GeneticAlgorithmBenchmark() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 6) {
      throw new IllegalArgumentException(
          "Usage: [<tasks> <candidates> <searchMillis> <scoreCallLimit> <seedsCsv> <outputDirectory>]");
    }
    int size = args.length == 0 ? 80 : Integer.parseInt(args[0]);
    int count = args.length == 0 ? 256 : Integer.parseInt(args[1]);
    long millis = args.length == 0 ? 250L : Long.parseLong(args[2]);
    long callLimit = args.length == 0 ? 1000L : Long.parseLong(args[3]);
    String[] seeds = (args.length == 0 ? "11,37,83" : args[4]).split(",");
    Path output = Path.of(args.length == 0 ? "target/genetic-algorithm/benchmark" : args[5]);
    if (count < 1
        || count > 100000
        || millis < 1
        || millis > 60000
        || callLimit < 2
        || callLimit > 10000000
        || seeds.length > 32
        || (long) size * count > 10000000) {
      throw new IllegalArgumentException("Benchmark options exceed the bounded harness limits.");
    }
    problem(size);
    Files.createDirectories(output);
    var rows = new ArrayList<String>();
    rows.add(
        "category,algorithm,shape,seed,tasks,candidates,budget_kind,budget,verified,complete,feasible,"
            + "initial_score,result_score,assignment_sha256,score_calls,session_builds,session_count_scope,"
            + "assignment_distance,setup_ns,work_ns,allocated_bytes,gc_count,gc_ms,moves,verification_ns,"
            + "target_score,target_status,time_to_target_ns");
    var metadata =
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
            "warmup=one evaluator pass per shape and one 200-score-call search per algorithm",
            "seeds=" + String.join(",", seeds),
            "source_revision="
                + System.getProperty("greycos.gaBenchmark.revision", git("rev-parse", "HEAD")),
            "source_dirty=" + !git("status", "--porcelain").isEmpty(),
            "source_note=revision identifies HEAD; dirty working changes require the accompanying qualification diff",
            "core_location="
                + DefaultSolver.class.getProtectionDomain().getCodeSource().getLocation(),
            "evaluator_score_calls_exclude_one_initial_setup_call=true",
            "search_score_calls_include_solver_initialization=true",
            "search_session_builds=observed distinct sessions at phase and completed-step boundaries",
            "allocated_bytes=current solver thread; -1 means JVM unsupported",
            "verification_and_assignment_export_excluded_from_work_ns=true",
            "work_limit=physical score-call threshold; algorithm boundary overshoot may differ",
            "target=feasible and soft >= -(minimum integer squared load at total fixture load + half initial preference penalty)",
            "time_to_target=first initialized feasible best event crossing target; zero if input meets target; -1 if unreached",
            "timing=single-process bounded evidence; no speedup or significance claim");
    Files.write(output.resolve("metadata.properties"), metadata);

    for (String seedText : seeds) {
      long seed = Long.parseLong(seedText);
      for (String shape : List.of("sparse", "broad")) {
        long preparationStarted = System.nanoTime();
        var candidates = candidates(size, count, seed, shape);
        var expected = expected(size, candidates);
        long preparationNanos = System.nanoTime() - preparationStarted;
        // Both implementations must match every independently replayed score before either is
        // timed.
        for (boolean retained : new boolean[] {false, true}) {
          evaluate(size, candidates, expected, retained);
        }
        var order = new ArrayList<>(List.of(false, true));
        Collections.shuffle(order, new Random(seed));
        for (boolean retained : order) {
          var result = evaluate(size, candidates, expected, retained);
          rows.add(
              row(
                  "evaluator",
                  retained ? "retained_delta" : "full_replacement",
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
      var order = new ArrayList<>(List.of("GA", "LS", "ALNS"));
      Collections.shuffle(order, new Random(seed));
      for (String algorithm : order) search(size, seed, algorithm, "score_calls", 200, null);
      for (String budgetKind : List.of("score_calls", "milliseconds")) {
        for (String algorithm : order) {
          long budget = budgetKind.equals("score_calls") ? callLimit : millis;
          var result =
              search(
                  size,
                  seed,
                  algorithm,
                  budgetKind,
                  budget,
                  output.resolve(algorithm + "-" + seed + "-" + budgetKind + ".csv"));
          rows.add(
              row(
                  "search",
                  algorithm,
                  "task_machine",
                  seed,
                  size,
                  0,
                  budgetKind,
                  budget,
                  result,
                  0));
        }
      }
      Files.write(output.resolve("results.csv"), rows);
    }
    System.out.println(
        "Correctness and independent replay: PASS; evidence: " + output.toAbsolutePath());
  }

  private static int[][] candidates(int size, int count, long seed, String shape) {
    var input = problem(size);
    int[] current = assignments(input);
    var random = new Random(seed);
    int[][] candidates = new int[count][];
    for (int i = 0; i < count; i++) {
      current = current.clone();
      if (shape.equals("sparse")) {
        int slot = random.nextInt(size);
        current[slot] =
            (current[slot] + 1 + random.nextInt(input.getMachines().size() - 1))
                % input.getMachines().size();
      } else {
        for (int j = 0; j < size; j++) current[j] = random.nextInt(input.getMachines().size());
      }
      candidates[i] = current;
    }
    return candidates;
  }

  private static HardSoftScore[] expected(int size, int[][] candidates) {
    var replaySolution = problem(size);
    var scores = new HardSoftScore[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      assign(replaySolution, candidates[i]);
      scores[i] = replay(replaySolution);
    }
    return scores;
  }

  private static Result evaluate(
      int size, int[][] candidates, HardSoftScore[] expected, boolean retained) {
    long setupStarted = System.nanoTime();
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(TaskMachineSolution.class, Task.class);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TaskMachineSolution, HardSoftScore>(
            descriptor, new TaskMachineConstraints(), EnvironmentMode.NO_ASSERT);
    try (var director =
        factory
            .createScoreDirectorBuilder()
            .withConstraintMatchPolicy(ConstraintMatchPolicy.DISABLED)
            .build()) {
      var initial = problem(size);
      director.setWorkingSolution(initial);
      var initialScore = director.calculateScore();
      var workspace =
          new GeneticAlgorithmWorkspace<TaskMachineSolution, HardSoftScore>(director, initialScore);
      var genomes = new GeneticAlgorithmGenome[candidates.length];
      for (int i = 0; i < candidates.length; i++) {
        Object[] values = new Object[size];
        for (int j = 0; j < size; j++) values[j] = initial.getMachines().get(candidates[i][j]);
        genomes[i] = new GeneticAlgorithmGenome(values);
      }
      long distance = 0;
      int[] previous = assignments(initial);
      for (int[] candidate : candidates) {
        distance += distance(previous, candidate);
        previous = candidate;
      }
      var actual = new HardSoftScore[candidates.length];
      long setupNanos = System.nanoTime() - setupStarted;
      long scoreCallsBefore = director.getCalculationCount();
      Object previousSession = director.getSession();
      long sessionBuilds = 1;
      var before = Resources.capture();
      long start = System.nanoTime();
      for (int i = 0; i < candidates.length; i++) {
        if (retained) {
          var transition = workspace.transition(genomes[i]);
          if (!transition.valid())
            throw new IllegalStateException("Unexpected structurally invalid candidate.");
        } else {
          var replacement = descriptor.getSolutionCloner().cloneSolution(initial);
          assign(replacement, candidates[i]);
          director.setWorkingSolution(replacement);
        }
        var score = director.calculateScore();
        if (retained) workspace.scored(score);
        actual[i] = score.raw();
        Object currentSession = director.getSession();
        if (currentSession != previousSession) sessionBuilds++;
        previousSession = currentSession;
      }
      long elapsed = System.nanoTime() - start;
      var resources = Resources.capture().minus(before);
      long verificationStarted = System.nanoTime();
      if (!Arrays.equals(actual, expected))
        throw new IllegalStateException("Evaluator differs from independent replay.");
      if (sessionBuilds != (retained ? 1L : candidates.length + 1L)) {
        throw new IllegalStateException("Unexpected Bavet session builds: " + sessionBuilds);
      }
      if (director.getCalculationCount() - scoreCallsBefore != candidates.length) {
        throw new IllegalStateException(
            "Expected exactly one physical score calculation per evaluator candidate.");
      }
      var finalSolution = director.getWorkingSolution();
      if (!Arrays.equals(assignments(finalSolution), candidates[candidates.length - 1])) {
        throw new IllegalStateException("Evaluator did not materialize final assignments.");
      }
      var finalScore = replay(finalSolution);
      return new Result(
          initialScore.raw(),
          finalScore,
          fingerprint(finalSolution),
          director.getCalculationCount() - scoreCallsBefore,
          sessionBuilds,
          "exact_including_setup",
          distance,
          setupNanos,
          elapsed,
          resources,
          candidates.length,
          System.nanoTime() - verificationStarted,
          null,
          -1);
    }
  }

  private static Result search(
      int size, long seed, String algorithm, String budgetKind, long budget, Path output)
      throws IOException {
    long setupStarted = System.nanoTime();
    var input = problem(size);
    var initial = replay(input);
    int[] startAssignments = assignments(input);
    PhaseConfig<?> phase =
        switch (algorithm) {
          case "GA" -> new GeneticAlgorithmPhaseConfig().withPopulationSize(32);
          case "LS" ->
              new LocalSearchPhaseConfig()
                  .withMoveSelectorConfig(
                      new UnionMoveSelectorConfig()
                          .withMoveSelectors(
                              new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()))
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
    var config =
        GeneticAlgorithmExample.config(seed).withPhases(phase).withTerminationConfig(termination);
    var solver =
        (DefaultSolver<TaskMachineSolution>)
            SolverFactory.<TaskMachineSolution>create(config).buildSolver();
    var sessions = new SessionRecorder();
    solver.addPhaseLifecycleListener(sessions);
    var target = target(input);
    long[] targetTime = {initial.isFeasible() && initial.compareTo(target) >= 0 ? 0L : -1L};
    long[] solveStarted = {0L};
    solver.addEventListener(
        event -> {
          var score = event.getNewBestSolution().getScore();
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
    verify(result);
    if (result.getScore().compareTo(initial) < 0)
      throw new IllegalStateException("Lost the initial best solution.");
    if (output != null) {
      GeneticAlgorithmExample.writeAssignments(result, output);
      var restored = GeneticAlgorithmExample.readAssignments(size, output);
      if (!Arrays.equals(assignments(result), assignments(restored))
          || !result.getScore().equals(replay(restored))) {
        throw new IllegalStateException("Export/reload changed assignments or business score.");
      }
    }
    return new Result(
        initial,
        result.getScore(),
        fingerprint(result),
        solver.getScoreCalculationCount(),
        sessions.builds,
        "observed_at_phase_and_step_boundaries",
        distance(startAssignments, assignments(result)),
        setupNanos,
        elapsed,
        resources,
        solver.getSolverScope().getMoveEvaluationCount(),
        System.nanoTime() - verificationStarted,
        target,
        targetTime[0]);
  }

  /**
   * Prespecified from immutable task data and initial assignments, never from measured outcomes.
   */
  private static HardSoftScore target(TaskMachineSolution input) {
    long totalLoad = 0;
    long preferencePenalty = 0;
    for (var task : input.getTasks()) {
      totalLoad += task.getUnits();
      preferencePenalty +=
          (long) Math.abs(task.getMachine().id() - task.getPreferredMachine()) * task.getUnits();
    }
    long machineCount = input.getMachines().size();
    long baseLoad = totalLoad / machineCount;
    long remainder = totalLoad % machineCount;
    long idealSquaredLoad =
        (machineCount - remainder) * baseLoad * baseLoad
            + remainder * (baseLoad + 1) * (baseLoad + 1);
    return HardSoftScore.of(0, -(idealSquaredLoad + preferencePenalty / 2));
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
    } catch (IOException e) {
      return "unavailable";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "unavailable";
    }
  }

  private static long distance(int[] left, int[] right) {
    long count = 0;
    for (int i = 0; i < left.length; i++) if (left[i] != right[i]) count++;
    return count;
  }

  private static String fingerprint(TaskMachineSolution solution) {
    return MoveThreadingWorkload.fingerprint(Arrays.toString(assignments(solution)));
  }

  private static String row(
      String category,
      String algorithm,
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

  private record Result(
      HardSoftScore initial,
      HardSoftScore score,
      String fingerprint,
      long scoreCalls,
      long sessionBuilds,
      String sessionScope,
      long distance,
      long setupNanos,
      long workNanos,
      Resources resources,
      long moves,
      long verificationNanos,
      HardSoftScore target,
      long timeToTargetNanos) {}

  private record Resources(long allocatedBytes, long gcCount, long gcMillis) {
    static Resources capture() {
      long allocated = -1L;
      if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
          && bean.isThreadAllocatedMemorySupported()) {
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      }
      long gcCount = 0;
      long gcMillis = 0;
      for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
        gcCount += Math.max(0, bean.getCollectionCount());
        gcMillis += Math.max(0, bean.getCollectionTime());
      }
      return new Resources(allocated, gcCount, gcMillis);
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

  private static final class SessionRecorder
      extends PhaseLifecycleListenerAdapter<TaskMachineSolution> {
    private Object previous;
    private long builds;

    private void observe(AbstractPhaseScope<TaskMachineSolution> scope) {
      Object session =
          ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
      if (session != previous) {
        builds++;
        previous = session;
      }
    }

    @Override
    public void phaseStarted(AbstractPhaseScope<TaskMachineSolution> scope) {
      observe(scope);
    }

    @Override
    public void stepEnded(AbstractStepScope<TaskMachineSolution> scope) {
      observe(scope.getPhaseScope());
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<TaskMachineSolution> scope) {
      observe(scope);
    }
  }
}
