package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.getListVariableDescriptor;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.mockEntitySelector;
import static ai.greycos.solver.core.testcotwin.list.TestdataListUtils.mockIterableValueSelector;
import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockScoreDirector;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

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
