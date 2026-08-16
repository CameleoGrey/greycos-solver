package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static greycos.solver.core.testutil.PlannerAssert.assertCodesOfIterator;
import static greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class RandomListSwapIteratorTest {

  @Test
  void iterator() {
    TestdataListValue v1 = new TestdataListValue("1");
    TestdataListValue v2 = new TestdataListValue("2");
    TestdataListValue v3 = new TestdataListValue("3");
    var e1 = TestdataListEntity.createWithValues("A", v1, v2);
    var e2 = TestdataListEntity.createWithValues("B");
    var e3 = TestdataListEntity.createWithValues("C", v3);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(e1, e2, e3));
    solution.setValueList(List.of(v1, v2, v3));

    InnerScoreDirector<TestdataListSolution, SimpleScore> scoreDirector =
        mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    scoreDirector.setWorkingSolution(solution);

    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        getListVariableDescriptor(scoreDirector);
    RandomListSwapIterator<TestdataListSolution> randomListSwapIterator =
        new RandomListSwapIterator<>(
            scoreDirector.getSupplyManager().demand(listVariableDescriptor.getStateDemand()),
            mockIterableValueSelector(listVariableDescriptor, v1, v1, v1, v3),
            mockIterableValueSelector(listVariableDescriptor, v1, v2, v3, v1));

    assertCodesOfIterator(
        randomListSwapIterator,
        "No change",
        "1 {A[0]} <-> 2 {A[1]}",
        "1 {A[0]} <-> 3 {C[0]}",
        "3 {C[0]} <-> 1 {A[0]}");
  }
}
