package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.Machine;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.Task;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.TaskMachineConstraints;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmExample.TaskMachineSolution;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneticAlgorithmExampleTest {
  @TempDir Path directory;

  @Test
  void replayAgreesWithBavetForInitialAndOverloadedAssignments() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(TaskMachineSolution.class, Task.class);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TaskMachineSolution, HardSoftScore>(
            descriptor, new TaskMachineConstraints(), EnvironmentMode.NO_ASSERT);
    var input = GeneticAlgorithmExample.problem(80);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(input);
      assertThat(director.calculateScore().raw()).isEqualTo(GeneticAlgorithmExample.replay(input));
      var overloaded = GeneticAlgorithmExample.problem(80);
      GeneticAlgorithmExample.assign(overloaded, new int[80]);
      director.setWorkingSolution(overloaded);
      var replayed = GeneticAlgorithmExample.replay(overloaded);
      assertThat(replayed.isFeasible()).isFalse();
      assertThat(director.calculateScore().raw()).isEqualTo(replayed);
    }
  }

  @Test
  void exportReloadUsesTaskIdsAndRejectsDuplicateRows() throws IOException {
    var input = GeneticAlgorithmExample.problem(80);
    var output = directory.resolve("assignments.csv");
    GeneticAlgorithmExample.writeAssignments(input, output);
    var lines = Files.readAllLines(output);
    Collections.reverse(lines.subList(1, lines.size()));
    Files.write(output, lines);
    var restored = GeneticAlgorithmExample.readAssignments(80, output);
    assertThat(GeneticAlgorithmExample.assignments(restored))
        .containsExactly(GeneticAlgorithmExample.assignments(input));
    assertThat(restored.getScore()).isEqualTo(input.getScore());
    lines.set(1, lines.get(2));
    Files.write(output, lines);
    assertThatThrownBy(() -> GeneticAlgorithmExample.readAssignments(80, output))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate assignment");
  }

  @Test
  void replayRejectsIncompleteAndNoncanonicalAssignments() {
    var input = GeneticAlgorithmExample.problem(80);
    input.getTasks().getFirst().setMachine(null);
    assertThatThrownBy(() -> GeneticAlgorithmExample.replay(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unassigned");
    var original = input.getMachines().getFirst();
    input.getTasks().getFirst().setMachine(new Machine(original.id(), original.capacity()));
    assertThatThrownBy(() -> GeneticAlgorithmExample.replay(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noncanonical");
  }
}
