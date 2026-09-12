package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class ListVariableBeforeChangeActionTest {

  private final VariableDescriptorAwareScoreDirector<TestdataListSolution> scoreDirector =
      mock(VariableDescriptorAwareScoreDirector.class);
  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();

  @Test
  void undoWithEmptyOldValueIsANoOp() {
    var v0 = new TestdataListValue("0");
    var entity = new TestdataListEntity("e", v0);
    // fromIndex == toIndex == 1: a pure insert recorded nothing to restore.
    var action = new ListVariableBeforeChangeAction<>(entity, List.of(), 1, 1, variableDescriptor);

    action.undo(scoreDirector);

    assertThat(entity.getValueList()).containsExactly(v0);
    // The sibling ListVariableAfterChangeAction's undo already notified for this range;
    // this action must not fire a redundant duplicate.
    verifyNoInteractions(scoreDirector);
  }

  @Test
  void undoWithNonEmptyOldValueRestoresAndNotifies() {
    var v0 = new TestdataListValue("0");
    var v1 = new TestdataListValue("1");
    var entity = new TestdataListEntity("e", v1);
    var action =
        new ListVariableBeforeChangeAction<>(entity, List.of(v0), 0, 1, variableDescriptor);

    action.undo(scoreDirector);

    assertThat(entity.getValueList())
        .extracting(TestdataListValue::toString)
        .containsExactly("0", "1");
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, entity, 0, 1);
  }
}
