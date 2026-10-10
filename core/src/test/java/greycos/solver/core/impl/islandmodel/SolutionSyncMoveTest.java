package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.IslandTerminationBudget;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListConstraintProvider;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.unassignedvar.TestdataUnassignedMixedSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataListElementConstraintProvider;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataListElementEntity;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataListElementSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataListElementValue;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementEntity;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class SolutionSyncMoveTest {

  @ParameterizedTest
  @MethodSource("listAssignments")
  void optionalAssignmentsApplyAndUndoExactly(
      int[][] initialAssignments, int[][] targetAssignments) {
    var scope = optionalScope();
    scope.setInitialSolution(optionalProblem(initialAssignments));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      assertOptionalState(director, initialAssignments);
      var originalScore = director.getWorkingSolution().getScore();
      var source = director.cloneWorkingSolution();
      setLists(source, targetAssignments);
      var move = SolutionSyncMove.createMove(director, source);
      var undo =
          director
              .getMoveDirector()
              .executeTemporaryProducingUndoMove(
                  move,
                  score -> {
                    assertOptionalState(director, targetAssignments);
                    director.assertWorkingScoreFromScratch(score, move);
                  });
      assertThat(director.getWorkingSolution().getScore()).isEqualTo(originalScore);
      assertOptionalState(director, initialAssignments);
      director.executeMove(move);
      assertOptionalState(director, targetAssignments);
      director.executeMove(undo);
      assertOptionalState(director, initialAssignments);
    }
  }

  static Stream<Arguments> listAssignments() {
    return Stream.of(
        Arguments.of(new int[][] {{0, 1}, {}}, new int[][] {{0}, {}}),
        Arguments.of(new int[][] {{}, {}}, new int[][] {{0, 1}, {}}),
        Arguments.of(new int[][] {{0, 1}, {2}}, new int[][] {{3}, {1}}),
        Arguments.of(new int[][] {{0, 1}, {2, 3}}, new int[][] {{3, 1}, {2, 0}}),
        Arguments.of(new int[][] {{0, 1}, {}}, new int[][] {{}, {}}),
        Arguments.of(new int[][] {{0}, {1}}, new int[][] {{0}, {1}}));
  }

  @Test
  void callbackFailureRestoresAssignmentsAndStoredScore() {
    var scope = optionalScope();
    var before = new int[][] {{0, 1}, {2}};
    scope.setInitialSolution(optionalProblem(before));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      assertOptionalState(director, before);
      var originalScore = director.getWorkingSolution().getScore();
      var source = director.cloneWorkingSolution();
      setLists(source, new int[][] {{3}, {1}});
      var move = SolutionSyncMove.createMove(director, source);
      var failure = new IllegalStateException("score callback failed");
      assertThatThrownBy(
              () ->
                  director
                      .getMoveDirector()
                      .executeTemporaryProducingUndoMove(
                          move,
                          score -> {
                            throw failure;
                          }))
          .isSameAs(failure);
      assertThat(director.getWorkingSolution().getScore()).isEqualTo(originalScore);
      assertOptionalState(director, before);
    }
  }

  @Test
  void optionalAssignmentsAndRetainedUndoRebaseToAnotherWorkingClone() {
    var scope = optionalScope();
    var destinationScope = optionalScope();
    var before = new int[][] {{0, 1}, {2}};
    var after = new int[][] {{3}, {1}};
    scope.setInitialSolution(optionalProblem(before));
    destinationScope.setInitialSolution(optionalProblem(before));
    try (var director = scope.<SimpleScore>getScoreDirector();
        var destination = destinationScope.<SimpleScore>getScoreDirector()) {
      assertOptionalState(director, before);
      var source = director.cloneWorkingSolution();
      setLists(source, after);
      var move = SolutionSyncMove.createMove(director, source);
      var undo = director.getMoveDirector().executeTemporaryProducingUndoMove(move, score -> {});
      destination.executeMove(move.rebase(destination));
      assertOptionalState(destination, after);
      destination.executeMove(undo.rebase(destination.getMoveDirector()));
      assertOptionalState(destination, before);
      assertOptionalState(director, before);
      assertThat(destination.getWorkingSolution().getValueList())
          .noneMatch(
              value ->
                  director.getWorkingSolution().getValueList().stream()
                      .anyMatch(other -> other == value));
    }
  }

  @Test
  void requiredListAssignmentCountsAreRestoredByUndo() {
    var scope =
        SolutionSyncMoveTest.<TestdataListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
                .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class));
    scope.setInitialSolution(TestdataListSolution.generateUninitializedSolution(2, 1));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().getValueList().addAll(source.getValueList());
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              SolutionSyncMove.createMove(director, source),
              score -> {
                assertThat(director.getWorkingInitScore()).isZero();
                assertThat(score.isFullyAssigned()).isTrue();
              });
      assertThat(director.getWorkingInitScore()).isEqualTo(-2);
      assertThat(director.calculateScore()).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidPinnedTargetFailsBeforeChangingEarlierEntities(boolean tooShort) {
    var scope = pinnedScope();
    var initial = pinnedProblem();
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().getValueList().clear();
      var pinnedList = source.getEntityList().getLast().getValueList();
      if (tooShort) {
        pinnedList.clear();
      } else {
        pinnedList.set(0, source.getValueList().getFirst());
      }
      var move = SolutionSyncMove.createMove(director, source);
      assertThatThrownBy(() -> director.executeMove(move))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              tooShort ? "smaller than pinned index" : "Pinned list segment differs");
      var working = director.getWorkingSolution();
      assertThat(working.getEntityList().getFirst().getValueList())
          .containsExactly(working.getValueList().getFirst());
      assertThat(working.getEntityList().getLast().getValueList())
          .containsExactly(working.getValueList().get(1));
      assertThat(director.calculateScore()).isEqualTo(before);
      director.assertWorkingScoreFromScratch(before, move);
    }
  }

  @Test
  void invalidPinnedTargetDoesNotChangeBasicVariables() {
    var scope =
        SolutionSyncMoveTest.<TestdataUnassignedMixedSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataUnassignedMixedSolution.class, TestdataUnassignedMixedEntity.class)
                .withEasyScoreCalculatorClass(TestdataUnassignedMixedEasyScoreCalculator.class)
                .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(context -> {})));
    var initial = TestdataUnassignedMixedSolution.generateUninitializedSolution(1, 2, 2);
    var entity = initial.getEntityList().getFirst();
    entity.setBasicValue(initial.getOtherValueList().getFirst());
    entity.setSecondBasicValue(initial.getOtherValueList().getFirst());
    entity.getValueList().add(initial.getValueList().getFirst());
    entity.setPinnedIndex(1);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().setBasicValue(source.getOtherValueList().getLast());
      source.getEntityList().getFirst().getValueList().set(0, source.getValueList().getLast());
      var move = SolutionSyncMove.createMove(director, source);
      assertThatThrownBy(() -> director.executeMove(move))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Pinned list segment differs");
      var working = director.getWorkingSolution();
      assertThat(working.getEntityList().getFirst().getBasicValue())
          .isSameAs(working.getOtherValueList().getFirst());
      assertThat(working.getEntityList().getFirst().getValueList())
          .containsExactly(working.getValueList().getFirst());
      assertThat(director.calculateScore()).isEqualTo(before);
    }
  }

  @Test
  void optionalAssignmentStateWithoutExternalShadowsIsRestored() {
    var scope =
        SolutionSyncMoveTest.<TestdataUnassignedMixedSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataUnassignedMixedSolution.class, TestdataUnassignedMixedEntity.class)
                .withEasyScoreCalculatorClass(TestdataUnassignedMixedEasyScoreCalculator.class)
                .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(context -> {})));
    var initial = TestdataUnassignedMixedSolution.generateUninitializedSolution(1, 2, 1);
    initial.getEntityList().getFirst().setSecondBasicValue(initial.getOtherValueList().getFirst());
    initial.getEntityList().getFirst().getValueList().add(initial.getValueList().getFirst());
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var working = director.getWorkingSolution();
      var state =
          director.getListVariableState(
              director.getSolutionDescriptor().getListVariableDescriptor());
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().getValueList().set(0, source.getValueList().getLast());
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              SolutionSyncMove.createMove(director, source),
              score -> {
                assertThat(state.isAssigned(working.getValueList().getFirst())).isFalse();
                assertThat(state.isAssigned(working.getValueList().getLast())).isTrue();
                assertThat(state.getUnassignedCount()).isEqualTo(1);
              });
      assertThat(state.isAssigned(working.getValueList().getFirst())).isTrue();
      assertThat(state.isAssigned(working.getValueList().getLast())).isFalse();
      assertThat(state.getUnassignedCount()).isEqualTo(1);
      assertThat(director.calculateScore()).isEqualTo(before);
    }
  }

  @Test
  void optionalListMigrationPreservesPinnedPrefix() {
    var scope = pinnedScope();
    scope.setInitialSolution(pinnedProblem());
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().getValueList().clear();
      source.getEntityList().getLast().getValueList().add(source.getValueList().get(2));
      var move = SolutionSyncMove.createMove(director, source);
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getEntityList().getLast().getValueList())
                    .containsExactly(working.getValueList().get(1), working.getValueList().get(2));
                assertThat(working.getValueList().getFirst().getEntity()).isNull();
                assertThat(working.getValueList().getFirst().getIndex()).isNull();
                assertThat(working.getValueList().get(1).getIndex()).isZero();
                assertThat(working.getValueList().get(2).getIndex()).isEqualTo(1);
                director.assertWorkingScoreFromScratch(score, move);
              });
      assertThat(director.calculateScore()).isEqualTo(before);
      director.assertWorkingScoreFromScratch(before, move);
    }
  }

  @Test
  void entirelyPinnedEntityIsUnchanged() {
    var scope =
        SolutionSyncMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataPinnedWithIndexListSolution.class,
                    TestdataPinnedWithIndexListEntity.class,
                    TestdataPinnedWithIndexListValue.class)
                .withEasyScoreCalculatorClass(
                    TestdataPinnedWithIndexListEasyScoreCalculator.class));
    var initial = TestdataPinnedWithIndexListSolution.generateInitializedSolution(4, 2);
    initial.getEntityList().getFirst().setPinned(true);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      java.util.Collections.reverse(source.getEntityList().getLast().getValueList());
      director.executeMove(SolutionSyncMove.createMove(director, source));
      var working = director.getWorkingSolution();
      assertThat(working.getEntityList().getFirst().getValueList())
          .containsExactly(working.getValueList().get(0), working.getValueList().get(2));
      assertThat(working.getEntityList().getLast().getValueList())
          .containsExactly(working.getValueList().get(3), working.getValueList().get(1));
      assertThat(director.calculateScore()).isEqualTo(before);
    }
  }

  @Test
  void strictUpgradeRetainsPinnedTargetAcrossPhaseRebasingAndSourceMutation() {
    var scope =
        SolutionSyncMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataPinnedWithIndexListSolution.class,
                    TestdataPinnedWithIndexListEntity.class,
                    TestdataPinnedWithIndexListValue.class)
                .withEasyScoreCalculatorClass(
                    TestdataPinnedWithIndexListEasyScoreCalculator.class));
    var initial = TestdataPinnedWithIndexListSolution.generateInitializedSolution(4, 2);
    initial.getEntityList().getFirst().setPinned(true);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      var sourcePinnedValues = new ArrayList<>(source.getEntityList().getFirst().getValueList());
      java.util.Collections.reverse(sourcePinnedValues);
      source.getEntityList().getFirst().setValueList(sourcePinnedValues);
      var pending = SolutionSyncMove.createMove(director, source);
      // Mutation after capture must not silently repair the stale pinned target.
      java.util.Collections.reverse(sourcePinnedValues);
      director.setWorkingSolution(director.cloneWorkingSolution());
      var rebased = pending.rebase(director);
      // The legacy phase behavior still omits pinned entities.
      director.executeMove(rebased);
      var strict = rebased.toStrictMove(director);
      assertThatThrownBy(() -> director.executeMove(strict))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Pinned");
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(director.getWorkingSolution().getEntityList().getFirst().getValueList())
          .containsExactly(
              director.getWorkingSolution().getValueList().get(0),
              director.getWorkingSolution().getValueList().get(2));
    }
  }

  @Test
  void optionalAssignmentsUpdateDeclarativeDependenciesAndUndo() {
    var scope =
        SolutionSyncMoveTest.<TestdataListElementSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                    TestdataListElementSolution.class,
                    TestdataListElementEntity.class,
                    TestdataListElementValue.class)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withScoreDirectorFactory(
                    new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(TestdataListElementConstraintProvider.class)));
    var initial = TestdataListElementSolution.generateSolution(2, 4);
    initial.getEntities().get(0).getValues().addAll(initial.getValues().subList(0, 2));
    initial.getEntities().get(1).getValues().add(initial.getValues().get(2));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntities().get(0).setValues(new ArrayList<>(List.of(source.getValues().get(3))));
      source.getEntities().get(1).setValues(new ArrayList<>(List.of(source.getValues().get(1))));
      var move = SolutionSyncMove.createMove(director, source);
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getEntities().get(0).getLastEndTime()).isEqualTo(1);
                assertThat(working.getEntities().get(1).getLastEndTime()).isEqualTo(3);
                for (var index : List.of(0, 2)) {
                  var value = working.getValues().get(index);
                  assertThat(value.getEntity()).isNull();
                  assertThat(value.getStartTime()).isNull();
                  assertThat(value.getEndTime()).isNull();
                }
                assertThat(score).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-204)));
                director.assertWorkingScoreFromScratch(score, move);
              });
      var working = director.getWorkingSolution();
      assertThat(working.getEntities().get(0).getLastEndTime()).isEqualTo(3);
      assertThat(working.getEntities().get(1).getLastEndTime()).isEqualTo(4);
      assertThat(working.getValues().get(3).getEntity()).isNull();
      assertThat(working.getValues().get(3).getEndTime()).isNull();
      assertThat(director.calculateScore()).isEqualTo(before);
      director.assertWorkingScoreFromScratch(before, move);
    }
  }

  @Test
  void mixedVariablesAndDeclarativeListAggregatesApplyAndUndo() {
    var scope =
        SolutionSyncMoveTest.<TestdataMixedListElementSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                TestdataMixedListElementSolution.class,
                TestdataMixedListElementEntity.class,
                TestdataMixedListElementValue.class));
    var value0 = new TestdataMixedListElementValue("v0");
    value0.setDuration(1);
    var value1 = new TestdataMixedListElementValue("v1");
    value1.setDuration(2);
    var entity0 = new TestdataMixedListElementEntity("e0");
    var entity1 = new TestdataMixedListElementEntity("e1");
    entity0.getValues().add(value0);
    entity1.getValues().add(value1);
    var initial = new TestdataMixedListElementSolution();
    initial.setEntities(List.of(entity0, entity1));
    initial.setValues(List.of(value0, value1));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getValues().getFirst().setDuration(5);
      source.getEntities().getFirst().getValues().clear();
      source.getEntities().getLast().getValues().add(source.getValues().getFirst());
      var move = SolutionSyncMove.createMove(director, source);
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getValues().getFirst().getPaddedDuration()).isEqualTo(6);
                assertThat(working.getEntities().getFirst().getTotalDuration()).isZero();
                assertThat(working.getEntities().getLast().getTotalDuration()).isEqualTo(9);
                assertThat(
                        director
                            .getListVariableState(
                                director.getSolutionDescriptor().getListVariableDescriptor())
                            .getUnassignedCount())
                    .isZero();
              });
      var working = director.getWorkingSolution();
      assertThat(working.getValues().getFirst().getDuration()).isEqualTo(1);
      assertThat(working.getValues().getFirst().getPaddedDuration()).isEqualTo(2);
      assertThat(working.getEntities().getFirst().getTotalDuration()).isEqualTo(2);
      assertThat(working.getEntities().getLast().getTotalDuration()).isEqualTo(3);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void improvingGlobalMigrantIsCorrectInLocalSearch(boolean threaded) {
    var scope = optionalScope();
    scope.setSolverMetricSet(EnumSet.of(SolverMetric.MOVE_COUNT_PER_TYPE));
    var before = new int[][] {{0, 1}, {}};
    var after = new int[][] {{0}, {}};
    scope.setInitialSolution(optionalProblem(before));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      scope.setBestScore(director.calculateScore());
      var phase = new LocalSearchPhaseScope<>(scope, 0);
      phase.reset();
      var budget =
          new IslandTerminationBudget<TestdataAllowsUnassignedValuesListSolution>(
              new TerminationConfig().withMoveCountLimit(1L),
              mock(HeuristicConfigPolicy.class),
              scope.getClock(),
              scope.getClock().millis());
      var quota = budget.createIslandTermination(scope);
      quota.solvingStarted(scope);
      quota.phaseStarted(phase);
      assertThat(quota.isPhaseTerminated(phase)).isFalse();
      queueGlobalMigrant(new LocalSearchStepScope<>(phase), after);
      var termination =
          PhaseTermination.bridge(
              new BasicPlumbingTermination<TestdataAllowsUnassignedValuesListSolution>(false));
      LocalSearchDecider<TestdataAllowsUnassignedValuesListSolution> decider =
          threaded
              ? new MultiThreadedLocalSearchDecider<>(
                  "",
                  termination,
                  mock(MoveSelectorBasedMoveRepository.class),
                  mock(Acceptor.class),
                  mock(LocalSearchForager.class),
                  Thread::new,
                  2,
                  4)
              : new LocalSearchDecider<>(
                  "",
                  termination,
                  mock(MoveSelectorBasedMoveRepository.class),
                  mock(Acceptor.class),
                  mock(LocalSearchForager.class));
      decider.enableAssertions(EnvironmentMode.FULL_ASSERT);
      decider.phaseStarted(phase);
      try {
        var step = new LocalSearchStepScope<>(phase);
        decider.decideNextStep(step);
        assertThat(step.getScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-5)));
        assertThat(step.getSelectedMoveCount()).isEqualTo(1L);
        assertThat(step.getAcceptedMoveCount()).isEqualTo(1L);
        assertThat(scope.getMoveEvaluationCount()).isEqualTo(1L);
        assertThat(scope.getReportedMoveEvaluationCount()).isEqualTo(1L);
        assertThat(scope.getMoveEvaluationCountPerType())
            .containsOnlyKeys(step.getStep().describe())
            .containsEntry(step.getStep().describe(), 1L);
        assertThat(quota.isPhaseTerminated(phase)).isTrue();
        assertOptionalState(director, before);
        director.executeMove(step.getStep());
        assertOptionalState(director, after);
      } finally {
        decider.phaseEnded(phase);
      }
    }
  }

  @Test
  void workersReplayMigrationBeforeScoringAnotherAssignmentChange() throws InterruptedException {
    var scope = optionalScope();
    scope.setInitialSolution(optionalProblem(new int[][] {{0, 1}, {2}}));
    try (var director = scope.<SimpleScore>getScoreDirector();
        var executor = Executors.newFixedThreadPool(2);
        var pipeline =
            new MoveEvaluationPipeline<TestdataAllowsUnassignedValuesListSolution>(
                executor, 2, 4, 0, false, true, true, true, true, true)) {
      director.calculateScore();
      pipeline.start(director);
      var source = director.cloneWorkingSolution();
      setLists(source, new int[][] {{3}, {1}});
      var move = SolutionSyncMove.createMove(director, source);
      pipeline.applyStep(1, move, InnerScore.fullyAssigned(SimpleScore.of(-6)));
      director.executeMove(move);
      assertOptionalState(director, new int[][] {{3}, {1}});
      var nextSource = director.cloneWorkingSolution();
      setLists(nextSource, new int[][] {{}, {0, 1}});
      var nextMove = SolutionSyncMove.createMove(director, nextSource);
      pipeline.submit(0, nextMove);
      pipeline.submit(1, nextMove);
      assertThat(pipeline.takeScore(1, 0)).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-6)));
      assertThat(pipeline.takeScore(1, 1)).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-6)));
      assertOptionalState(director, new int[][] {{3}, {1}});
    }
  }

  @ParameterizedTest
  @CsvSource({"NONE, PROBES", "2, PROBES", "2, REPAIR_ATTEMPTS"})
  void alnsAdoptsOptionalMigrantBeforeTheNextTrial(String threads, AlnsMoveThreadingMode mode) {
    var alns =
        new AlnsPhaseConfig()
            .withMoveThreadCount(threads)
            .withMoveThreadingMode(mode)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(2));
    if (mode == AlnsMoveThreadingMode.REPAIR_ATTEMPTS) {
      alns.withRepairAttemptCount(2)
          .withRepairOperators(
              new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY));
    }
    var solver =
        (DefaultSolver<TestdataAllowsUnassignedValuesListSolution>)
            SolverFactory.<TestdataAllowsUnassignedValuesListSolution>create(
                    optionalConfig().withPhases(alns))
                .buildSolver();
    var observedTrials = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(
              AbstractPhaseScope<TestdataAllowsUnassignedValuesListSolution> phase) {
            queueGlobalMigrant(
                new AlnsStepScope<>(
                    (AlnsPhaseScope<TestdataAllowsUnassignedValuesListSolution>) phase),
                new int[][] {{0}, {}});
          }

          @Override
          public void stepStarted(
              AbstractStepScope<TestdataAllowsUnassignedValuesListSolution> step) {
            if (observedTrials.getAndIncrement() == 0) {
              assertOptionalState(step.getScoreDirector(), new int[][] {{0}, {}});
              assertThat(step.getPhaseScope().getBestScore())
                  .isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-5)));
            }
          }
        });
    var result = solver.solve(optionalProblem(new int[][] {{0, 1}, {}}));
    assertThat(observedTrials.get()).isEqualTo(2);
    var assignedCount =
        result.getEntityList().stream().mapToInt(entity -> entity.getValueList().size()).sum();
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-4 - assignedCount));
  }

  private static void queueGlobalMigrant(
      AbstractStepScope<TestdataAllowsUnassignedValuesListSolution> step, int[][] assignments) {
    var migrantScope = optionalScope();
    migrantScope.setInitialSolution(optionalProblem(assignments));
    try (var migrantDirector = migrantScope.<SimpleScore>getScoreDirector()) {
      var migrantScore = migrantDirector.calculateScore();
      var global = new SharedGlobalState<TestdataAllowsUnassignedValuesListSolution>();
      global.tryUpdate(migrantDirector.cloneWorkingSolution(), migrantScore);
      new GlobalCompareListener<>(
              global, IslandModelConfig.builder().withReceiveGlobalUpdateFrequency(1).build(), 0)
          .stepEnded(step);
    }
  }

  private static SolverConfig optionalConfig() {
    return PlannerTestUtils.buildSolverConfig(
            TestdataAllowsUnassignedValuesListSolution.class,
            TestdataAllowsUnassignedValuesListEntity.class,
            TestdataAllowsUnassignedValuesListValue.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(OptionalListScoreProvider.class));
  }

  private static SolverScope<TestdataAllowsUnassignedValuesListSolution> optionalScope() {
    return scope(optionalConfig());
  }

  private static <Solution_> SolverScope<Solution_> scope(SolverConfig config) {
    return ((DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver())
        .getSolverScope();
  }

  private static TestdataAllowsUnassignedValuesListSolution optionalProblem(int[][] assignments) {
    var solution = TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(4, 2);
    setLists(solution, assignments);
    return solution;
  }

  private static void setLists(
      TestdataAllowsUnassignedValuesListSolution solution, int[][] assignments) {
    for (int i = 0; i < assignments.length; i++) {
      solution
          .getEntityList()
          .get(i)
          .setValueList(
              new ArrayList<>(
                  Arrays.stream(assignments[i]).mapToObj(solution.getValueList()::get).toList()));
    }
  }

  private static void assertOptionalState(
      InnerScoreDirector<TestdataAllowsUnassignedValuesListSolution, SimpleScore> director,
      int[][] assignments) {
    var working = director.getWorkingSolution();
    int assignedCount = 0;
    for (int entityIndex = 0; entityIndex < assignments.length; entityIndex++) {
      var entity = working.getEntityList().get(entityIndex);
      var expected =
          Arrays.stream(assignments[entityIndex]).mapToObj(working.getValueList()::get).toList();
      assertThat(entity.getValueList()).containsExactlyElementsOf(expected);
      assignedCount += expected.size();
      for (int index = 0; index < expected.size(); index++) {
        var value = expected.get(index);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPrevious()).isSameAs(index == 0 ? null : expected.get(index - 1));
        assertThat(value.getNext())
            .isSameAs(index + 1 == expected.size() ? null : expected.get(index + 1));
      }
    }
    for (var value : working.getValueList()) {
      if (working.getEntityList().stream()
          .noneMatch(entity -> entity.getValueList().contains(value))) {
        assertThat(value.getEntity()).isNull();
        assertThat(value.getIndex()).isNull();
        assertThat(value.getPrevious()).isNull();
        assertThat(value.getNext()).isNull();
      }
    }
    var variable =
        (ListVariableDescriptor<TestdataAllowsUnassignedValuesListSolution>)
            director
                .getSolutionDescriptor()
                .findEntityDescriptorOrFail(TestdataAllowsUnassignedValuesListEntity.class)
                .getGenuineVariableDescriptor("valueList");
    assertThat(director.getListVariableState(variable).getUnassignedCount())
        .isEqualTo(4 - assignedCount);
    assertThat(director.getWorkingInitScore()).isZero();
    var score = director.calculateScore();
    assertThat(score).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-4 - assignedCount)));
    director.assertWorkingScoreFromScratch(score, "Solution synchronization");
  }

  private static SolverScope<TestdataPinnedUnassignedValuesListSolution> pinnedScope() {
    return scope(
        PlannerTestUtils.buildSolverConfig(
                TestdataPinnedUnassignedValuesListSolution.class,
                TestdataPinnedUnassignedValuesListEntity.class,
                TestdataPinnedUnassignedValuesListValue.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(
                        TestdataPinnedUnassignedValuesListConstraintProvider.class)));
  }

  private static TestdataPinnedUnassignedValuesListSolution pinnedProblem() {
    var values =
        List.of(
            new TestdataPinnedUnassignedValuesListValue("v0"),
            new TestdataPinnedUnassignedValuesListValue("v1"),
            new TestdataPinnedUnassignedValuesListValue("v2"));
    var first = new TestdataPinnedUnassignedValuesListEntity("e0", values.get(0));
    var last = new TestdataPinnedUnassignedValuesListEntity("e1", values.get(1));
    last.setPlanningPinToIndex(1);
    var solution = new TestdataPinnedUnassignedValuesListSolution();
    solution.setEntityList(List.of(first, last));
    solution.setValueList(values);
    return solution;
  }

  public static final class OptionalListScoreProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataAllowsUnassignedValuesListEntity.class)
            .penalize(SimpleScore.of(2), entity -> entity.getValueList().size())
            .asConstraint("Assigned values"),
        factory
            .forEachIncludingUnassigned(TestdataAllowsUnassignedValuesListValue.class)
            .filter(value -> value.getEntity() == null)
            .penalize(SimpleScore.ONE)
            .asConstraint("Unassigned values")
      };
    }
  }
}
