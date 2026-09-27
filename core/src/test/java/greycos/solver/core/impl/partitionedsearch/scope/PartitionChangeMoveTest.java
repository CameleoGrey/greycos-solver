package greycos.solver.core.impl.partitionedsearch.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
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
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementEntity;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementSolution;
import greycos.solver.core.testcotwin.shadow.list_element.TestdataMixedListElementValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class PartitionChangeMoveTest {

  @Test
  void basicAssignmentsAreRecordedAndUndone() {
    var scope =
        PartitionChangeMoveTest.<TestdataSolution>scope(
            PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class));
    scope.setInitialSolution(TestdataSolution.generateUninitializedSolution(2, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().setValue(source.getValueList().getFirst());
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score ->
                  assertThat(director.getWorkingSolution().getEntityList().getFirst().getValue())
                      .isSameAs(director.getWorkingSolution().getValueList().getFirst()));
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(director.getWorkingSolution().getEntityList().getFirst().getValue()).isNull();
    }
  }

  @Test
  void requiredListSnapshotRebasesElementsAndRestoresAssignmentCounts() {
    var scope = PartitionChangeMoveTest.<TestdataListSolution>scope(listConfig());
    scope.setInitialSolution(TestdataListSolution.generateUninitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.getEntityList().getFirst().getValueList().addAll(source.getValueList());
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      source.getEntityList().getFirst().getValueList().clear();
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                var working = director.getWorkingSolution();
                assertThat(working.getEntityList().getFirst().getValueList())
                    .containsExactlyElementsOf(working.getValueList());
                assertThat(director.getWorkingInitScore()).isZero();
                for (int i = 0; i < 4; i++) {
                  assertThat(working.getValueList().get(i).getEntity())
                      .isSameAs(working.getEntityList().getFirst());
                  assertThat(working.getValueList().get(i).getIndex()).isEqualTo(i);
                }
                director.assertWorkingScoreFromScratch(score, move);
              });
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(director.getWorkingInitScore()).isEqualTo(-4);
      assertThat(director.getWorkingSolution().getValueList())
          .allSatisfy(
              value -> {
                assertThat(value.getEntity()).isNull();
                assertThat(value.getIndex()).isNull();
              });
    }
  }

  @Test
  void optionalPartitionAssignmentAndUnassignmentLeaveOtherPartitionUntouched() {
    var scope =
        PartitionChangeMoveTest.<TestdataAllowsUnassignedValuesListSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                TestdataAllowsUnassignedValuesListSolution.class,
                TestdataAllowsUnassignedValuesListEntity.class,
                TestdataAllowsUnassignedValuesListValue.class));
    var initial = TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(4, 2);
    initial.getEntityList().get(0).getValueList().add(initial.getValueList().get(0));
    initial.getEntityList().get(1).getValueList().add(initial.getValueList().get(1));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var working = director.getWorkingSolution();
      var first = optionalPart(working, 0);
      var second = optionalPart(working, 1);
      var ownership = PartitionOwnership.validate(director, List.of(first, second));
      var source = director.cloneSolution(first);
      source
          .getEntityList()
          .getFirst()
          .setValueList(new ArrayList<>(List.of(source.getValueList().getLast())));
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      ownership.validateMove(move, director);
      director
          .getMoveDirector()
          .executeTemporaryProducingUndoMove(
              move,
              score -> {
                assertThat(working.getEntityList().get(0).getValueList())
                    .containsExactly(working.getValueList().get(2));
                assertThat(working.getEntityList().get(1).getValueList())
                    .containsExactly(working.getValueList().get(1));
                assertThat(working.getValueList().get(0).getEntity()).isNull();
                assertThat(working.getValueList().get(0).getIndex()).isNull();
                assertThat(working.getValueList().get(0).getPrevious()).isNull();
                assertThat(working.getValueList().get(0).getNext()).isNull();
                assertThat(working.getValueList().get(2).getEntity())
                    .isSameAs(working.getEntityList().get(0));
                assertThat(working.getValueList().get(2).getIndex()).isZero();
                assertThat(
                        director
                            .getListVariableState(
                                director.getSolutionDescriptor().getListVariableDescriptor())
                            .getUnassignedCount())
                    .isEqualTo(2);
              });
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(working.getEntityList().get(0).getValueList())
          .containsExactly(working.getValueList().get(0));
    }
  }

  @Test
  void mixedAssignmentsAndDeclarativeShadowsApplyAndUndo() {
    var scope =
        PartitionChangeMoveTest.<TestdataMixedListElementSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                TestdataMixedListElementSolution.class,
                TestdataMixedListElementEntity.class,
                TestdataMixedListElementValue.class));
    var firstValue = new TestdataMixedListElementValue("v0");
    firstValue.setDuration(1);
    var lastValue = new TestdataMixedListElementValue("v1");
    lastValue.setDuration(2);
    var first = new TestdataMixedListElementEntity("e0");
    first.getValues().add(firstValue);
    var last = new TestdataMixedListElementEntity("e1");
    last.getValues().add(lastValue);
    var initial = new TestdataMixedListElementSolution();
    initial.setEntities(List.of(first, last));
    initial.setValues(List.of(firstValue, lastValue));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var ownership = PartitionOwnership.validate(director, List.of(director.getWorkingSolution()));
      var source = director.cloneWorkingSolution();
      source.getValues().getFirst().setDuration(5);
      source.getEntities().getFirst().getValues().clear();
      source.getEntities().getLast().getValues().add(source.getValues().getFirst());
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      ownership.validateMove(move, director);
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
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(director.getWorkingSolution().getValues().getFirst().getDuration()).isEqualTo(1);
      assertThat(director.getWorkingSolution().getEntities().getFirst().getTotalDuration())
          .isEqualTo(2);
    }
  }

  @Test
  void pinnedPrefixMismatchFailsBeforeEarlierListChanges() {
    var scope = PartitionChangeMoveTest.<TestdataPinnedWithIndexListSolution>scope(pinnedConfig());
    var initial = TestdataPinnedWithIndexListSolution.generateInitializedSolution(4, 2);
    initial.getEntityList().getLast().setPinIndex(1);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var source = director.cloneWorkingSolution();
      java.util.Collections.reverse(source.getEntityList().getFirst().getValueList());
      java.util.Collections.reverse(source.getEntityList().getLast().getValueList());
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      assertThatThrownBy(() -> director.executeMove(move))
          .hasMessageContaining("Pinned list segment differs");
      assertThat(director.getWorkingSolution().getEntityList().getFirst().getValueList())
          .containsExactly(
              director.getWorkingSolution().getValueList().get(0),
              director.getWorkingSolution().getValueList().get(2));
      assertThat(director.calculateScore()).isEqualTo(before);
    }
  }

  static SolverConfig listConfig() {
    return PlannerTestUtils.buildSolverConfig(
            TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
        .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class);
  }

  static SolverConfig pinnedConfig() {
    return PlannerTestUtils.buildSolverConfig(
            TestdataPinnedWithIndexListSolution.class,
            TestdataPinnedWithIndexListEntity.class,
            TestdataPinnedWithIndexListValue.class)
        .withEasyScoreCalculatorClass(TestdataPinnedWithIndexListEasyScoreCalculator.class);
  }

  static <Solution_> SolverScope<Solution_> scope(SolverConfig config) {
    return ((DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver())
        .getSolverScope();
  }

  private static TestdataAllowsUnassignedValuesListSolution optionalPart(
      TestdataAllowsUnassignedValuesListSolution solution, int index) {
    var part = new TestdataAllowsUnassignedValuesListSolution();
    part.setEntityList(List.of(solution.getEntityList().get(index)));
    part.setValueList(
        List.of(solution.getValueList().get(index), solution.getValueList().get(index + 2)));
    return part;
  }
}
