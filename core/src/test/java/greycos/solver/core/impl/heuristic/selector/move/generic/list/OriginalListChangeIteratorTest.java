package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.mockEntitySelector;
import static greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class OriginalListChangeIteratorTest {

  @Test
  void emptyValueSelector() {
    assertEmptyIterator(emptyList(), singletonList(new TestdataListEntity("e1")));
  }

  @Test
  void emptyEntitySelector() {
    assertEmptyIterator(singletonList(new TestdataListValue("v1")), emptyList());
  }

  static void assertEmptyIterator(List<Object> values, List<TestdataListEntity> entities) {
    InnerScoreDirector<TestdataListSolution, SimpleScore> scoreDirector =
        mockScoreDirector(TestdataListSolution.buildSolutionDescriptor());
    ListVariableDescriptor<TestdataListSolution> listVariableDescriptor =
        getListVariableDescriptor(scoreDirector);
    IterableValueSelector<TestdataListSolution> valueSelector =
        mockIterableValueSelector(listVariableDescriptor, values.toArray());
    OriginalListChangeIterator<TestdataListSolution> listChangeIterator =
        new OriginalListChangeIterator<>(
            scoreDirector.getSupplyManager().demand(listVariableDescriptor.getStateDemand()),
            valueSelector,
            new ElementDestinationSelector<>(
                mockEntitySelector(entities.toArray(TestdataListEntity[]::new)),
                valueSelector,
                false));

    assertThat(listChangeIterator).isExhausted();
  }
}
