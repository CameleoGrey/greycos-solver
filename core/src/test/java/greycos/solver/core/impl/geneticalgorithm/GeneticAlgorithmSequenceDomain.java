package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;

/** Immutable business input and solver-independent ordered-assignment persistence/replay. */
public final class GeneticAlgorithmSequenceDomain {
  private GeneticAlgorithmSequenceDomain() {}

  public record TaskInput(int id, int duration, int family, int weight) {}

  public record MachineInput(int id, long capacity) {}

  public record Mode(int id, int speed, int cost) {}

  public record Input(List<TaskInput> tasks, List<MachineInput> machines, List<Mode> modes) {
    public Input {
      tasks = List.copyOf(tasks);
      machines = List.copyOf(machines);
      modes = List.copyOf(modes);
    }
  }

  static final class Assignment {
    private final int[][] sequences;
    private final int[] modes;

    Assignment(int[][] sequences, int[] modes) {
      this.sequences = Arrays.stream(sequences).map(int[]::clone).toArray(int[][]::new);
      this.modes = modes.clone();
    }

    int[][] sequences() {
      return Arrays.stream(sequences).map(int[]::clone).toArray(int[][]::new);
    }

    int[] modes() {
      return modes.clone();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Assignment assignment
          && Arrays.deepEquals(sequences, assignment.sequences)
          && Arrays.equals(modes, assignment.modes);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.deepHashCode(sequences) + Arrays.hashCode(modes);
    }

    String fingerprint() {
      return MoveThreadingWorkload.fingerprint(
          Arrays.deepToString(sequences) + Arrays.toString(modes));
    }
  }

  static Input input(int size, boolean mixed) {
    if (size < 8 || size > 100000) {
      throw new IllegalArgumentException("The task count (" + size + ") must be in [8, 100000].");
    }
    var tasks = new ArrayList<TaskInput>();
    long total = 0;
    for (int i = 0; i < size; i++) {
      int duration = 1 + i * 17 % 19;
      tasks.add(new TaskInput(i, duration, (i * 7 + 3) % 5, 1 + i % 4));
      total += duration;
    }
    int count = Math.max(4, size / 20);
    long capacity = (total + count - 1) / count + 40;
    var machines = new ArrayList<MachineInput>();
    for (int i = 0; i < count; i++) machines.add(new MachineInput(i, capacity));
    var modes =
        mixed
            ? List.of(new Mode(0, 1, 0), new Mode(1, 2, 4), new Mode(2, 3, 9))
            : List.of(new Mode(0, 1, 0));
    return new Input(tasks, machines, modes);
  }

  static Assignment initial(Input input) {
    var rows = new ArrayList<List<Integer>>();
    for (int i = 0; i < input.machines.size(); i++) rows.add(new ArrayList<>());
    for (var task : input.tasks) rows.get(task.id % rows.size()).add(task.id);
    return new Assignment(
        rows.stream()
            .map(row -> row.stream().mapToInt(Integer::intValue).toArray())
            .toArray(int[][]::new),
        new int[input.tasks.size()]);
  }

  /** Arithmetic uses only immutable business records and stable IDs, never shadows or Bavet. */
  static HardSoftScore replay(Input input, Assignment assignment, boolean mixed) {
    if (!input.equals(input(input.tasks.size(), mixed))) {
      throw new IllegalStateException("Changed immutable business input.");
    }
    int size = input.tasks.size();
    if (assignment.sequences.length != input.machines.size() || assignment.modes.length != size) {
      throw new IllegalStateException("Assignment dimensions differ from the immutable input.");
    }
    boolean[] seen = new boolean[size];
    long hard = 0;
    long soft = 0;
    for (int m = 0; m < assignment.sequences.length; m++) {
      long load = 0;
      int previousFamily = -1;
      int[] row = assignment.sequences[m];
      for (int index = 0; index < row.length; index++) {
        int id = row[index];
        if (id < 0 || id >= size || seen[id]) {
          throw new IllegalStateException("Invalid or duplicate task assignment: " + id);
        }
        seen[id] = true;
        int modeId = assignment.modes[id];
        if (modeId < 0 || modeId >= input.modes.size()) {
          throw new IllegalStateException("Invalid processing mode for task: " + id);
        }
        var task = input.tasks.get(id);
        var mode = input.modes.get(modeId);
        long duration = (task.duration + mode.speed - 1L) / mode.speed;
        load += duration;
        soft += (index + 1L) * duration * task.weight + (long) task.duration * mode.cost;
        if (previousFamily >= 0 && previousFamily != task.family) {
          soft += 1L + 3L * Math.abs(previousFamily - task.family);
        }
        previousFamily = task.family;
      }
      hard += Math.max(0L, load - input.machines.get(m).capacity);
    }
    for (int id = 0; id < size; id++) {
      if (!seen[id]) throw new IllegalStateException("Unassigned task: " + id);
    }
    return HardSoftScore.of(-hard, -soft);
  }

  static long distance(Assignment left, Assignment right) {
    int size = left.modes.length;
    int[] leftOwner = new int[size];
    int[] leftIndex = new int[size];
    for (int m = 0; m < left.sequences.length; m++) {
      for (int i = 0; i < left.sequences[m].length; i++) {
        leftOwner[left.sequences[m][i]] = m;
        leftIndex[left.sequences[m][i]] = i;
      }
    }
    long distance = 0;
    for (int m = 0; m < right.sequences.length; m++) {
      for (int i = 0; i < right.sequences[m].length; i++) {
        int id = right.sequences[m][i];
        if (leftOwner[id] != m || leftIndex[id] != i) distance++;
        if (left.modes[id] != right.modes[id]) distance++;
      }
    }
    return distance;
  }

  static void write(Input input, Assignment assignment, boolean mixed, Path output)
      throws IOException {
    replay(input, assignment, mixed);
    var lines = new ArrayList<String>();
    lines.add(header(input, mixed));
    lines.add("machine_id,index,task_id,mode_id");
    for (int m = 0; m < assignment.sequences.length; m++) {
      for (int i = 0; i < assignment.sequences[m].length; i++) {
        int id = assignment.sequences[m][i];
        lines.add(m + "," + i + "," + id + "," + assignment.modes[id]);
      }
    }
    Path target = output.toAbsolutePath();
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".sequence-", ".csv");
    try {
      Files.write(temporary, lines);
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new IOException("Atomic assignment export is not supported for " + target, exception);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static Assignment read(Input input, boolean mixed, Path source) throws IOException {
    var lines = Files.readAllLines(source);
    int size = input.tasks.size();
    if (lines.size() != size + 2
        || !lines.getFirst().equals(header(input, mixed))
        || !lines.get(1).equals("machine_id,index,task_id,mode_id")) {
      throw new IllegalArgumentException(
          "Assignment file has incompatible input/header/row count: " + source);
    }
    var indexed = new ArrayList<Map<Integer, Integer>>();
    for (int m = 0; m < input.machines.size(); m++) indexed.add(new HashMap<>());
    int[] modes = new int[size];
    boolean[] seen = new boolean[size];
    for (var line : lines.subList(2, lines.size())) {
      var cells = line.split(",", -1);
      if (cells.length != 4) throw new IllegalArgumentException("Invalid assignment row: " + line);
      int machine = Integer.parseInt(cells[0]);
      int index = Integer.parseInt(cells[1]);
      int task = Integer.parseInt(cells[2]);
      int mode = Integer.parseInt(cells[3]);
      if (machine < 0
          || machine >= indexed.size()
          || index < 0
          || index >= size
          || task < 0
          || task >= size
          || mode < 0
          || mode >= input.modes.size()
          || seen[task]
          || indexed.get(machine).containsKey(index)) {
        throw new IllegalArgumentException("Invalid or duplicate assignment: " + line);
      }
      indexed.get(machine).put(index, task);
      seen[task] = true;
      modes[task] = mode;
    }
    int[][] rows = new int[indexed.size()][];
    for (int m = 0; m < rows.length; m++) {
      var row = indexed.get(m);
      rows[m] = new int[row.size()];
      for (int i = 0; i < rows[m].length; i++) {
        Integer task = row.get(i);
        if (task == null)
          throw new IllegalArgumentException("Noncontiguous sequence indices for machine: " + m);
        rows[m][i] = task;
      }
    }
    var result = new Assignment(rows, modes);
    replay(input, result, mixed);
    return result;
  }

  private static String header(Input input, boolean mixed) {
    return "sequence-v1,tasks,"
        + input.tasks.size()
        + ",mixed,"
        + mixed
        + ",input_sha256,"
        + MoveThreadingWorkload.fingerprint(input.toString());
  }
}
