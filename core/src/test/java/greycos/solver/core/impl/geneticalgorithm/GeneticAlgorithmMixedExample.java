package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Assignment;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Input;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.MachineInput;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Mode;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.TaskInput;

/** Standalone mixed ordered-task scheduling with strict stable-ID export and independent replay. */
public final class GeneticAlgorithmMixedExample {
  private GeneticAlgorithmMixedExample() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 4 && args.length != 5) {
      throw new IllegalArgumentException(
          "Usage: [<tasks> <seed> <scoreCalls> <assignment.csv> [<localImprovementMoves>]]");
    }
    int size = args.length == 0 ? 80 : Integer.parseInt(args[0]);
    long seed = args.length == 0 ? 37L : Long.parseLong(args[1]);
    long calls = args.length == 0 ? 2000L : Long.parseLong(args[2]);
    Path output =
        Path.of(args.length == 0 ? "target/genetic-algorithm/mixed-assignments.csv" : args[3]);
    long localImprovementMoves = args.length == 5 ? Long.parseLong(args[4]) : 0L;
    if (calls < 2)
      throw new IllegalArgumentException("The score-call budget must be at least two.");
    if (localImprovementMoves < 0)
      throw new IllegalArgumentException("The local-improvement move limit must be nonnegative.");
    var input = problem(size);
    var initial = replay(input);
    var solverConfig =
        config(seed)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(32)
                    .withLocalImprovementMoveCountLimit(localImprovementMoves))
            .withTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(calls));
    var result = SolverFactory.<SequenceSolution>create(solverConfig).buildSolver().solve(input);
    verify(result);
    writeAssignments(result, output);
    var restored = readAssignments(size, output);
    if (!assignments(result).equals(assignments(restored))
        || !replay(restored).equals(result.score)) {
      throw new IllegalStateException(
          "Export/reload changed exact sequence, modes or replay score.");
    }
    System.out.printf(
        "model=mixed initial=%s best=%s complete=true feasible=%s replay=PASS assignments=%s%n",
        initial, result.score, result.score.isFeasible(), output.toAbsolutePath());
  }

  static SolverConfig config(long seed) {
    return new SolverConfig()
        .withSolutionClass(SequenceSolution.class)
        .withEntityClasses(Machine.class, Task.class)
        .withConstraintProviderClass(SequenceConstraints.class)
        .withRandomSeed(seed)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()));
  }

  static SequenceSolution problem(int size) {
    var result = new SequenceSolution();
    result.input = GeneticAlgorithmSequenceDomain.input(size, true);
    result.modes = result.input.modes();
    for (var data : result.input.machines()) result.machines.add(new Machine(data));
    for (var data : result.input.tasks()) result.tasks.add(new Task(data, result.modes.getFirst()));
    assign(result, GeneticAlgorithmSequenceDomain.initial(result.input));
    result.score = replay(result);
    return result;
  }

  static Assignment assignments(SequenceSolution solution) {
    int size = solution.input.tasks().size();
    if (solution.tasks.size() != size
        || solution.machines.size() != solution.input.machines().size()
        || !solution.modes.equals(solution.input.modes())) {
      throw new IllegalStateException("Changed immutable input collections.");
    }
    int[] modes = new int[size];
    for (int i = 0; i < size; i++) {
      var task = solution.tasks.get(i);
      if (task.id != i || !task.data.equals(solution.input.tasks().get(i))) {
        throw new IllegalStateException("Changed task input: " + i);
      }
      if (task.mode == null
          || task.mode.id() < 0
          || task.mode.id() >= solution.modes.size()
          || task.mode != solution.modes.get(task.mode.id())) {
        throw new IllegalStateException("Invalid or noncanonical processing mode: " + i);
      }
      modes[i] = task.mode.id();
    }
    int[][] rows = new int[solution.machines.size()][];
    for (int m = 0; m < rows.length; m++) {
      var machine = solution.machines.get(m);
      if (machine.id != m || !machine.data.equals(solution.input.machines().get(m))) {
        throw new IllegalStateException("Changed machine input: " + m);
      }
      rows[m] = new int[machine.tasks.size()];
      for (int i = 0; i < rows[m].length; i++) {
        var task = machine.tasks.get(i);
        if (task == null || task.id < 0 || task.id >= size || task != solution.tasks.get(task.id)) {
          throw new IllegalStateException("Sequence contains a noncanonical task.");
        }
        if (task.machine != machine || task.index == null || task.index != i) {
          throw new IllegalStateException(
              "Sequence shadow owner/index differs for task: " + task.id);
        }
        rows[m][i] = task.id;
      }
    }
    return new Assignment(rows, modes);
  }

  static void assign(SequenceSolution solution, Assignment assignment) {
    GeneticAlgorithmSequenceDomain.replay(solution.input, assignment, true);
    materializeValidated(solution, assignment);
  }

  /** Materializes a snapshot already checked against this fixture; does no business replay. */
  static void materializeValidated(SequenceSolution solution, Assignment assignment) {
    var modes = assignment.modes();
    for (var task : solution.tasks) {
      task.mode = solution.modes.get(modes[task.id]);
      task.machine = null;
      task.index = null;
    }
    var sequences = assignment.sequences();
    for (var machine : solution.machines) {
      machine.tasks = new ArrayList<>();
      for (int id : sequences[machine.id]) {
        var task = solution.tasks.get(id);
        task.machine = machine;
        task.index = machine.tasks.size();
        machine.tasks.add(task);
      }
    }
  }

  static HardSoftScore replay(SequenceSolution solution) {
    return GeneticAlgorithmSequenceDomain.replay(solution.input, assignments(solution), true);
  }

  static void verify(SequenceSolution solution) {
    var replayed = replay(solution);
    if (!replayed.equals(solution.score)) {
      throw new IllegalStateException(
          "Independent business score differs: " + replayed + " != " + solution.score);
    }
  }

  static void writeAssignments(SequenceSolution solution, Path output) throws IOException {
    verify(solution);
    GeneticAlgorithmSequenceDomain.write(solution.input, assignments(solution), true, output);
  }

  static SequenceSolution readAssignments(int size, Path source) throws IOException {
    var solution = problem(size);
    assign(solution, GeneticAlgorithmSequenceDomain.read(solution.input, true, source));
    solution.score = replay(solution);
    return solution;
  }

  @PlanningEntity
  public static class Machine {
    @PlanningId private int id;
    private MachineInput data;

    @PlanningListVariable(valueRangeProviderRefs = "tasks")
    private List<Task> tasks = new ArrayList<>();

    public Machine() {}

    Machine(MachineInput data) {
      this.id = data.id();
      this.data = data;
    }

    public int getId() {
      return id;
    }

    public MachineInput getData() {
      return data;
    }

    public List<Task> getTasks() {
      return tasks;
    }

    public void setTasks(List<Task> tasks) {
      this.tasks = tasks;
    }
  }

  @PlanningEntity
  public static class Task {
    @PlanningId private int id;
    private TaskInput data;

    @PlanningVariable(valueRangeProviderRefs = "modes")
    private Mode mode;

    @InverseRelationShadowVariable(sourceVariableName = "tasks")
    private Machine machine;

    @IndexShadowVariable(sourceVariableName = "tasks")
    private Integer index;

    public Task() {}

    Task(TaskInput data, Mode mode) {
      this.id = data.id();
      this.data = data;
      this.mode = mode;
    }

    public int getId() {
      return id;
    }

    public TaskInput getData() {
      return data;
    }

    public Mode getMode() {
      return mode;
    }

    public void setMode(Mode mode) {
      this.mode = mode;
    }

    public Machine getMachine() {
      return machine;
    }

    public void setMachine(Machine machine) {
      this.machine = machine;
    }

    public Integer getIndex() {
      return index;
    }

    public void setIndex(Integer index) {
      this.index = index;
    }

    public int getDuration() {
      return (data.duration() + mode.speed() - 1) / mode.speed();
    }
  }

  @PlanningSolution
  public static class SequenceSolution {
    @ProblemFactProperty private Input input;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "modes")
    private List<Mode> modes;

    @PlanningEntityCollectionProperty private List<Machine> machines = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "tasks")
    private List<Task> tasks = new ArrayList<>();

    @PlanningScore private HardSoftScore score;

    public SequenceSolution() {}

    public Input getInput() {
      return input;
    }

    public List<Mode> getModes() {
      return modes;
    }

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

  public static class SequenceConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var assigned =
          factory.forEach(Task.class).filter(task -> task.machine != null && task.index != null);
      return new Constraint[] {
        assigned
            .groupBy(Task::getMachine, ConstraintCollectors.sum(Task::getDuration))
            .filter((machine, load) -> load > machine.data.capacity())
            .penalize(HardSoftScore.ONE_HARD, (machine, load) -> load - machine.data.capacity())
            .asConstraint("Machine capacity"),
        assigned
            .penalize(
                HardSoftScore.ONE_SOFT,
                task ->
                    (task.index + 1L) * task.getDuration() * task.data.weight()
                        + (long) task.data.duration() * task.mode.cost())
            .asConstraint("Position and processing cost"),
        assigned
            .join(
                assigned,
                Joiners.equal(Task::getMachine),
                Joiners.equal(task -> task.index + 1, Task::getIndex))
            .filter((left, right) -> left.data.family() != right.data.family())
            .penalize(
                HardSoftScore.ONE_SOFT,
                (left, right) -> 1L + 3L * Math.abs(left.data.family() - right.data.family()))
            .asConstraint("Adjacent family setup")
      };
    }
  }
}
