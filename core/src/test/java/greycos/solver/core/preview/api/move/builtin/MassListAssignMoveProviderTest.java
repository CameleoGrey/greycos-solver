package greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import greycos.solver.core.impl.move.builtin.MassListChangeMove;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Samplers;
import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.pinned.unassignedvar.TestdataPinnedAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.TestdataListUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.unassignedvar.TestdataListUnassignedEntityProvidingSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class MassListAssignMoveProviderTest {

  @Test
  void constructorRejectsNonUnassignedVariable() {
    var solutionMetaModel = TestdataListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataListEntity.class)
            .listVariable("valueList", TestdataListValue.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MassListAssignMoveProvider<>(variableMetaModel, Samplers.exactly(2)));
  }

  @Test
  void sampleMembersAreAlwaysASubsetOfTheUnassignedValues() {
    var solutionMetaModel = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);

    var assigned = new TestdataAllowsUnassignedValuesListValue("assigned");
    var unassigned1 = new TestdataAllowsUnassignedValuesListValue("unassigned1");
    var unassigned2 = new TestdataAllowsUnassignedValuesListValue("unassigned2");
    var entity = new TestdataAllowsUnassignedValuesListEntity("A", assigned);
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(assigned, unassigned1, unassigned2));

    var context =
        NeighborhoodTester.build(
                new MassListAssignMoveProvider<>(variableMetaModel, Samplers.exactly(2)),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassListChangeMove<
                            TestdataAllowsUnassignedValuesListSolution,
                            TestdataAllowsUnassignedValuesListEntity,
                            TestdataAllowsUnassignedValuesListValue>)
                        move)
            .limit(50)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(
            move -> {
              assertThat(move.getDestination()).isNotNull();
              assertThat(move.getSample())
                  .isSubsetOf(unassigned1, unassigned2)
                  .doesNotContain(assigned);
            });
  }

  @Test
  void destinationRespectsEveryMembersValueRange() {
    var solutionMetaModel = TestdataListUnassignedEntityProvidingSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataListUnassignedEntityProvidingEntity.class)
            .listVariable("valueList", TestdataValue.class);

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var entityA = new TestdataListUnassignedEntityProvidingEntity("A", List.of(v1, v2));
    var entityB = new TestdataListUnassignedEntityProvidingEntity("B", List.of(v1));
    var solution = new TestdataListUnassignedEntityProvidingSolution();
    solution.setEntityList(List.of(entityA, entityB));

    var context =
        NeighborhoodTester.build(
                new MassListAssignMoveProvider<>(variableMetaModel, Samplers.all()),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassListChangeMove<
                            TestdataListUnassignedEntityProvidingSolution,
                            TestdataListUnassignedEntityProvidingEntity,
                            TestdataValue>)
                        move)
            .limit(50)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(
            move -> {
              var destination = move.getDestination();
              assertThat(destination).isNotNull();
              TestdataListUnassignedEntityProvidingEntity destinationEntity = destination.entity();
              assertThat(destinationEntity.getValueRange()).containsAll(move.getSample());
            });
  }

  @Test
  void destinationNeverFallsInThePinnedPrefix() {
    var solutionMetaModel =
        TestdataPinnedUnassignedValuesListSolution.buildSolutionDescriptor().getMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataPinnedUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataPinnedUnassignedValuesListValue.class);

    var pinned1 = new TestdataPinnedUnassignedValuesListValue("pinned1");
    var pinned2 = new TestdataPinnedUnassignedValuesListValue("pinned2");
    var free1 = new TestdataPinnedUnassignedValuesListValue("free1");
    var unassigned1 = new TestdataPinnedUnassignedValuesListValue("unassigned1");
    var unassigned2 = new TestdataPinnedUnassignedValuesListValue("unassigned2");
    var entity = new TestdataPinnedUnassignedValuesListEntity("A", pinned1, pinned2, free1);
    entity.setPlanningPinToIndex(2);
    var solution = new TestdataPinnedUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(pinned1, pinned2, free1, unassigned1, unassigned2));

    var context =
        NeighborhoodTester.build(
                new MassListAssignMoveProvider<>(variableMetaModel, Samplers.exactly(2)),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassListChangeMove<
                            TestdataPinnedUnassignedValuesListSolution,
                            TestdataPinnedUnassignedValuesListEntity,
                            TestdataPinnedUnassignedValuesListValue>)
                        move)
            .limit(100)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(
            move -> {
              var destination = move.getDestination();
              assertThat(destination).isNotNull();
              assertThat(destination.index()).isGreaterThanOrEqualTo(2);
            });
  }

  @Test
  void fullyPinnedEntityIsNeverAssignedInto() {
    var solutionMetaModel = TestdataPinnedAllowsUnassignedValuesListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataPinnedAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataPinnedAllowsUnassignedValuesListValue.class);

    var pinnedValues = List.of(new TestdataPinnedAllowsUnassignedValuesListValue("p0"));
    var pinnedEntity = new TestdataPinnedAllowsUnassignedValuesListEntity("pinned", pinnedValues);
    pinnedEntity.setPinned(true);
    var freeEntity = new TestdataPinnedAllowsUnassignedValuesListEntity("free");
    var unassigned1 = new TestdataPinnedAllowsUnassignedValuesListValue("u1");
    var unassigned2 = new TestdataPinnedAllowsUnassignedValuesListValue("u2");

    var solution = new TestdataPinnedAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(pinnedEntity, freeEntity));
    solution.setValueList(List.of(pinnedValues.getFirst(), unassigned1, unassigned2));

    var context =
        NeighborhoodTester.build(
                new MassListAssignMoveProvider<>(variableMetaModel, Samplers.exactly(2)),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassListChangeMove<
                            TestdataPinnedAllowsUnassignedValuesListSolution,
                            TestdataPinnedAllowsUnassignedValuesListEntity,
                            TestdataPinnedAllowsUnassignedValuesListValue>)
                        move)
            .limit(100)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(
            move -> {
              var destination = move.getDestination();
              assertThat(destination).isNotNull();
              assertThat(destination.<TestdataPinnedAllowsUnassignedValuesListEntity>entity())
                  .isNotEqualTo(pinnedEntity);
            });
  }
}
