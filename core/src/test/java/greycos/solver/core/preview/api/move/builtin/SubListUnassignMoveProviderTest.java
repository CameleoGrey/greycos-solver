package greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import greycos.solver.core.impl.move.builtin.SubListUnassignMove;
import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListValue;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class SubListUnassignMoveProviderTest {

  @Test
  void unassignsSpansOfBoundedLength() {
    var solutionMetaModel = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);

    var values = new TestdataAllowsUnassignedValuesListValue[10];
    for (var i = 0; i < 10; i++) {
      values[i] = new TestdataAllowsUnassignedValuesListValue("v" + i);
    }
    var entity = new TestdataAllowsUnassignedValuesListEntity("A", values);
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(values));

    var context =
        NeighborhoodTester.build(
                new SubListUnassignMoveProvider<>(variableMetaModel, 2, 5), solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (SubListUnassignMove<
                            TestdataAllowsUnassignedValuesListSolution,
                            TestdataAllowsUnassignedValuesListEntity,
                            TestdataAllowsUnassignedValuesListValue>)
                        move)
            .limit(300)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(move -> assertThat(move.getRange().length()).isBetween(2, 5));
  }

  @Test
  void differentDrawsProduceDifferentSpans() {
    var solutionMetaModel = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);

    var values = new TestdataAllowsUnassignedValuesListValue[10];
    for (var i = 0; i < 10; i++) {
      values[i] = new TestdataAllowsUnassignedValuesListValue("v" + i);
    }
    var entity = new TestdataAllowsUnassignedValuesListEntity("A", values);
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(values));

    var context =
        NeighborhoodTester.build(
                new SubListUnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);

    var distinctSpans =
        context
            .getMovesAsStream(
                move ->
                    (SubListUnassignMove<
                            TestdataAllowsUnassignedValuesListSolution,
                            TestdataAllowsUnassignedValuesListEntity,
                            TestdataAllowsUnassignedValuesListValue>)
                        move)
            .limit(300)
            .map(move -> move.getRange().fromIndex() + ".." + move.getRange().toIndex())
            .collect(Collectors.toCollection(HashSet::new));
    assertThat(distinctSpans).hasSizeGreaterThan(1);
  }

  @Test
  void drawnSpanNeverTouchesPinnedPrefix() {
    var solutionMetaModel =
        TestdataPinnedUnassignedValuesListSolution.buildSolutionDescriptor().getMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataPinnedUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataPinnedUnassignedValuesListValue.class);

    var values = new TestdataPinnedUnassignedValuesListValue[8];
    for (var i = 0; i < 8; i++) {
      values[i] = new TestdataPinnedUnassignedValuesListValue("v" + i);
    }
    var entity = new TestdataPinnedUnassignedValuesListEntity("A", values);
    entity.setPlanningPinToIndex(3);
    var solution = new TestdataPinnedUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(values));

    var context =
        NeighborhoodTester.build(
                new SubListUnassignMoveProvider<>(variableMetaModel), solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (SubListUnassignMove<
                            TestdataPinnedUnassignedValuesListSolution,
                            TestdataPinnedUnassignedValuesListEntity,
                            TestdataPinnedUnassignedValuesListValue>)
                        move)
            .limit(300)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(move -> assertThat(move.getRange().fromIndex()).isGreaterThanOrEqualTo(3));
  }

  @Test
  void constructorRequiresAllowsUnassignedValues() {
    var solutionMetaModel = TestdataListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataListEntity.class)
            .listVariable("valueList", TestdataListValue.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SubListUnassignMoveProvider<>(variableMetaModel));
  }

  @Test
  void constructorRejectsInvalidSizes() {
    var solutionMetaModel = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedValuesListEntity.class)
            .listVariable("valueList", TestdataAllowsUnassignedValuesListValue.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SubListUnassignMoveProvider<>(variableMetaModel, 0, 5));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SubListUnassignMoveProvider<>(variableMetaModel, 5, 2));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SubListUnassignMoveProvider<>(variableMetaModel, 1, 5));
  }
}
