package greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class UnassignMoveProviderTest {

  @Test
  void pinnedEntitySkipped() {
    var solutionMetaModel = TestdataPinnedAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataPinnedAllowsUnassignedEntity.class)
            .basicVariable("value", TestdataValue.class);

    var solution = TestdataPinnedAllowsUnassignedSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    var secondEntity = solution.getEntityList().get(1);
    firstEntity.setPinned(true);

    // firstEntity is pinned -> no unassign moves. Only secondEntity can be unassigned.
    var context =
        NeighborhoodTester.build(new UnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);
    context.producesAllOf(Moves.change(variableMetaModel, secondEntity, null));
    context.producesNoneOf(Moves.change(variableMetaModel, firstEntity, null));
  }

  @Test
  void unassignedEntitySkipped() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntity.class)
            .basicVariable("value", TestdataValue.class);

    // generateSolution(2, 2): entity0=null, entity1=value1
    var solution = TestdataAllowsUnassignedSolution.generateSolution(2, 2);
    var entity0 = solution.getEntityList().get(0); // null value
    var entity1 = solution.getEntityList().get(1); // non-null value

    var context =
        NeighborhoodTester.build(new UnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);
    context.producesAllOf(Moves.change(variableMetaModel, entity1, null));
    context.producesNoneOf(Moves.change(variableMetaModel, entity0, null)); // Already null.
  }

  @Test
  void allEntitiesAssigned() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntity.class)
            .basicVariable("value", TestdataValue.class);

    var solution = TestdataAllowsUnassignedSolution.generateSolution(2, 2);
    var entity0 = solution.getEntityList().get(0);
    var entity1 = solution.getEntityList().get(1);
    entity0.setValue(solution.getValueList().getFirst());

    NeighborhoodTester.build(new UnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
        .using(solution)
        .producesAllOf(
            Moves.change(variableMetaModel, entity0, null),
            Moves.change(variableMetaModel, entity1, null));
  }

  @Test
  void noAssignedEntities() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntity.class)
            .basicVariable("value", TestdataValue.class);

    var solution = TestdataAllowsUnassignedSolution.generateSolution(2, 2);
    var entity0 = solution.getEntityList().get(0);
    var entity1 = solution.getEntityList().get(1);
    solution.getEntityList().forEach(e -> e.setValue(null));

    NeighborhoodTester.build(new UnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
        .using(solution)
        .producesNoneOf(
            Moves.change(variableMetaModel, entity0, null),
            Moves.change(variableMetaModel, entity1, null));
  }

  @Test
  void failsOnNonNullableVariable() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UnassignMoveProvider<>(variableMetaModel));
  }
}
