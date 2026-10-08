package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.phaseStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.stepStarted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.valuerange.pinned.TestdataListPinnedEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.pinned.TestdataListPinnedEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.TestdataListUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.TestdataListUnassignedEntityProvidingSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
@SuppressWarnings("unchecked")
class GeneticAlgorithmLocalImprovementMovesTest {

  @Test
  void basicChangesSampleLongRecipientRangesWithoutEnumerationAndSkipImmovableSlots() {
    ValueRange<Object> largeRange = mock(ValueRange.class);
    long size = 1L << 40;
    when(largeRange.getSize()).thenReturn(size);
    when(largeRange.get(size - 1)).thenReturn(size - 1);
    BasicVariableDescriptor<Object> descriptor = mock(BasicVariableDescriptor.class);
    var movableEntity = new Object();
    var movable = new GeneticAlgorithmSlot<>(movableEntity, descriptor, largeRange, true);
    ValueRange<Object> pinnedRange = mock(ValueRange.class);
    var pinned = new GeneticAlgorithmSlot<>(new Object(), descriptor, pinnedRange, false);
    var workspace = workspace(List.of(pinned, movable), null);
    var moves = new GeneticAlgorithmLocalImprovementMoves<Object>(List.of());
    moves.initialize(workspace);
    var random = mock(RandomGenerator.class);
    when(random.nextLong(size)).thenReturn(size - 1);

    var move = (SelectorBasedChangeMove<Object>) moves.nextMove(random);

    assertThat(move.getEntity()).isSameAs(movableEntity);
    assertThat(move.getVariableDescriptor()).isSameAs(descriptor);
    assertThat(move.getToPlanningValue()).isEqualTo(size - 1);
    verify(largeRange).get(size - 1);
    verifyNoInteractions(pinnedRange);
  }

  @Test
  void basicChangesRetainNullAndCurrentValueSamplesAsProbes() {
    ValueRange<Object> range = mock(ValueRange.class);
    when(range.getSize()).thenReturn(2L);
    when(range.get(0L)).thenReturn(null);
    when(range.get(1L)).thenReturn("current");
    BasicVariableDescriptor<Object> descriptor = mock(BasicVariableDescriptor.class);
    var entity = new Object();
    when(descriptor.getValue(entity)).thenReturn("current");
    var moves = new GeneticAlgorithmLocalImprovementMoves<Object>(List.of());
    moves.initialize(
        workspace(List.of(new GeneticAlgorithmSlot<>(entity, descriptor, range, true)), null));
    var random = mock(RandomGenerator.class);
    when(random.nextLong(2L)).thenReturn(0L, 1L);

    var first = (SelectorBasedChangeMove<Object>) moves.nextMove(random);
    var second = (SelectorBasedChangeMove<Object>) moves.nextMove(random);

    assertThat(first.getToPlanningValue()).isNull();
    assertThat(second.getToPlanningValue()).isEqualTo("current");
    assertThat(second.isMoveDoable(null)).isFalse();
  }

  @Test
  void familiesHaveEqualSelectionWeightAndResetDiscardsStaleListMoves() {
    var firstListMove = mock(Move.class);
    var secondListMove = mock(Move.class);
    MoveSelector<Object> change = mock(MoveSelector.class);
    MoveSelector<Object> swap = mock(MoveSelector.class);
    when(change.iterator()).thenAnswer(ignored -> List.<Move<Object>>of(firstListMove).iterator());
    when(swap.iterator()).thenAnswer(ignored -> List.<Move<Object>>of(secondListMove).iterator());
    ValueRange<Object> range = mock(ValueRange.class);
    when(range.getSize()).thenReturn(2L);
    BasicVariableDescriptor<Object> descriptor = mock(BasicVariableDescriptor.class);
    var moves = new GeneticAlgorithmLocalImprovementMoves<>(List.of(change, swap));
    moves.initialize(
        workspace(
            List.of(new GeneticAlgorithmSlot<>(new Object(), descriptor, range, true)),
            movableListModel()));
    var random = mock(RandomGenerator.class);
    when(random.nextInt(3)).thenReturn(0, 1, 2, 1);

    assertThat(moves.nextMove(random)).isInstanceOf(SelectorBasedChangeMove.class);
    assertThat(moves.nextMove(random)).isSameAs(firstListMove);
    assertThat(moves.nextMove(random)).isSameAs(secondListMove);
    moves.reset();
    assertThat(moves.nextMove(random)).isSameAs(firstListMove);
  }

  @Test
  void exhaustedFamiliesEndAndEmptyOrFullyPinnedModelsDoNotStartListSelectors() {
    MoveSelector<Object> selector = mock(MoveSelector.class);
    when(selector.iterator()).thenAnswer(ignored -> List.<Move<Object>>of().iterator());
    var moves = new GeneticAlgorithmLocalImprovementMoves<>(List.of(selector));
    moves.initialize(workspace(List.of(), movableListModel()));
    assertThat(moves.nextMove(new Random(0))).isNull();

    for (int shape = 0; shape < 3; shape++) {
      MoveSelector<Object> inactive = mock(MoveSelector.class);
      GeneticAlgorithmListModel<Object> model = mock(GeneticAlgorithmListModel.class);
      when(model.ownerCount()).thenReturn(shape == 0 ? 0 : 1);
      when(model.ownerMovable(0)).thenReturn(shape != 2);
      when(model.movableValueIds()).thenReturn(shape == 1 ? new int[0] : new int[] {0});
      var inactiveMoves = new GeneticAlgorithmLocalImprovementMoves<>(List.of(inactive));
      inactiveMoves.initialize(workspace(List.of(), model));
      SolverScope<Object> solver = mock(SolverScope.class);
      AbstractPhaseScope<Object> phase = mock(AbstractPhaseScope.class);
      AbstractStepScope<Object> step = mock(AbstractStepScope.class);
      inactiveMoves.solvingStarted(solver);
      inactiveMoves.phaseStarted(phase);
      inactiveMoves.stepStarted(step);
      assertThat(inactiveMoves.nextMove(new Random(0))).isNull();
      inactiveMoves.stepEnded(step);
      inactiveMoves.phaseEnded(phase);
      inactiveMoves.solvingEnded(solver);
      verifyNoInteractions(inactive);
    }
  }

  @Test
  void selectorCleanupContinuesAfterFailureAndDoesNotRepeatStepCleanup() {
    MoveSelector<Object> first = mock(MoveSelector.class);
    MoveSelector<Object> second = mock(MoveSelector.class);
    var moves = new GeneticAlgorithmLocalImprovementMoves<>(List.of(first, second));
    moves.initialize(workspace(List.of(), movableListModel()));
    SolverScope<Object> solver = mock(SolverScope.class);
    AbstractPhaseScope<Object> phase = mock(AbstractPhaseScope.class);
    AbstractStepScope<Object> step = mock(AbstractStepScope.class);
    moves.solvingStarted(solver);
    moves.phaseStarted(phase);
    moves.stepStarted(step);
    var firstFailure = new IllegalStateException("first cleanup");
    var secondFailure = new IllegalStateException("second cleanup");
    doThrow(firstFailure).when(first).stepEnded(step);
    doThrow(secondFailure).when(second).stepEnded(step);

    assertThatThrownBy(() -> moves.stepEnded(step)).isSameAs(firstFailure);
    assertThat(firstFailure.getSuppressed()).containsExactly(secondFailure);
    moves.stepEnded(step);
    moves.phaseEnded(phase);
    moves.solvingEnded(solver);

    for (var selector : List.of(first, second)) {
      verify(selector).solvingStarted(solver);
      verify(selector).phaseStarted(phase);
      verify(selector).stepStarted(step);
      verify(selector).stepEnded(step);
      verify(selector).phaseEnded(phase);
      verify(selector).solvingEnded(solver);
    }
  }

  @Test
  void nativeListFamiliesPreservePinsRecipientRangesAndUndoAcrossAcceptedChanges() {
    var values =
        List.of(
            new TestdataValue("p"),
            new TestdataValue("a"),
            new TestdataValue("b"),
            new TestdataValue("c"),
            new TestdataValue("q"));
    var first = new TestdataListPinnedEntityProvidingEntity("first", values.subList(0, 3));
    first.setValueList(new ArrayList<>(values.subList(0, 2)));
    first.setPinIndex(1);
    var second = new TestdataListPinnedEntityProvidingEntity("second", values.subList(1, 4));
    second.setValueList(new ArrayList<>(values.subList(2, 4)));
    var pinned = new TestdataListPinnedEntityProvidingEntity("pinned", values.subList(4, 5));
    pinned.setValueList(new ArrayList<>(values.subList(4, 5)));
    pinned.setPinned(true);
    var solution = new TestdataListPinnedEntityProvidingSolution();
    solution.setEntityList(List.of(first, second, pinned));
    var descriptor = TestdataListPinnedEntityProvidingSolution.buildSolutionDescriptor();
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataListPinnedEntityProvidingSolution, SimpleScore>(
            descriptor,
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataListPinnedEntityProvidingEntity.class)
                      .penalize(SimpleScore.ONE, owner -> owner.getValueList().size())
                      .asConstraint("assigned")
                },
            EnvironmentMode.NO_ASSERT);
    var moves =
        DefaultGeneticAlgorithmPhaseFactory.buildLocalImprovementMoves(
            buildHeuristicConfigPolicy(descriptor));
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var model = workspace.listModel();
      var session = director.getSession();
      moves.initialize(workspace);
      var random = new Random(37);
      var solver = solvingStarted(moves, director, random);
      var phase = phaseStarted(moves, solver);
      var step = stepStarted(moves, phase);
      try {
        for (int i = 0; i < 150; i++) {
          var move = moves.nextMove(random);
          assertThat(move).isNotNull();
          if (!((AbstractSelectorBasedMove<TestdataListPinnedEntityProvidingSolution>) move)
              .isMoveDoable(director)) {
            continue;
          }
          var baseline = model.captureLists();
          director.executeTemporaryMove(
              move,
              ignored -> {
                assertThat(model.isValid(model.captureLists())).isTrue();
                assertThat(first.getValueList().getFirst()).isSameAs(values.getFirst());
                assertThat(pinned.getValueList()).containsExactly(values.getLast());
                solution
                    .getEntityList()
                    .forEach(
                        owner ->
                            assertThat(owner.getValueList())
                                .allMatch(owner.getValueRange()::contains));
              },
              false);
          assertThat(Arrays.deepEquals(model.captureLists(), baseline)).isTrue();
          assertThat(director.getSession()).isSameAs(session);
          if (i % 3 == 0) {
            director.executeMove(move);
            director.calculateScore();
            moves.reset();
          }
        }
      } finally {
        moves.stepEnded(step);
        moves.phaseEnded(phase);
        moves.solvingEnded(solver);
      }
    }
  }

  @Test
  void nativeListFamiliesIncludeAllowedAssignmentAndUnassignmentWithRecipientRanges() {
    var values =
        List.of(
            new TestdataValue("a"),
            new TestdataValue("b"),
            new TestdataValue("c"),
            new TestdataValue("d"));
    var first =
        new TestdataListUnassignedEntityProvidingEntity(
            "first", values.subList(0, 3), new ArrayList<>(List.of(values.get(0))));
    var second =
        new TestdataListUnassignedEntityProvidingEntity(
            "second", values.subList(1, 4), new ArrayList<>(List.of(values.get(2))));
    var solution = new TestdataListUnassignedEntityProvidingSolution();
    solution.setEntityList(List.of(first, second));
    var descriptor = TestdataListUnassignedEntityProvidingSolution.buildSolutionDescriptor();
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataListUnassignedEntityProvidingSolution, SimpleScore>(
            descriptor,
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataListUnassignedEntityProvidingEntity.class)
                      .penalize(SimpleScore.ONE, owner -> owner.getValueList().size())
                      .asConstraint("assigned")
                },
            EnvironmentMode.NO_ASSERT);
    var moves =
        DefaultGeneticAlgorithmPhaseFactory.buildLocalImprovementMoves(
            buildHeuristicConfigPolicy(descriptor));
    var sawAssignment = new AtomicBoolean();
    var sawUnassignment = new AtomicBoolean();
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
      var model = workspace.listModel();
      moves.initialize(workspace);
      var random = new Random(47);
      var solver = solvingStarted(moves, director, random);
      var phase = phaseStarted(moves, solver);
      var step = stepStarted(moves, phase);
      try {
        for (int i = 0; i < 150; i++) {
          var move = moves.nextMove(random);
          assertThat(move).isNotNull();
          if (!((AbstractSelectorBasedMove<TestdataListUnassignedEntityProvidingSolution>) move)
              .isMoveDoable(director)) {
            continue;
          }
          var baseline = model.captureLists();
          var probeScore =
              director.executeTemporaryMove(
                  move,
                  ignored -> {
                    assertThat(model.isValid(model.captureLists())).isTrue();
                    int assigned = first.getValueList().size() + second.getValueList().size();
                    if (assigned > 2) sawAssignment.set(true);
                    if (assigned < 2) sawUnassignment.set(true);
                    solution
                        .getEntityList()
                        .forEach(
                            owner ->
                                assertThat(owner.getValueList())
                                    .allMatch(owner.getValueRange()::contains));
                  },
                  false);
          assertThat(probeScore.isFullyAssigned()).isTrue();
          assertThat(Arrays.deepEquals(model.captureLists(), baseline)).isTrue();
        }
      } finally {
        moves.stepEnded(step);
        moves.phaseEnded(phase);
        moves.solvingEnded(solver);
      }
    }
    assertThat(sawAssignment).isTrue();
    assertThat(sawUnassignment).isTrue();
  }

  private static GeneticAlgorithmWorkspace<Object, SimpleScore> workspace(
      List<GeneticAlgorithmSlot<Object>> slots, GeneticAlgorithmListModel<Object> model) {
    GeneticAlgorithmWorkspace<Object, SimpleScore> workspace =
        mock(GeneticAlgorithmWorkspace.class);
    when(workspace.slots()).thenReturn(slots);
    when(workspace.listModel()).thenReturn(model);
    return workspace;
  }

  private static GeneticAlgorithmListModel<Object> movableListModel() {
    GeneticAlgorithmListModel<Object> model = mock(GeneticAlgorithmListModel.class);
    when(model.ownerCount()).thenReturn(1);
    when(model.ownerMovable(0)).thenReturn(true);
    when(model.movableValueIds()).thenReturn(new int[] {0});
    return model;
  }
}
