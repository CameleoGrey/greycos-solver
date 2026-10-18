package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.list.SubList;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListAssignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListUnassignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListAssignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListUnassignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListUnassignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SubListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SubListSwapMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SubListUnassignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt.SelectorBasedTwoOptListMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt.TwoOptListMove;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Range;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MultipleListMoveRebaseTest {

  static Stream<Arguments> moves() {
    return Stream.of("firstValueList", "secondValueList")
        .flatMap(
            name ->
                Stream.of(
                        "change",
                        "swap",
                        "assign",
                        "unassign",
                        "subChange",
                        "subSwap",
                        "subUnassign",
                        "twoOpt",
                        "selectorChange",
                        "selectorSwap",
                        "selectorAssign",
                        "selectorUnassign",
                        "selectorSubChange",
                        "selectorSubSwap",
                        "selectorSubUnassign",
                        "selectorTwoOpt",
                        "previewChange",
                        "previewSwap",
                        "previewAssign",
                        "previewUnassign",
                        "previewSubChange",
                        "previewSubSwap",
                        "previewSubUnassign",
                        "previewMassChange")
                    .map(kind -> Arguments.of(name, kind)));
  }

  @ParameterizedTest
  @MethodSource("moves")
  void rebaseUsesReceivingModelAndPreservesBothFieldsThroughUndo(String name, String kind) {
    var sourceSolution = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    var destinationSolution = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    try (var source = TestdataMultipleListSolution.buildScoreDirector();
        var destination = TestdataMultipleListSolution.buildScoreDirector()) {
      var sourceDescriptor =
          source
              .getSolutionDescriptor()
              .resolveListVariableDescriptor(TestdataMultipleListEntity.class, name);
      var destinationDescriptor =
          destination
              .getSolutionDescriptor()
              .resolveListVariableDescriptor(TestdataMultipleListEntity.class, name);
      if (kind.equals("assign") || kind.equals("selectorAssign") || kind.equals("previewAssign")) {
        sourceDescriptor.getValue(sourceSolution.getEntityList().getFirst()).remove(1);
        destinationDescriptor.getValue(destinationSolution.getEntityList().getFirst()).remove(1);
      }
      source.setWorkingSolution(sourceSolution);
      destination.setWorkingSolution(destinationSolution);
      var baseline = fingerprint(destinationSolution);
      var move = move(kind, sourceDescriptor, sourceSolution);
      var rebased = move.rebase(destination.getMoveDirector());
      assertThat(rebased.getClass()).isEqualTo(move.getClass());
      if (rebased instanceof AbstractMove<TestdataMultipleListSolution> preview) {
        assertThat(preview.variableMetaModels())
            .singleElement()
            .isSameAs(destinationDescriptor.getVariableMetaModel());
      }
      var expected = new AtomicReference<String>();
      var expectedScore =
          source.executeTemporaryMove(
              move, ignored -> expected.set(fingerprint(sourceSolution)), true);
      var actualScore =
          destination.executeTemporaryMove(
              rebased,
              ignored -> {
                assertThat(fingerprint(destinationSolution)).isEqualTo(expected.get());
                assertWorkingObjects(destinationSolution);
              },
              true);
      assertThat(actualScore).isEqualTo(expectedScore);
      assertThat(fingerprint(destinationSolution)).isEqualTo(baseline);
      assertWorkingObjects(destinationSolution);
    }
  }

  private static Move<TestdataMultipleListSolution> move(
      String kind,
      ListVariableDescriptor<TestdataMultipleListSolution> descriptor,
      TestdataMultipleListSolution solution) {
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    Object value =
        descriptor.getVariableName().equals("firstValueList")
            ? solution.getFirstValueList().get(2)
            : solution.getSecondValueList().get(2);
    PlanningListVariableMetaModel<TestdataMultipleListSolution, TestdataMultipleListEntity, Object>
        metaModel = descriptor.getVariableMetaModel();
    return switch (kind) {
      case "change" -> new ListChangeMove<>(descriptor, left, 0, right, 1);
      case "swap" -> new ListSwapMove<>(descriptor, left, 0, right, 1);
      case "assign" -> new ListAssignMove<>(descriptor, value, right, 1);
      case "unassign" -> new ListUnassignMove<>(descriptor, left, 0);
      case "subChange" ->
          new SubListChangeMove<>(descriptor, new SubList(left, 0, 2), right, 1, true);
      case "subSwap" ->
          new SubListSwapMove<>(
              descriptor, new SubList(left, 0, 2), new SubList(right, 0, 1), true);
      case "subUnassign" -> new SubListUnassignMove<>(descriptor, new SubList(left, 0, 2));
      case "twoOpt" -> new TwoOptListMove<>(descriptor, left, right, 1, 0);
      case "selectorChange" -> new SelectorBasedListChangeMove<>(descriptor, left, 0, right, 1);
      case "selectorSwap" -> new SelectorBasedListSwapMove<>(descriptor, left, 0, right, 1);
      case "selectorAssign" -> new SelectorBasedListAssignMove<>(descriptor, value, right, 1);
      case "selectorUnassign" -> new SelectorBasedListUnassignMove<>(descriptor, left, 0);
      case "selectorSubChange" ->
          new SelectorBasedSubListChangeMove<>(descriptor, new SubList(left, 0, 2), right, 1, true);
      case "selectorSubSwap" ->
          new SelectorBasedSubListSwapMove<>(
              descriptor, new SubList(left, 0, 2), new SubList(right, 0, 1), true);
      case "selectorSubUnassign" ->
          new SelectorBasedSubListUnassignMove<>(descriptor, new SubList(left, 0, 2));
      case "selectorTwoOpt" -> new SelectorBasedTwoOptListMove<>(descriptor, left, right, 1, 0);
      case "previewChange" ->
          new greycos.solver.core.impl.move.builtin.ListChangeMove<>(metaModel, left, 0, right, 1);
      case "previewSwap" ->
          new greycos.solver.core.impl.move.builtin.ListSwapMove<>(metaModel, left, 0, right, 1);
      case "previewAssign" ->
          new greycos.solver.core.impl.move.builtin.ListAssignMove<>(metaModel, value, right, 1);
      case "previewUnassign" ->
          new greycos.solver.core.impl.move.builtin.ListUnassignMove<>(metaModel, left, 0);
      case "previewSubChange" ->
          new greycos.solver.core.impl.move.builtin.SubListChangeMove<>(
              metaModel, new Range<>(left, 0, 2), ElementPosition.of(right, 1), true);
      case "previewSubSwap" ->
          new greycos.solver.core.impl.move.builtin.SubListSwapMove<>(
              metaModel, new Range<>(left, 0, 2), new Range<>(right, 0, 1), true);
      case "previewSubUnassign" ->
          new greycos.solver.core.impl.move.builtin.SubListUnassignMove<>(
              metaModel, new Range<>(left, 0, 2));
      case "previewMassChange" ->
          new greycos.solver.core.impl.move.builtin.MassListChangeMove<>(
              metaModel, Sample.of(List.of(value)), ElementPosition.of(right, 1));
      default -> throw new IllegalArgumentException(kind);
    };
  }

  private static String fingerprint(TestdataMultipleListSolution solution) {
    return solution.getEntityList().stream()
            .map(owner -> owner.getFirstValueList() + "/" + owner.getSecondValueList())
            .toList()
        + "/"
        + solution.getFirstValueList().stream()
            .map(
                value ->
                    value.getEntity()
                        + ":"
                        + value.getIndex()
                        + ":"
                        + value.getPrevious()
                        + ":"
                        + value.getNext())
            .toList()
        + "/"
        + solution.getSecondValueList().stream()
            .map(
                value ->
                    value.getEntity()
                        + ":"
                        + value.getIndex()
                        + ":"
                        + value.getPrevious()
                        + ":"
                        + value.getNext())
            .toList();
  }

  private static void assertWorkingObjects(TestdataMultipleListSolution solution) {
    for (var owner : solution.getEntityList()) {
      owner
          .getFirstValueList()
          .forEach(
              value -> {
                assertThat(solution.getFirstValueList()).anyMatch(candidate -> candidate == value);
                assertThat(value.getEntity()).isSameAs(owner);
              });
      owner
          .getSecondValueList()
          .forEach(
              value -> {
                assertThat(solution.getSecondValueList()).anyMatch(candidate -> candidate == value);
                assertThat(value.getEntity()).isSameAs(owner);
              });
    }
  }
}
