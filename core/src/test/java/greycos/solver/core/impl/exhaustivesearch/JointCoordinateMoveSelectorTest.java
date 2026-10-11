package greycos.solver.core.impl.exhaustivesearch;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import greycos.solver.core.impl.exhaustivesearch.decider.JointVariableExhaustiveSearchDecider.DecisionFilter;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class JointCoordinateMoveSelectorTest {

  @Test
  @SuppressWarnings("unchecked")
  void rejectsNeverEndingChildrenBeforeIteration() {
    MoveSelector<TestdataMultipleListSolution> child = mock(MoveSelector.class);
    when(child.isNeverEnding()).thenReturn(true);
    var descriptor =
        TestdataMultipleListSolution.buildSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataMultipleListEntity.class)
            .getGenuineVariableDescriptor("firstValueList");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new JointCoordinateMoveSelector<>(child, descriptor, new DecisionFilter<>()))
        .withMessageContaining("joint exhaustiveSearch coordinate")
        .withMessageContaining("firstValueList")
        .withMessageContaining("never-ending child move selector")
        .withMessageContaining("ORIGINAL, SORTED or SHUFFLED");
    verify(child, never()).iterator();
  }
}
