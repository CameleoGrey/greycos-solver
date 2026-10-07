package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmSequenceDomain.Assignment;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneticAlgorithmSequenceExampleTest {
  @TempDir Path directory;

  @Test
  void listAndMixedReplayAgreeForInitialOverloadedReorderedAndModeAssignments() {
    checkReplay(GeneticAlgorithmListBenchmark.listModel());
    checkReplay(GeneticAlgorithmListBenchmark.mixedModel());
  }

  private static <S> void checkReplay(GeneticAlgorithmListBenchmark.Model<S> model) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<S, HardSoftScore>(
            model.descriptor(), model.constraints(), EnvironmentMode.NO_ASSERT);
    var input = model.problem().apply(80);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(input);
      assertThat(director.calculateScore().raw()).isEqualTo(model.replay(input));
      var overloaded = model.problem().apply(80);
      int[][] rows = new int[model.input().apply(overloaded).machines().size()][];
      for (int m = 0; m < rows.length; m++) rows[m] = new int[0];
      rows[0] = java.util.stream.IntStream.range(0, 80).toArray();
      model.assign().accept(overloaded, new Assignment(rows, new int[80]));
      director.setWorkingSolution(overloaded);
      assertThat(model.replay(overloaded).isFeasible()).isFalse();
      assertThat(director.calculateScore().raw()).isEqualTo(model.replay(overloaded));
      var candidates =
          GeneticAlgorithmListBenchmark.candidates(model.input().apply(input), 8, 37L, "broad");
      for (var candidate : candidates) {
        var changed = model.problem().apply(80);
        model.assign().accept(changed, candidate);
        director.setWorkingSolution(changed);
        assertThat(director.calculateScore().raw()).isEqualTo(model.replay(changed));
      }
    }
  }

  @Test
  void bothModelsExportAndReloadExactSequencesModesAndImmutableInput() throws IOException {
    var list = GeneticAlgorithmListExample.problem(80);
    var listFile = directory.resolve("list.csv");
    GeneticAlgorithmListExample.writeAssignments(list, listFile);
    reverseRows(listFile);
    var listRestored = GeneticAlgorithmListExample.readAssignments(80, listFile);
    assertThat(GeneticAlgorithmListExample.assignments(listRestored))
        .isEqualTo(GeneticAlgorithmListExample.assignments(list));
    assertThat(listRestored.getTasks().getFirst()).isNotSameAs(list.getTasks().getFirst());
    assertThat(listRestored.getInput()).isEqualTo(list.getInput());
    assertThat(listRestored.getScore()).isEqualTo(list.getScore());

    var mixed = GeneticAlgorithmMixedExample.problem(80);
    var candidate = GeneticAlgorithmListBenchmark.candidates(mixed.getInput(), 1, 83L, "broad")[0];
    GeneticAlgorithmMixedExample.assign(mixed, candidate);
    mixed.setScore(GeneticAlgorithmMixedExample.replay(mixed));
    var mixedFile = directory.resolve("mixed.csv");
    GeneticAlgorithmMixedExample.writeAssignments(mixed, mixedFile);
    reverseRows(mixedFile);
    var mixedRestored = GeneticAlgorithmMixedExample.readAssignments(80, mixedFile);
    assertThat(GeneticAlgorithmMixedExample.assignments(mixedRestored)).isEqualTo(candidate);
    assertThat(mixedRestored.getTasks().getFirst()).isNotSameAs(mixed.getTasks().getFirst());
    assertThat(mixedRestored.getInput()).isEqualTo(mixed.getInput());
    assertThat(mixedRestored.getScore()).isEqualTo(mixed.getScore());
  }

  private static void reverseRows(Path file) throws IOException {
    var lines = Files.readAllLines(file);
    Collections.reverse(lines.subList(2, lines.size()));
    Files.write(file, lines);
  }

  @Test
  void readRejectsDuplicateMissingGapAndChangedInputRows() throws IOException {
    var input = GeneticAlgorithmMixedExample.problem(80);
    var file = directory.resolve("bad.csv");
    GeneticAlgorithmMixedExample.writeAssignments(input, file);
    var valid = Files.readAllLines(file);
    var duplicate = new java.util.ArrayList<>(valid);
    duplicate.set(2, duplicate.get(3));
    Files.write(file, duplicate);
    assertThatThrownBy(() -> GeneticAlgorithmMixedExample.readAssignments(80, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
    var gap = new java.util.ArrayList<>(valid);
    var cells = gap.get(2).split(",");
    gap.set(2, cells[0] + ",79," + cells[2] + "," + cells[3]);
    Files.write(file, gap);
    assertThatThrownBy(() -> GeneticAlgorithmMixedExample.readAssignments(80, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Noncontiguous");
    Files.write(file, valid.subList(0, valid.size() - 1));
    assertThatThrownBy(() -> GeneticAlgorithmMixedExample.readAssignments(80, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("row count");
    Files.write(file, valid);
    assertThatThrownBy(() -> GeneticAlgorithmListExample.readAssignments(80, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("incompatible input");
  }

  @Test
  void replayRejectsIncompleteNoncanonicalAndChangedInputModels() {
    var incomplete = GeneticAlgorithmListExample.problem(80);
    incomplete.getMachines().getFirst().getTasks().removeFirst();
    assertThatThrownBy(() -> GeneticAlgorithmListExample.replay(incomplete))
        .isInstanceOf(IllegalStateException.class);
    var foreign = GeneticAlgorithmListExample.problem(80);
    var other = GeneticAlgorithmListExample.problem(80);
    foreign.getMachines().getFirst().getTasks().set(0, other.getTasks().getFirst());
    assertThatThrownBy(() -> GeneticAlgorithmListExample.replay(foreign))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noncanonical");
    var mixed = GeneticAlgorithmMixedExample.problem(80);
    mixed.getTasks().getFirst().setMode(new GeneticAlgorithmSequenceDomain.Mode(0, 1, 0));
    assertThatThrownBy(() -> GeneticAlgorithmMixedExample.replay(mixed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noncanonical");
    var canonical = GeneticAlgorithmSequenceDomain.input(80, false);
    var changedTasks = new java.util.ArrayList<>(canonical.tasks());
    changedTasks.set(0, new GeneticAlgorithmSequenceDomain.TaskInput(0, 99, 3, 1));
    var changed =
        new GeneticAlgorithmSequenceDomain.Input(
            changedTasks, canonical.machines(), canonical.modes());
    assertThatThrownBy(
            () ->
                GeneticAlgorithmSequenceDomain.replay(
                    changed, GeneticAlgorithmSequenceDomain.initial(canonical), false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Changed immutable");
  }

  @Test
  void retainedAndReplacementEvaluatorsAgreeOnEverySparseAndBroadCandidate() {
    checkEvaluators(GeneticAlgorithmListBenchmark.listModel());
    checkEvaluators(GeneticAlgorithmListBenchmark.mixedModel());
  }

  private static <S> void checkEvaluators(GeneticAlgorithmListBenchmark.Model<S> model) {
    var input = GeneticAlgorithmSequenceDomain.input(24, model.mixed());
    for (var shape : java.util.List.of("sparse", "broad")) {
      var candidates = GeneticAlgorithmListBenchmark.candidates(input, 8, 11L, shape);
      var expected =
          Arrays.stream(candidates)
              .map(
                  candidate ->
                      GeneticAlgorithmSequenceDomain.replay(input, candidate, model.mixed()))
              .toArray(HardSoftScore[]::new);
      var retained =
          GeneticAlgorithmListBenchmark.evaluate(model, 24, candidates, expected, true, true);
      var replacement =
          GeneticAlgorithmListBenchmark.evaluate(model, 24, candidates, expected, false, true);
      assertThat(retained.score()).isEqualTo(replacement.score());
      assertThat(retained.fingerprint()).isEqualTo(replacement.fingerprint());
      assertThat(retained.scoreCalls()).isEqualTo(8);
      assertThat(replacement.scoreCalls()).isEqualTo(8);
      assertThat(retained.sessionBuilds()).isEqualTo(1);
      assertThat(replacement.sessionBuilds()).isEqualTo(9);
      assertThat(retained.distance()).isEqualTo(replacement.distance());
    }
  }

  @Test
  void everySearchSupportsBothModelsAndExportsReplayCheckedAssignments() throws IOException {
    for (var algorithm : java.util.List.of("GA", "LS", "ALNS")) {
      var list =
          GeneticAlgorithmListBenchmark.search(
              GeneticAlgorithmListBenchmark.listModel(),
              24,
              37L,
              algorithm,
              "score_calls",
              80,
              directory.resolve("list-" + algorithm + ".csv"));
      var mixed =
          GeneticAlgorithmListBenchmark.search(
              GeneticAlgorithmListBenchmark.mixedModel(),
              24,
              37L,
              algorithm,
              "score_calls",
              80,
              directory.resolve("mixed-" + algorithm + ".csv"));
      assertThat(list.score()).isGreaterThanOrEqualTo(list.initial());
      assertThat(mixed.score()).isGreaterThanOrEqualTo(mixed.initial());
      assertThat(list.scoreCalls()).isPositive();
      assertThat(mixed.scoreCalls()).isPositive();
      assertThat(list.target()).isEqualTo(HardSoftScore.of(0, list.initial().softScore() * 9 / 10));
      assertThat(mixed.target())
          .isEqualTo(HardSoftScore.of(0, mixed.initial().softScore() * 9 / 10));
    }
  }
}
