package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class OriginalListSwapIteratorTest {

  private final TestdataListValue v1 = new TestdataListValue("v1");
  private final TestdataListValue v2 = new TestdataListValue("v2");

  @Test
  void emptyLeftValueSelector() {
    assertEmptyIterator(emptyList(), asList(v1, v2));
  }

  @Test
  void emptyRightValueSelector() {
    assertEmptyIterator(asList(v1, v2), emptyList());
  }

  static void assertEmptyIterator(List<Object> leftValues, List<Object> rightValues) {
    InnerScoreDirector<TestdataListSolution, SimpleScore> scoreDirector =
        mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        getListVariableDescriptor(scoreDirector);
    OriginalListSwapIterator<TestdataListSolution> listSwapIterator =
        new OriginalListSwapIterator<>(
            scoreDirector.getSupplyManager().demand(listVariableDescriptor.getStateDemand()),
            mockIterableValueSelector(listVariableDescriptor, leftValues.toArray()),
            mockIterableValueSelector(listVariableDescriptor, rightValues.toArray()));

    assertThat(listSwapIterator).isExhausted();
  }
}
