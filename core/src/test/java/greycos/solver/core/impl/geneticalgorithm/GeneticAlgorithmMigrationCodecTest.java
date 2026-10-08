package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Model;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Owner;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmListWorkspaceTest.Task;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.multientity.TestdataHerdEntity;
import greycos.solver.core.testcotwin.multientity.TestdataLeadEntity;
import greycos.solver.core.testcotwin.multientity.TestdataMultiEntitySolution;

import org.junit.jupiter.api.Test;

class GeneticAlgorithmMigrationCodecTest {

  @Test
  void entityValuedBasicAssignmentsUseOnlyRecipientEntities() {
    var input = TestdataMultiEntitySolution.generateUninitializedSolution(3, 3);
    for (int i = 0; i < 3; i++) {
      input.getLeadEntityList().get(i).setValue(input.getValueList().get(i));
      input.getHerdEntityList().get(i).setLeadEntity(input.getLeadEntityList().get(i));
    }
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultiEntitySolution, SimpleScore>(
            TestdataMultiEntitySolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataHerdEntity.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("assigned")
                },
            EnvironmentMode.NO_ASSERT);
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var recipient = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      donor.setWorkingSolution(input);
      var workspace = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmMigrationCodec<>(workspace, donor);
      var batch = codec.export(List.of(workspace.genome()), List.of(workspace.score()));
      var clone = donor.cloneWorkingSolution();
      Collections.reverse(clone.getLeadEntityList());
      Collections.reverse(clone.getHerdEntityList());
      clone
          .getHerdEntityList()
          .forEach(herd -> herd.setLeadEntity(clone.getLeadEntityList().getFirst()));
      recipient.setWorkingSolution(clone);
      var target = new GeneticAlgorithmWorkspace<>(recipient, recipient.calculateScore());
      var imported =
          new GeneticAlgorithmMigrationCodec<>(target, recipient)
              .importGenome(batch.getFirst().assignments());
      assertThat(target.transition(imported).valid()).isTrue();
      target.scored(recipient.calculateScore());
      for (var herd : clone.getHerdEntityList()) {
        assertThat(clone.getLeadEntityList()).anyMatch(lead -> lead == herd.getLeadEntity());
        assertThat(input.getLeadEntityList()).noneMatch(lead -> lead == herd.getLeadEntity());
        var donorHerd =
            input.getHerdEntityList().stream()
                .filter(value -> value.getCode().equals(herd.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(herd.getLeadEntity().getCode()).isEqualTo(donorHerd.getLeadEntity().getCode());
      }
      assertThat(
              batch.getFirst().assignments().getBasicChanges().values().stream()
                  .flatMap(List::stream)
                  .map(SolutionAssignments.BasicChangeRecord::value)
                  .filter(TestdataLeadEntity.class::isInstance))
          .noneMatch(input.getLeadEntityList()::contains);
    }
  }

  @Test
  void batchOwnsDetachedIdentitiesAndRebasesDifferentSlotAndListOrdersWithoutScoringDonor() {
    var factory = factory();
    try (var donor = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var recipient = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var fresh = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      var input = problem();
      donor.setWorkingSolution(input);
      var workspace = new GeneticAlgorithmWorkspace<>(donor, donor.calculateScore());
      var codec = new GeneticAlgorithmMigrationCodec<>(workspace, donor);
      var first = workspace.genome();
      var firstScore = workspace.score();
      var values = first.toArray();
      values[1] = 3;
      values[4] = 4;
      var second = new GeneticAlgorithmGenome(values, new int[][] {{0, 3}, {2, 1}});
      assertThat(workspace.transition(second).valid()).isTrue();
      workspace.scored(donor.calculateScore());
      var session = donor.getSession();
      var calls = donor.getCalculationCount();
      var batch = codec.export(List.of(first, second), List.of(firstScore, workspace.score()));
      assertThat(donor.getCalculationCount()).isEqualTo(calls);
      assertThat(donor.getSession()).isSameAs(session);
      assertThat(workspace.genome()).isEqualTo(second);
      var detachedOwner =
          (Owner)
              batch
                  .getFirst()
                  .assignments()
                  .getListChanges()
                  .values()
                  .iterator()
                  .next()
                  .getFirst()
                  .entity();
      assertThat(detachedOwner).isNotSameAs(input.owners.getFirst());
      assertThat(
              batch
                  .getLast()
                  .assignments()
                  .getListChanges()
                  .values()
                  .iterator()
                  .next()
                  .getFirst()
                  .entity())
          .isSameAs(detachedOwner);
      var detachedValues =
          batch
              .getFirst()
              .assignments()
              .getListChanges()
              .values()
              .iterator()
              .next()
              .getFirst()
              .values();
      assertThat(detachedValues.getFirst()).isNotSameAs(input.tasks.getFirst());
      assertThatThrownBy(() -> detachedValues.clear())
          .isInstanceOf(UnsupportedOperationException.class);

      var target = donor.cloneWorkingSolution();
      Collections.reverse(target.owners);
      Collections.reverse(target.tasks);
      target.owners.forEach(owner -> Collections.reverse(owner.range));
      recipient.setWorkingSolution(target);
      var targetWorkspace = new GeneticAlgorithmWorkspace<>(recipient, recipient.calculateScore());
      var targetCodec = new GeneticAlgorithmMigrationCodec<>(targetWorkspace, recipient);
      var recipientSession = recipient.getSession();
      for (var entry : batch) {
        var decoded = targetCodec.importGenome(entry.assignments());
        assertThat(targetWorkspace.transition(decoded).valid()).isTrue();
        targetWorkspace.scored(recipient.calculateScore());
        assertThat(targetWorkspace.score()).isEqualTo(entry.score());
        assertThat(recipient.getSession()).isSameAs(recipientSession);
        fresh.setWorkingSolution(recipient.cloneWorkingSolution());
        assertThat(fresh.calculateScore()).isEqualTo(entry.score());
        for (var owner : target.owners) {
          for (int i = 0; i < owner.tasks.size(); i++) {
            var task = owner.tasks.get(i);
            assertThat(task.owner).isSameAs(owner);
            assertThat(task.index).isEqualTo(i);
            assertThat(target.tasks).anyMatch(value -> value == task);
          }
        }
      }
      var detachedOffset = detachedOwner.offset;
      assertThat(workspace.transition(first).valid()).isTrue();
      workspace.scored(donor.calculateScore());
      assertThat(detachedOwner.offset).isEqualTo(detachedOffset);
      assertThat(detachedValues).extracting(value -> ((Task) value).id).containsExactly(0, 1);
    }
  }

  @Test
  void rejectsMissingAssignmentsBeforeTouchingRecipient() {
    var factory = factory();
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(problem());
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var codec = new GeneticAlgorithmMigrationCodec<>(workspace, director);
      var original = workspace.genome();
      var calls = director.getCalculationCount();
      assertThatThrownBy(() -> codec.importGenome(SolutionAssignments.of(Map.of(), Map.of())))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("omits a movable basic");
      assertThat(workspace.genome()).isEqualTo(original);
      assertThat(director.getCalculationCount()).isEqualTo(calls);
    }
  }

  @Test
  void incumbentSnapshotsMayOmitPinnedOwnersWithoutLosingTheirAssignments() {
    var factory = factory();
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      var input = problem();
      input.owners.getFirst().pinned = true;
      input.tasks.getFirst().pinned = true;
      director.setWorkingSolution(input);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var codec = new GeneticAlgorithmMigrationCodec<>(workspace, director);
      var incumbent = director.cloneWorkingSolution();
      incumbent.owners.get(1).tasks.clear();
      var decoded =
          codec.importGenome(
              SolutionAssignments.capture(director.getSolutionDescriptor(), incumbent));
      assertThat(decoded.list(0)).containsExactly(0, 1);
      assertThat(decoded.list(1)).isEmpty();
      assertThat(workspace.transition(decoded).valid()).isTrue();
      workspace.scored(director.calculateScore());
      assertThat(input.tasks.get(2).owner).isNull();
      assertThat(input.tasks.getFirst().owner).isSameAs(input.owners.getFirst());
    }
  }

  private static BavetConstraintStreamScoreDirectorFactory<Model, SimpleScore> factory() {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(Model.class, Owner.class, Task.class),
        constraints ->
            new Constraint[] {
              constraints
                  .forEach(Owner.class)
                  .penalize(SimpleScore.ONE, owner -> owner.totalDuration)
                  .asConstraint("aggregate"),
              constraints
                  .forEachIncludingUnassigned(Task.class)
                  .penalize(SimpleScore.ONE, task -> task.cumulative)
                  .asConstraint("cascade")
            },
        EnvironmentMode.NO_ASSERT);
  }

  private static Model problem() {
    var model = new Model();
    for (int i = 0; i < 4; i++) {
      var task = new Task();
      task.id = i;
      model.tasks.add(task);
    }
    for (int i = 0; i < 2; i++) {
      var owner = new Owner();
      owner.id = i;
      owner.range = new ArrayList<>(model.tasks);
      owner.tasks.addAll(model.tasks.subList(i * 2, i * 2 + 2));
      model.owners.add(owner);
    }
    model.owners.getFirst().pinIndex = 1;
    return model;
  }
}
