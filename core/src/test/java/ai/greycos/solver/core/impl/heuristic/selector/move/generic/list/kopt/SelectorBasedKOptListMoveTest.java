package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import static ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt.KOptUtils.getMultiEntityBetweenPredicate;
import static ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt.KOptUtils.getMultiEntitySuccessorFunction;
import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import ai.greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateDemand;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SelectorBasedKOptListMoveTest {

  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();
  private final InnerScoreDirector<TestdataListSolution, ?> scoreDirector =
      PlannerTestUtils.mockScoreDirector(
          variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

  private final TestdataListValue v1 = new TestdataListValue("1");
  private final TestdataListValue v2 = new TestdataListValue("2");
  private final TestdataListValue v3 = new TestdataListValue("3");
  private final TestdataListValue v4 = new TestdataListValue("4");
  private final TestdataListValue v5 = new TestdataListValue("5");
  private final TestdataListValue v6 = new TestdataListValue("6");

  private final TestdataListValue destinationV1 = new TestdataListValue("1");
  private final TestdataListValue destinationV2 = new TestdataListValue("2");
  private final TestdataListValue destinationV3 = new TestdataListValue("3");
  private final TestdataListValue destinationV4 = new TestdataListValue("4");
  private final TestdataListValue destinationV5 = new TestdataListValue("5");
  private final TestdataListValue destinationV6 = new TestdataListValue("6");

  @Test
  void test3Opt() {
    var e1 = TestdataListEntity.createWithValues("e1", v1, v2, v3, v4, v5, v6);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(e1));
    solution.setValueList(List.of(v1, v2, v3, v4, v5, v6));
    scoreDirector.setWorkingSolution(solution);

    var kOptListMove =
        fromRemovedAndAddedEdges(
            scoreDirector,
            variableDescriptor,
            List.of(v6, v1, v2, v3, v4, v5),
            List.of(v1, v3, v2, v5, v4, v6));

    assertThat(kOptListMove).isInstanceOf(SelectorBasedKOptListMove.class);
    assertThat(kOptListMove.isMoveDoable(scoreDirector)).isTrue();
    kOptListMove.doMoveOnly(scoreDirector);
    assertThat(e1.getValueList()).containsExactly(v1, v2, v5, v6, v4, v3);
    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 0, 6);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 0, 6);
  }

  @Test
  void test3OptRebase() {
    var e1 = TestdataListEntity.createWithValues("e1", v1, v2, v3, v4, v5, v6);
    var destinationE1 =
        TestdataListEntity.createWithValues(
            "e1",
            destinationV1,
            destinationV2,
            destinationV3,
            destinationV4,
            destinationV5,
            destinationV6);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(e1));
    solution.setValueList(List.of(v1, v2, v3, v4, v5, v6));
    scoreDirector.setWorkingSolution(solution);

    var kOptListMove =
        fromRemovedAndAddedEdges(
            scoreDirector,
            variableDescriptor,
            List.of(v6, v1, v2, v3, v4, v5),
            List.of(v1, v3, v2, v5, v4, v6));

    InnerScoreDirector<TestdataListSolution, ?> destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {v3, destinationV3},
              {v4, destinationV4},
              {v5, destinationV5},
              {v6, destinationV6},
              {e1, destinationE1},
            });
    var supplyManager = Mockito.mock(SupplyManager.class);
    var inverseVariableSupply = Mockito.mock(ListVariableStateSupply.class);

    when(destinationScoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(Mockito.any(ListVariableStateDemand.class)))
        .thenReturn(inverseVariableSupply);
    when(inverseVariableSupply.getInverseSingleton(destinationE1.getValueList().get(0)))
        .thenReturn(destinationE1);

    var rebasedMove = kOptListMove.rebase(destinationScoreDirector);

    assertThat(rebasedMove).isInstanceOf(SelectorBasedKOptListMove.class);
    assertThat(rebasedMove.isMoveDoable(destinationScoreDirector)).isTrue();
    rebasedMove.doMoveOnly(destinationScoreDirector);
    assertThat(destinationE1.getValueList())
        .containsExactly(
            destinationV1,
            destinationV2,
            destinationV5,
            destinationV6,
            destinationV4,
            destinationV3);
    verify(destinationScoreDirector)
        .beforeListVariableChanged(variableDescriptor, destinationE1, 0, 6);
    verify(destinationScoreDirector)
        .afterListVariableChanged(variableDescriptor, destinationE1, 0, 6);
  }

  @Test
  void testEnableNearbyMixedModel() {
    var moveSelectorConfig = new KOptListMoveSelectorConfig();
    assertThat(moveSelectorConfig.canEnableNearbyInMixedModels()).isTrue();
  }

  private static <Solution_, Value_> SelectorBasedKOptListMove<Solution_> fromRemovedAndAddedEdges(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      List<Value_> removedEdgeList,
      List<Value_> addedEdgeList) {
    if (addedEdgeList.size() != removedEdgeList.size()) {
      throw new IllegalArgumentException(
          "addedEdgeList ("
              + addedEdgeList
              + ") and removedEdgeList ("
              + removedEdgeList
              + ") have the same size");
    }

    if ((addedEdgeList.size() % 2) != 0) {
      throw new IllegalArgumentException(
          "addedEdgeList and removedEdgeList are invalid: there is an odd number of endpoints.");
    }

    if (!addedEdgeList.containsAll(removedEdgeList)) {
      throw new IllegalArgumentException(
          "addedEdgeList ("
              + addedEdgeList
              + ") is invalid; it contains endpoints that are not included in the removedEdgeList ("
              + removedEdgeList
              + ").");
    }

    var pickedValues = removedEdgeList.toArray(Object[]::new);

    var listVariableDataSupply =
        scoreDirector.getSupplyManager().demand(listVariableDescriptor.getStateDemand());
    Function<Value_, Value_> successorFunction =
        getSuccessorFunction(listVariableDescriptor, listVariableDataSupply);

    for (var i = 0; i < removedEdgeList.size(); i += 2) {
      if (successorFunction.apply(removedEdgeList.get(i)) != removedEdgeList.get(i + 1)
          && successorFunction.apply(removedEdgeList.get(i + 1)) != removedEdgeList.get(i)) {
        throw new IllegalArgumentException(
            "removedEdgeList ("
                + removedEdgeList
                + ") contains an invalid edge (("
                + removedEdgeList.get(i)
                + ", "
                + removedEdgeList.get(i + 1)
                + ")).");
      }
    }

    var tourArray = new Object[removedEdgeList.size() + 1];
    var incl = new int[removedEdgeList.size() + 1];
    for (var i = 0; i < removedEdgeList.size(); i += 2) {
      tourArray[i + 1] = removedEdgeList.get(i);
      tourArray[i + 2] = removedEdgeList.get(i + 1);
      var addedEdgeIndex = identityIndexOf(addedEdgeList, removedEdgeList.get(i));

      if (addedEdgeIndex % 2 == 0) {
        incl[i + 1] = identityIndexOf(removedEdgeList, addedEdgeList.get(addedEdgeIndex + 1)) + 1;
      } else {
        incl[i + 1] = identityIndexOf(removedEdgeList, addedEdgeList.get(addedEdgeIndex - 1)) + 1;
      }

      addedEdgeIndex = identityIndexOf(addedEdgeList, removedEdgeList.get(i + 1));
      if (addedEdgeIndex % 2 == 0) {
        incl[i + 2] = identityIndexOf(removedEdgeList, addedEdgeList.get(addedEdgeIndex + 1)) + 1;
      } else {
        incl[i + 2] = identityIndexOf(removedEdgeList, addedEdgeList.get(addedEdgeIndex - 1)) + 1;
      }
    }

    var descriptor =
        new KOptDescriptor<>(
            tourArray,
            incl,
            getMultiEntitySuccessorFunction(pickedValues, listVariableDataSupply),
            getMultiEntityBetweenPredicate(pickedValues, listVariableDataSupply));
    return descriptor.getKOptListMove(listVariableDataSupply);
  }

  @SuppressWarnings("unchecked")
  private static <Node_> Function<Node_, Node_> getSuccessorFunction(
      ListVariableDescriptor<?> listVariableDescriptor,
      ListVariableStateSupply<?, Object, Object> listVariableStateSupply) {
    return node -> {
      var entity = listVariableStateSupply.getInverseSingleton(node);
      var valueList = (List<Node_>) listVariableDescriptor.getValue(entity);
      var index = listVariableStateSupply.getIndex(node);
      if (index == valueList.size() - 1) {
        var firstUnpinnedIndex = listVariableDescriptor.getFirstUnpinnedIndex(entity);
        return valueList.get(firstUnpinnedIndex);
      } else {
        return valueList.get(index + 1);
      }
    };
  }

  private static <Value_> int identityIndexOf(List<Value_> sourceList, Value_ query) {
    for (var i = 0; i < sourceList.size(); i++) {
      if (sourceList.get(i) == query) {
        return i;
      }
    }
    return -1;
  }
}
