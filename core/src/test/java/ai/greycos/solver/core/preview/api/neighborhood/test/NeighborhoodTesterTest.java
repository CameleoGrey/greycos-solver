package ai.greycos.solver.core.preview.api.neighborhood.test;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.preview.api.move.builtin.ChangeMove;
import ai.greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.assertj.core.error.AssertionErrorCreator;
import org.junit.jupiter.api.Test;

// Much of the test coverage for NeighborhoodTester is in tests for the specific MoveProviders.
// There is no better coverage than real-world use cases.
class NeighborhoodTesterTest {

  @Test
  void temporaryMoveExecutionDoesNotAffectMoveIterator() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();

    var solution = TestdataSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    firstEntity.setValue(null);
    var secondEntity = solution.getEntityList().get(1);
    secondEntity.setValue(null);
    var firstValue = solution.getValueList().get(0);
    var secondValue = solution.getValueList().get(1);

    var evaluatedNeighborhood =
        NeighborhoodTester.build(new ChangeMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);
    var moveList =
        evaluatedNeighborhood.getMovesAsList(
            move -> (ChangeMove<TestdataSolution, TestdataEntity, TestdataValue>) move);

    assertThat(moveList).hasSize(4);

    var firstMove =
        moveList.stream()
            .filter(
                move ->
                    move.getPlanningEntities().contains(firstEntity)
                        && move.getPlanningValues().contains(firstValue))
            .findFirst()
            .orElseThrow(
                () -> new AssertionErrorCreator().assertionError("Move not found in move list."));
    evaluatedNeighborhood
        .getMoveTestContext()
        .executeTemporarily(
            firstMove, solutionView -> assertThat(firstEntity.getValue()).isEqualTo(firstValue));
    assertThat(firstEntity.getValue()).isNull();

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(firstEntity)
                    && move.getPlanningValues().contains(secondValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(secondEntity)
                    && move.getPlanningValues().contains(firstValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(secondEntity)
                    && move.getPlanningValues().contains(secondValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));
  }

  @Test
  void newIteratorAfterMoveExecution() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();

    var solution = TestdataSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    firstEntity.setValue(null);
    var secondEntity = solution.getEntityList().get(1);
    secondEntity.setValue(null);
    var firstValue = solution.getValueList().get(0);
    var secondValue = solution.getValueList().get(1);

    var evaluatedNeighborhood =
        NeighborhoodTester.build(new ChangeMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);
    var moveList =
        evaluatedNeighborhood.getMovesAsList(
            move -> (ChangeMove<TestdataSolution, TestdataEntity, TestdataValue>) move);
    assertThat(moveList).hasSize(4);

    var firstMove =
        moveList.stream()
            .filter(
                move ->
                    move.getPlanningEntities().contains(firstEntity)
                        && move.getPlanningValues().contains(firstValue))
            .findFirst()
            .orElseThrow(
                () -> new AssertionErrorCreator().assertionError("Move not found in move list."));
    evaluatedNeighborhood.getMoveTestContext().execute(firstMove);
    assertThat(firstEntity.getValue()).isEqualTo(firstValue);

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(firstEntity)
                    && move.getPlanningValues().contains(secondValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(secondEntity)
                    && move.getPlanningValues().contains(firstValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));

    moveList.stream()
        .filter(
            move ->
                move.getPlanningEntities().contains(secondEntity)
                    && move.getPlanningValues().contains(secondValue))
        .findFirst()
        .orElseThrow(
            () -> new AssertionErrorCreator().assertionError("Move not found in move list."));
  }
}
