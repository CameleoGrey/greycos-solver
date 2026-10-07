package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GeneticAlgorithmListWorkspaceTest {

  @Test
  void genomeOwnsNestedArraysAndPreservesBasicEquality() {
    var rows = new int[][] {{0, 1}, {2}};
    var genome = new GeneticAlgorithmGenome(new Object[] {new String("equal"), null}, rows);
    rows[0][0] = 5;
    rows[1] = new int[0];
    genome.list(0)[0] = 6;
    genome.lists()[1][0] = 7;
    var equal = new GeneticAlgorithmGenome(new Object[] {"equal", null}, new int[][] {{0, 1}, {2}});
    assertThat(genome).isEqualTo(equal).hasSameHashCodeAs(equal);
    assertThat(genome.listCount()).isEqualTo(2);
    assertThat(genome.list(0)).containsExactly(0, 1);
    assertThat(genome.list(1)).containsExactly(2);
    assertThat(genome)
        .isNotEqualTo(new GeneticAlgorithmGenome(genome.toArray(), new int[][] {{1, 0}, {2}}));
  }

  @Test
  void emptyListUniverseAndEmptyOwnerCollectionAreValidNoChangeStates() {
    for (var lists : List.of(new int[0][], new int[][] {{}, {}})) {
      var solution = solution(0, lists);
      try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
        director.setWorkingSolution(solution);
        var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
        var model = workspace.listModel();
        assertThat(model.ownerCount()).isEqualTo(lists.length);
        assertThat(model.valueCount()).isZero();
        assertThat(model.movableValueIds()).isEmpty();
        var calculationCount = director.getCalculationCount();
        assertThat(workspace.transition(workspace.genome()))
            .isEqualTo(new GeneticAlgorithmWorkspace.Transition(true, 0));
        assertThat(director.getCalculationCount()).isEqualTo(calculationCount);
        assertReplay(solution, director);
      }
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void nonIntSizedListUniverseFailsBeforeEnumerationOrAllocation() {
    InnerScoreDirector<Model, SimpleScore> director = mock(InnerScoreDirector.class);
    ValueRangeManager<Model> manager = mock(ValueRangeManager.class);
    ValueRange<Object> universe = mock(ValueRange.class);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(Model.class, Owner.class, Task.class);
    when(director.getSolutionDescriptor()).thenReturn(descriptor);
    when(director.getValueRangeManager()).thenReturn(manager);
    when(manager.getFromSolution(descriptor.getListVariableDescriptor().getValueRangeDescriptor()))
        .thenReturn(universe);
    for (var size : new long[] {-1, (long) Integer.MAX_VALUE + 1}) {
      when(universe.getSize()).thenReturn(size);
      assertThatThrownBy(() -> new GeneticAlgorithmListModel<>(director))
          .hasMessageContaining("must fit in an int");
    }
    verify(universe, never()).createOriginalIterator();
  }

  @Test
  void unequalMixedTransferUsesMinimalRangesAndRetainsSessionAndExactShadows() {
    var ownerCalls = new HashMap<Integer, Integer>();
    var factory = factory(ownerCalls);
    var solution = solution(8, new int[][] {{0, 1, 2, 3}, {4, 5}, {6}});
    solution.owners.getFirst().pinIndex = 1;
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var previous = workspace.genome();
      var previousScore = workspace.score();
      var bestClone = director.cloneWorkingSolution();
      var session = director.getSession();
      var observer = new Observer();
      director.setWorkingSolutionMutationObserver(observer);
      ownerCalls.clear();
      var basics = previous.toArray();
      setBasic(workspace, basics, solution.owners.get(1), "offset", 2);
      setBasic(workspace, basics, solution.tasks.get(2), "duration", 3);
      var target = new GeneticAlgorithmGenome(basics, new int[][] {{0, 1, 3}, {4, 2, 7, 5}, {6}});
      assertThat(workspace.transition(target))
          .isEqualTo(new GeneticAlgorithmWorkspace.Transition(true, 6));
      workspace.scored(director.calculateScore());
      assertThat(observer.listEvents)
          .containsExactly(
              "beforeAssign:7",
              "before:0:2:3",
              "before:1:1:1",
              "after:0:2:2",
              "after:1:1:3",
              "afterAssign:7");
      assertThat(ownerCalls).containsOnlyKeys(0, 1);
      assertThat(observer.basicEvents).containsExactly("offset:1", "duration:2");
      assertThat(director.getSession()).isSameAs(session);
      assertReplay(solution, director);
      assertThat(previous.list(0)).containsExactly(0, 1, 2, 3);
      assertThat(bestClone.owners.getFirst().tasks)
          .extracting(task -> task.id)
          .containsExactly(0, 1, 2, 3);
      try (var fresh = factory.createScoreDirectorBuilder().build()) {
        fresh.setWorkingSolution(director.cloneWorkingSolution());
        assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
        assertReplay(fresh.getWorkingSolution(), fresh);
      }
      observer.listEvents.clear();
      workspace.restore(previous, previousScore);
      assertThat(observer.listEvents)
          .containsExactly(
              "beforeUnassign:7",
              "before:0:2:2",
              "before:1:1:3",
              "after:0:2:3",
              "after:1:1:1",
              "afterUnassign:7");
      assertThat(workspace.genome()).isEqualTo(previous);
      assertThat(workspace.score()).isEqualTo(previousScore);
      assertThat(director.getSession()).isSameAs(session);
      assertReplay(solution, director);
    }
  }

  @Test
  void optionalHeadTailReorderTransferAndEmptyListsRemainExact() {
    var factory = factory(new HashMap<>());
    var solution = solution(5, new int[][] {{0, 1, 2}, {3}, {}});
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var session = director.getSession();
      for (var lists :
          List.of(
              new int[][] {{4, 0, 1, 2}, {3}, {}},
              new int[][] {{4, 2, 1, 0}, {}, {3}},
              new int[][] {{}, {2, 0}, {}},
              new int[][] {{0, 1, 2, 3, 4}, {}, {}},
              new int[][] {{}, {}, {}})) {
        assertThat(
                workspace
                    .transition(new GeneticAlgorithmGenome(workspace.genome().toArray(), lists))
                    .valid())
            .isTrue();
        workspace.scored(director.calculateScore());
        assertThat(director.getSession()).isSameAs(session);
        assertReplay(solution, director);
        try (var fresh = factory.createScoreDirectorBuilder().build()) {
          fresh.setWorkingSolution(director.cloneWorkingSolution());
          assertThat(fresh.calculateScore()).isEqualTo(workspace.score());
          assertReplay(fresh.getWorkingSolution(), fresh);
        }
      }
      var observer = new Observer();
      director.setWorkingSolutionMutationObserver(observer);
      var count = director.getCalculationCount();
      assertThat(workspace.transition(workspace.genome()))
          .isEqualTo(new GeneticAlgorithmWorkspace.Transition(true, 0));
      assertThat(observer.listEvents).isEmpty();
      assertThat(director.getCalculationCount()).isEqualTo(count);
    }
  }

  @Test
  void wholeMixedPreflightRejectsIllegalMembershipRangesAndPinsWithoutNotifications() {
    var solution = solution(5, new int[][] {{0, 1}, {2}, {3}});
    solution.owners.get(0).pinIndex = 1;
    solution.owners.get(1).range.remove(solution.tasks.get(1));
    solution.owners.get(2).pinned = true;
    try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var previous = workspace.genome();
      var observer = new Observer();
      director.setWorkingSolutionMutationObserver(observer);
      var basics = previous.toArray();
      setBasic(workspace, basics, solution.owners.getFirst(), "offset", 2);
      for (var lists :
          List.of(
              new int[][] {{0, 1, 2}, {2}, {3}},
              new int[][] {{0, -1}, {2}, {3}},
              new int[][] {{0, 5}, {2}, {3}},
              new int[][] {{1, 0}, {2}, {3}},
              new int[][] {{0}, {2, 1}, {3}},
              new int[][] {{0, 1}, {2, 3}, {}},
              new int[][] {{0, 1}, {2}, {3, 4}})) {
        assertThat(workspace.transition(new GeneticAlgorithmGenome(basics, lists)))
            .isEqualTo(new GeneticAlgorithmWorkspace.Transition(false, 0));
        assertThat(workspace.genome()).isEqualTo(previous);
        assertThat(workspace.listModel().captureLists()).isDeepEqualTo(previous.lists());
      }
      assertThat(observer.listEvents).isEmpty();
      assertThat(observer.basicEvents).isEmpty();
      assertThatThrownBy(
              () -> workspace.transition(new GeneticAlgorithmGenome(basics, new int[][] {{0, 1}})))
          .hasMessageContaining("owner count");
    }
  }

  @Test
  void listPositionPinsAndElementBasicPinsAreIndependentAndPinnedEndAllowsAppend() {
    var solution = solution(4, new int[][] {{0}, {1}, {2}});
    solution.owners.getFirst().pinIndex = 1;
    solution.tasks.get(1).pinned = true;
    try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var model = workspace.listModel();
      assertThat(model.movableValueIds()).containsExactly(1, 2, 3);
      var basics = workspace.genome().toArray();
      // A pinned list position does not pin the value's own genuine basic variable.
      setBasic(workspace, basics, solution.tasks.get(0), "duration", 2);
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(basics, new int[][] {{0, 1, 3}, {}, {2}}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertReplay(solution, director);
      // The pinned value moved between owners, but changing its own basic variable is forbidden.
      var rejectedBasics = workspace.genome().toArray();
      setBasic(workspace, rejectedBasics, solution.tasks.get(1), "duration", 2);
      assertThat(
              workspace
                  .transition(
                      new GeneticAlgorithmGenome(rejectedBasics, workspace.genome().lists()))
                  .valid())
          .isFalse();
      assertThat(solution.tasks.get(1).owner).isSameAs(solution.owners.getFirst());
      assertThat(solution.tasks.get(1).duration).isEqualTo(1);
    }
  }

  @Test
  void universeIncludesUnassignedValuesAvailableOnlyToPinnedOwners() {
    var solution = solution(4, new int[][] {{0}, {1}});
    solution.owners.getFirst().range =
        new ArrayList<>(List.of(solution.tasks.get(0), solution.tasks.get(2)));
    solution.owners.get(1).range =
        new ArrayList<>(List.of(solution.tasks.get(1), solution.tasks.get(3)));
    solution.owners.get(1).pinned = true;
    try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var model = workspace.listModel();
      assertThat(model.valueCount()).isEqualTo(4);
      // The union follows the providers' first encounter order, independently of current lists.
      assertThat(model.value(0)).isSameAs(solution.tasks.get(0));
      assertThat(model.value(1)).isSameAs(solution.tasks.get(2));
      assertThat(model.value(2)).isSameAs(solution.tasks.get(1));
      assertThat(model.value(3)).isSameAs(solution.tasks.get(3));
      assertThat(model.accepts(0, 3)).isFalse();
      assertThat(model.accepts(1, 3)).isTrue();
      assertThat(model.ownerMovable(1)).isFalse();
      assertThat(model.movableValueIds()).containsExactly(0, 1, 3);
      var exported = model.initialLists();
      exported[0][0] = 3;
      model.movableValueIds()[0] = 3;
      assertThat(model.initialLists()[0]).containsExactly(0);
      assertThat(model.movableValueIds()).containsExactly(0, 1, 3);
      assertThat(model.isValid(new int[][] {{0, 1}, {2}})).isTrue();
      assertThat(model.isValid(new int[][] {{0, 1, 3}, {2}})).isFalse();
    }
  }

  @Test
  void requiredListCoverageRejectsMissingValuesButTransfersKeepNativeInitialization() {
    var solution = TestdataListSolution.generateInitializedSolution(3, 2);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataListSolution, SimpleScore>(
            TestdataListSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataListValue.class)
                      .penalize(SimpleScore.ONE, value -> value.getIndex())
                      .asConstraint("index")
                },
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var initial = workspace.genome();
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[0], new int[][] {{0}, {1}}))
                  .valid())
          .isFalse();
      assertThat(workspace.genome()).isEqualTo(initial);
      assertThat(
              workspace
                  .transition(
                      new GeneticAlgorithmGenome(new Object[0], new int[][] {{}, {0, 1, 2}}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(workspace.score().isFullyAssigned()).isTrue();
      assertThat(
              director
                  .getListVariableState(
                      director.getSolutionDescriptor().getListVariableDescriptor())
                  .getUnassignedCount())
          .isZero();
      assertThat(solution.getValueList())
          .extracting(TestdataListValue::getIndex)
          .containsExactly(0, 1, 2);
    }
  }

  @Test
  void inheritedWholeOwnerAndPrefixPinsUseTheEffectiveDescriptor() {
    var solution = new InheritedPinSolution();
    var wholePinned = new PinnedListOwner();
    wholePinned.pinned = true;
    wholePinned.pinIndex = -9; // Ignored when the whole owner is pinned.
    wholePinned.values.add(solution.values.getFirst());
    var prefixPinned = new PinnedListOwner();
    prefixPinned.pinIndex = 1;
    prefixPinned.values.add(solution.values.get(1));
    solution.owners.addAll(List.of(wholePinned, prefixPinned));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<InheritedPinSolution, SimpleScore>(
            SolutionDescriptor.buildSolutionDescriptor(
                InheritedPinSolution.class, BaseListOwner.class, PinnedListOwner.class),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(BaseListOwner.class)
                      .penalize(SimpleScore.ONE, owner -> owner.values.size())
                      .asConstraint("size")
                },
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var model = workspace.listModel();
      assertThat(model.ownerCount()).isEqualTo(2);
      assertThat(model.ownerMovable(0)).isFalse();
      assertThat(model.firstUnpinnedIndex(1)).isOne();
      assertThat(model.movableValueIds()).containsExactly(2);
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[0], new int[][] {{0}, {1, 2}}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[0], new int[][] {{0, 2}, {1}}))
                  .valid())
          .isFalse();
      assertThat(
              workspace
                  .transition(new GeneticAlgorithmGenome(new Object[0], new int[][] {{0}, {2, 1}}))
                  .valid())
          .isFalse();
    }
  }

  @Test
  void optionalMembershipWithoutExternalShadowsRestoresNativeState() {
    var solution = TestdataUnassignedMixedSolution.generateUninitializedSolution(2, 3, 1);
    solution
        .getEntityList()
        .forEach(entity -> entity.setSecondBasicValue(solution.getOtherValueList().getFirst()));
    solution.getEntityList().getFirst().getValueList().add(solution.getValueList().getFirst());
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataUnassignedMixedSolution, SimpleScore>(
            TestdataUnassignedMixedSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataUnassignedMixedEntity.class)
                      .penalize(SimpleScore.ONE, entity -> entity.getValueList().size())
                      .asConstraint("size")
                },
            EnvironmentMode.NO_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var before = workspace.genome();
      var beforeScore = workspace.score();
      var state =
          director.getListVariableState(
              director.getSolutionDescriptor().getListVariableDescriptor());
      assertThat(
              workspace
                  .transition(
                      new GeneticAlgorithmGenome(before.toArray(), new int[][] {{}, {1, 2}}))
                  .valid())
          .isTrue();
      workspace.scored(director.calculateScore());
      assertThat(state.getUnassignedCount()).isEqualTo(1);
      assertThat(state.isAssigned(solution.getValueList().getFirst())).isFalse();
      assertThat(state.getInverseSingleton(solution.getValueList().get(2)))
          .isSameAs(solution.getEntityList().get(1));
      workspace.restore(before, beforeScore);
      assertThat(state.getUnassignedCount()).isEqualTo(2);
      assertThat(state.getInverseSingleton(solution.getValueList().getFirst()))
          .isSameAs(solution.getEntityList().getFirst());
      assertThat(state.isAssigned(solution.getValueList().get(1))).isFalse();
      assertThat(workspace.score()).isEqualTo(beforeScore);
    }
  }

  @Test
  void noncanonicalInitialListValueFailsAndCascadeFailureAborts() {
    var solution = solution(2, new int[][] {{0, 1}});
    try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var initialScore = director.calculateScore();
      var impostor = new Task();
      impostor.id = 0;
      solution.owners.getFirst().tasks.set(0, impostor);
      assertThatThrownBy(() -> new GeneticAlgorithmWorkspace<>(director, initialScore))
          .hasMessageContaining("canonical value range");
    }
    solution = solution(2, new int[][] {{0, 1}});
    try (var director = factory(new HashMap<>()).createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      solution.tasks.getFirst().failCascade = true;
      assertThatThrownBy(
              () ->
                  workspace.transition(
                      new GeneticAlgorithmGenome(
                          workspace.genome().toArray(), new int[][] {{1, 0}})))
          .hasRootCauseMessage("injected cascade failure");
    }
  }

  private static void setBasic(
      GeneticAlgorithmWorkspace<Model, SimpleScore> workspace,
      Object[] basics,
      Object entity,
      String name,
      Object value) {
    for (var i = 0; i < workspace.slots().size(); i++) {
      var slot = workspace.slots().get(i);
      if (slot.entity() == entity && slot.variableDescriptor().getVariableName().equals(name)) {
        basics[i] = value;
        return;
      }
    }
    throw new AssertionError("Missing slot " + name);
  }

  private static void assertReplay(
      Model solution, InnerScoreDirector<Model, SimpleScore> director) {
    var assigned = new boolean[solution.tasks.size()];
    var penalty = 0;
    for (var owner : solution.owners) {
      var total = 0;
      var cumulative = 0;
      for (var index = 0; index < owner.tasks.size(); index++) {
        var task = owner.tasks.get(index);
        assertThat(assigned[task.id]).isFalse();
        assigned[task.id] = true;
        assertThat(task.owner).isSameAs(owner);
        assertThat(task.index).isEqualTo(index);
        assertThat(task.previous).isSameAs(index == 0 ? null : owner.tasks.get(index - 1));
        assertThat(task.next)
            .isSameAs(index + 1 == owner.tasks.size() ? null : owner.tasks.get(index + 1));
        assertThat(task.paddedDuration).isEqualTo(task.duration + 1);
        total += task.duration + 1;
        cumulative += task.duration + 1 + owner.offset;
        assertThat(task.cumulative).isEqualTo(cumulative);
        penalty += cumulative;
      }
      assertThat(owner.totalDuration).isEqualTo(total);
      penalty += total;
    }
    var unassigned = 0;
    for (var task : solution.tasks) {
      if (!assigned[task.id]) {
        unassigned++;
        assertThat(task.owner).isNull();
        assertThat(task.index).isNull();
        assertThat(task.previous).isNull();
        assertThat(task.next).isNull();
        assertThat(task.cumulative).isZero();
      }
    }
    assertThat(
            director
                .getListVariableState(director.getSolutionDescriptor().getListVariableDescriptor())
                .getUnassignedCount())
        .isEqualTo(unassigned);
    assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-penalty));
  }

  private static BavetConstraintStreamScoreDirectorFactory<Model, SimpleScore> factory(
      Map<Integer, Integer> calls) {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(Model.class, Owner.class, Task.class),
        constraints ->
            new Constraint[] {
              constraints
                  .forEach(Owner.class)
                  .penalize(
                      SimpleScore.ONE,
                      owner -> {
                        calls.merge(owner.id, 1, Integer::sum);
                        return owner.totalDuration;
                      })
                  .asConstraint("aggregate"),
              constraints
                  .forEachIncludingUnassigned(Task.class)
                  .penalize(SimpleScore.ONE, task -> task.cumulative)
                  .asConstraint("cascade")
            },
        EnvironmentMode.NO_ASSERT);
  }

  private static Model solution(int taskCount, int[][] lists) {
    var solution = new Model();
    for (var id = 0; id < taskCount; id++) {
      var task = new Task();
      task.id = id;
      solution.tasks.add(task);
    }
    for (var id = 0; id < lists.length; id++) {
      var owner = new Owner();
      owner.id = id;
      owner.range.addAll(solution.tasks);
      Arrays.stream(lists[id]).mapToObj(solution.tasks::get).forEach(owner.tasks::add);
      solution.owners.add(owner);
    }
    return solution;
  }

  private static final class Observer implements WorkingSolutionMutationObserver<Model> {
    final List<String> listEvents = new ArrayList<>();
    final List<String> basicEvents = new ArrayList<>();

    @Override
    public void workingSolutionChanged() {
      throw new AssertionError("The retained session must not be rebuilt.");
    }

    @Override
    public void beforeVariableChanged(Object entity, String name) {
      if (name.equals("offset")) basicEvents.add(name + ":" + ((Owner) entity).id);
      if (name.equals("duration")) basicEvents.add(name + ":" + ((Task) entity).id);
    }

    @Override
    public void beforeListVariableChanged(Object entity, String name, int from, int to) {
      listEvents.add("before:" + ((Owner) entity).id + ":" + from + ":" + to);
    }

    @Override
    public void afterListVariableChanged(Object entity, String name, int from, int to) {
      listEvents.add("after:" + ((Owner) entity).id + ":" + from + ":" + to);
    }

    @Override
    public void beforeListVariableElementAssigned(String name, Object value) {
      listEvents.add("beforeAssign:" + ((Task) value).id);
    }

    @Override
    public void afterListVariableElementAssigned(String name, Object value) {
      listEvents.add("afterAssign:" + ((Task) value).id);
    }

    @Override
    public void beforeListVariableElementUnassigned(String name, Object value) {
      listEvents.add("beforeUnassign:" + ((Task) value).id);
    }

    @Override
    public void afterListVariableElementUnassigned(String name, Object value) {
      listEvents.add("afterUnassign:" + ((Task) value).id);
    }
  }

  @PlanningSolution
  public static class Model {
    @PlanningEntityCollectionProperty public List<Owner> owners = new ArrayList<>();
    @PlanningEntityCollectionProperty public List<Task> tasks = new ArrayList<>();

    @ValueRangeProvider(id = "settings")
    public List<Integer> settings = List.of(1, 2, 3, 4);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class Owner {
    @PlanningId public int id;
    @PlanningPin public boolean pinned;
    @PlanningPinToIndex public int pinIndex;

    @ValueRangeProvider(id = "tasks")
    public List<Task> range = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "tasks", allowsUnassignedValues = true)
    public List<Task> tasks = new ArrayList<>();

    @PlanningVariable(valueRangeProviderRefs = "settings")
    public Integer offset = 1;

    @ShadowVariable(supplierName = "totalDuration")
    public Integer totalDuration;

    @ShadowSources("tasks[].paddedDuration")
    public Integer totalDuration() {
      var total = 0;
      for (var task : tasks) {
        if (task.paddedDuration == null) return null;
        total += task.paddedDuration;
      }
      return total;
    }
  }

  @PlanningEntity
  public static class Task {
    @PlanningId public int id;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "settings")
    public Integer duration = 1;

    @InverseRelationShadowVariable(sourceVariableName = "tasks")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "tasks")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "tasks")
    public Task previous;

    @NextElementShadowVariable(sourceVariableName = "tasks")
    public Task next;

    @ShadowVariable(supplierName = "paddedDuration")
    public Integer paddedDuration;

    @CascadingUpdateShadowVariable(targetMethodName = "updateCumulative")
    public Integer cumulative;

    public boolean failCascade;

    @ShadowSources("duration")
    public Integer paddedDuration() {
      return duration + 1;
    }

    public void updateCumulative() {
      if (failCascade) throw new IllegalStateException("injected cascade failure");
      cumulative =
          owner == null
              ? 0
              : paddedDuration + owner.offset + (previous == null ? 0 : previous.cumulative);
    }
  }

  @PlanningSolution
  public static class InheritedPinSolution {
    @PlanningEntityCollectionProperty public List<BaseListOwner> owners = new ArrayList<>();

    @ProblemFactCollectionProperty @ValueRangeProvider
    public List<Integer> values = List.of(1, 2, 3);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class BaseListOwner {
    @PlanningListVariable(allowsUnassignedValues = true)
    public List<Integer> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class PinnedListOwner extends BaseListOwner {
    @PlanningPin public boolean pinned;
    @PlanningPinToIndex public int pinIndex;
  }
}
