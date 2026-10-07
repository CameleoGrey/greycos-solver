package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;

/**
 * Standalone initialized task-to-machine example with independent assignment and business replay.
 */
public final class GeneticAlgorithmExample {
  private GeneticAlgorithmExample() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 4) {
      throw new IllegalArgumentException("Usage: [<tasks> <seed> <attempts> <assignment.csv>]");
    }
    int size = args.length == 0 ? 80 : Integer.parseInt(args[0]);
    long seed = args.length == 0 ? 37L : Long.parseLong(args[1]);
    long attempts = args.length == 0 ? 2000L : Long.parseLong(args[2]);
    Path output = Path.of(args.length == 0 ? "target/genetic-algorithm/assignments.csv" : args[3]);
    if (attempts < 1) throw new IllegalArgumentException("The attempts must be positive.");
    var input = problem(size);
    var initial = replay(input);
    var config =
        config(seed)
            .withPhases(new GeneticAlgorithmPhaseConfig().withPopulationSize(32))
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(attempts));
    var result = SolverFactory.<TaskMachineSolution>create(config).buildSolver().solve(input);
    verify(result);
    writeAssignments(result, output);
    var restored = readAssignments(size, output);
    if (!Arrays.equals(assignments(result), assignments(restored))
        || !replay(restored).equals(result.score)) {
      throw new IllegalStateException(
          "Exported assignments or independently replayed score differ.");
    }
    System.out.printf(
        "initial=%s best=%s complete=true feasible=%s replay=PASS assignments=%s%n",
        initial, result.score, result.score.isFeasible(), output.toAbsolutePath());
  }

  static SolverConfig config(long seed) {
    return new SolverConfig()
        .withSolutionClass(TaskMachineSolution.class)
        .withEntityClasses(Task.class)
        .withConstraintProviderClass(TaskMachineConstraints.class)
        .withRandomSeed(seed)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()));
  }

  static TaskMachineSolution problem(int size) {
    if (size < 8 || size > 100000) {
      throw new IllegalArgumentException("The task count (" + size + ") must be in [8, 100000].");
    }
    int machineCount = Math.max(4, size / 20);
    long totalLoad = 0;
    for (int i = 0; i < size; i++) totalLoad += units(i);
    long capacity = (totalLoad + machineCount - 1) / machineCount + 20;
    var solution = new TaskMachineSolution();
    for (int i = 0; i < machineCount; i++) solution.machines.add(new Machine(i, capacity));
    for (int i = 0; i < size; i++) {
      solution.tasks.add(
          new Task(
              i, units(i), preferred(i, machineCount), solution.machines.get(i % machineCount)));
    }
    solution.score = replay(solution);
    return solution;
  }

  private static int units(int taskId) {
    return 1 + taskId * 17 % 19;
  }

  private static int preferred(int taskId, int machineCount) {
    return (taskId * 7 + 3) % machineCount;
  }

  /** Pure arithmetic replay, including complete stable-ID and immutable-input checks. */
  static HardSoftScore replay(TaskMachineSolution solution) {
    int machineCount = Math.max(4, solution.tasks.size() / 20);
    if (solution.machines.size() != machineCount) {
      throw new IllegalStateException("Unexpected machine count.");
    }
    long totalLoad = 0;
    for (int i = 0; i < solution.tasks.size(); i++) totalLoad += units(i);
    long capacity = (totalLoad + machineCount - 1) / machineCount + 20;
    for (int i = 0; i < machineCount; i++) {
      if (!solution.machines.get(i).equals(new Machine(i, capacity))) {
        throw new IllegalStateException("Changed machine input: " + i);
      }
    }
    long[] loads = new long[machineCount];
    boolean[] seen = new boolean[solution.tasks.size()];
    long hardPenalty = 0;
    long softPenalty = 0;
    for (var task : solution.tasks) {
      if (task.id < 0
          || task.id >= seen.length
          || seen[task.id]
          || task.units != units(task.id)
          || task.preferredMachine != preferred(task.id, machineCount)) {
        throw new IllegalStateException("Invalid or changed task input: " + task.id);
      }
      seen[task.id] = true;
      if (task.machine == null
          || task.machine.id < 0
          || task.machine.id >= machineCount
          || task.machine != solution.machines.get(task.machine.id)) {
        throw new IllegalStateException(
            "Task is unassigned or uses a noncanonical machine: " + task.id);
      }
      loads[task.machine.id] += task.units;
      softPenalty += (long) Math.abs(task.machine.id - task.preferredMachine) * task.units;
    }
    for (long load : loads) {
      hardPenalty += Math.max(0, load - capacity);
      softPenalty += load * load;
    }
    return HardSoftScore.of(-hardPenalty, -softPenalty);
  }

  static void verify(TaskMachineSolution solution) {
    var recomputed = replay(solution);
    if (!recomputed.equals(solution.score)) {
      throw new IllegalStateException(
          "Independent score mismatch: " + recomputed + " != " + solution.score);
    }
  }

  static int[] assignments(TaskMachineSolution solution) {
    int[] result = new int[solution.tasks.size()];
    for (var task : solution.tasks) result[task.id] = task.machine.id;
    return result;
  }

  static void assign(TaskMachineSolution solution, int[] assignments) {
    if (solution.tasks.size() != assignments.length)
      throw new IllegalArgumentException("Assignment length mismatch.");
    for (var task : solution.tasks) task.machine = solution.machines.get(assignments[task.id]);
  }

  static void writeAssignments(TaskMachineSolution solution, Path output) throws IOException {
    Path parent = output.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    var lines = new ArrayList<String>();
    lines.add("task_id,machine_id");
    int[] assignments = assignments(solution);
    for (int i = 0; i < assignments.length; i++) lines.add(i + "," + assignments[i]);
    Files.write(output, lines);
  }

  static TaskMachineSolution readAssignments(int size, Path input) throws IOException {
    var lines = Files.readAllLines(input);
    if (lines.size() != size + 1 || !lines.getFirst().equals("task_id,machine_id")) {
      throw new IllegalArgumentException("Invalid assignment file: " + input);
    }
    var solution = problem(size);
    boolean[] seen = new boolean[size];
    for (var line : lines.subList(1, lines.size())) {
      String[] cells = line.split(",", -1);
      if (cells.length != 2) throw new IllegalArgumentException("Invalid assignment row: " + line);
      int taskId = Integer.parseInt(cells[0]);
      int machineId = Integer.parseInt(cells[1]);
      if (taskId < 0
          || taskId >= size
          || seen[taskId]
          || machineId < 0
          || machineId >= solution.machines.size()) {
        throw new IllegalArgumentException("Invalid or duplicate assignment: " + line);
      }
      seen[taskId] = true;
      solution.tasks.get(taskId).machine = solution.machines.get(machineId);
    }
    solution.score = replay(solution);
    return solution;
  }

  public record Machine(@PlanningId int id, long capacity) {}

  @PlanningEntity
  public static class Task {
    @PlanningId private int id;
    private int units;
    private int preferredMachine;

    @PlanningVariable(valueRangeProviderRefs = "machines")
    private Machine machine;

    public Task() {}

    Task(int id, int units, int preferredMachine, Machine machine) {
      this.id = id;
      this.units = units;
      this.preferredMachine = preferredMachine;
      this.machine = machine;
    }

    public int getId() {
      return id;
    }

    public int getUnits() {
      return units;
    }

    public int getPreferredMachine() {
      return preferredMachine;
    }

    public Machine getMachine() {
      return machine;
    }

    public void setMachine(Machine machine) {
      this.machine = machine;
    }
  }

  @PlanningSolution
  public static class TaskMachineSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "machines")
    private List<Machine> machines = new ArrayList<>();

    @PlanningEntityCollectionProperty private List<Task> tasks = new ArrayList<>();
    @PlanningScore private HardSoftScore score;

    public TaskMachineSolution() {}

    public List<Machine> getMachines() {
      return machines;
    }

    public List<Task> getTasks() {
      return tasks;
    }

    public HardSoftScore getScore() {
      return score;
    }

    public void setScore(HardSoftScore score) {
      this.score = score;
    }
  }

  public static class TaskMachineConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var loads =
          factory
              .forEach(Task.class)
              .groupBy(Task::getMachine, ConstraintCollectors.sum(Task::getUnits));
      return new Constraint[] {
        loads
            .filter((machine, load) -> load > machine.capacity)
            .penalize(HardSoftScore.ONE_HARD, (machine, load) -> load - machine.capacity)
            .asConstraint("Machine capacity"),
        loads
            .penalize(HardSoftScore.ONE_SOFT, (machine, load) -> (long) load * load)
            .asConstraint("Squared machine load"),
        factory
            .forEach(Task.class)
            .penalize(
                HardSoftScore.ONE_SOFT,
                task -> (long) Math.abs(task.machine.id - task.preferredMachine) * task.units)
            .asConstraint("Machine preference")
      };
    }
  }
}
