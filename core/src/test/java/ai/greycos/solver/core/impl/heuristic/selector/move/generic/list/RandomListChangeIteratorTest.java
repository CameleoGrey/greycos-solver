package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.mockEntitySelector;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static ai.greycos.solver.core.testutil.PlannerAssert.assertCodesOfIterator;
import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;

import java.util.List;

import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

class RandomListChangeIteratorTest {

  @Test
  void iterator() {
    var v1 = new TestdataListValue("1");
    var v2 = new TestdataListValue("2");
    var v3 = new TestdataListValue("3");
    var a = TestdataListEntity.createWithValues("A", v1, v2);
    var b = TestdataListEntity.createWithValues("B");
    var c = TestdataListEntity.createWithValues("C", v3);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(a, b, c));
    solution.setValueList(List.of(v1, v2, v3));

    var scoreDirector = mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    scoreDirector.setWorkingSolution(solution);

    var listVariableDescriptor = getListVariableDescriptor(scoreDirector);
    // Iterates over values in this given order.
    var sourceValueSelector = mockIterableValueSelector(listVariableDescriptor, v1, v2, v3);
    var destinationValueSelector = mockIterableValueSelector(listVariableDescriptor, v2, v3);
    var entitySelector = mockEntitySelector(b, a, c);
    var destinationSelector =
        new ElementDestinationSelector<>(entitySelector, destinationValueSelector, true);

    var random = new TestRandom(3, 0, 1);
    solvingStarted(destinationSelector, scoreDirector, random);
    var randomListChangeIterator =
        new RandomListChangeIterator<>(
            scoreDirector.getSupplyManager().demand(listVariableDescriptor.getStateDemand()),
            sourceValueSelector,
            destinationSelector);

    // <3 => entity selector; >=3 => value selector
    final var destinationRange = entitySelector.getSize() + destinationValueSelector.getSize();

    // The moved values (1, 2, 3) and their source positions are supplied by the mocked value
    // selector.
    // The test is focused on the destinations (A[2], B[0], A[0]), which reflect the numbers
    // supplied by the test random.
    assertCodesOfIterator(
        randomListChangeIterator, "1 {A[0]->A[2]}", "2 {A[1]->B[0]}", "3 {C[0]->A[0]}");

    random.assertIntBoundJustRequested((int) destinationRange);
  }
}
