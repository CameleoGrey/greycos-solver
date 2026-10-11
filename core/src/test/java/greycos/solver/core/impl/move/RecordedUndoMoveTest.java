package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class RecordedUndoMoveTest {

  @Test
  @SuppressWarnings("unchecked")
  void retainedUndoRebindsEveryActionToIndependentDestinationModel() {
    var sourceDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var destinationDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var source = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 1);
    var destination = TestdataMultipleListSolution.generateUninitializedSolution(1, 1, 1);
    var sourceOwner = source.getEntityList().getFirst();
    var destinationOwner = destination.getEntityList().getFirst();
    var sourceFirst = source.getFirstValueList().getFirst();
    var destinationFirst = destination.getFirstValueList().getFirst();
    var sourceSecond = source.getSecondValueList().getFirst();
    var destinationSecond = destination.getSecondValueList().getFirst();
    var sourceFirstDescriptor =
        sourceDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "firstValueList");
    var sourceSecondDescriptor =
        sourceDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "secondValueList");
    var destinationFirstDescriptor =
        destinationDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "firstValueList");
    var destinationSecondDescriptor =
        destinationDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "secondValueList");
    var sourceBasic =
        sourceDescriptor
            .findEntityDescriptorOrFail(TestdataMultipleListEntity.class)
            .getVariableDescriptor("basicValue");
    var destinationBasic =
        destinationDescriptor
            .findEntityDescriptorOrFail(TestdataMultipleListEntity.class)
            .getVariableDescriptor("basicValue");
    var sourceQuantity =
        sourceDescriptor
            .findEntityDescriptorOrFail(TestdataMultipleListSecondValue.class)
            .getVariableDescriptor("quantity");
    var destinationQuantity =
        destinationDescriptor
            .findEntityDescriptorOrFail(TestdataMultipleListSecondValue.class)
            .getVariableDescriptor("quantity");
    assertThat(destinationFirstDescriptor).isNotSameAs(sourceFirstDescriptor);
    assertThat(destinationSecondDescriptor).isNotSameAs(sourceSecondDescriptor);
    assertThat(destinationBasic).isNotSameAs(sourceBasic);
    assertThat(destinationQuantity).isNotSameAs(sourceQuantity);
    InnerScoreDirector<TestdataMultipleListSolution, SimpleScore> sourceDirector =
        mock(InnerScoreDirector.class);
    var recorder = new VariableChangeRecordingScoreDirector<>(sourceDirector);
    sourceOwner.getFirstValueList().add(sourceFirst);
    sourceOwner.setBasicValue(0);
    sourceSecond.setQuantity(1);

    recorder.beforeVariableChanged(sourceBasic, sourceOwner);
    sourceOwner.setBasicValue(2);
    recorder.afterVariableChanged(sourceBasic, sourceOwner);
    recorder.beforeVariableChanged(sourceQuantity, sourceSecond);
    sourceSecond.setQuantity(3);
    recorder.afterVariableChanged(sourceQuantity, sourceSecond);
    recorder.beforeListVariableElementUnassigned(sourceFirstDescriptor, sourceFirst);
    recorder.beforeListVariableChanged(sourceFirstDescriptor, sourceOwner, 0, 1);
    sourceOwner.getFirstValueList().clear();
    recorder.afterListVariableChanged(sourceFirstDescriptor, sourceOwner, 0, 0);
    recorder.afterListVariableElementUnassigned(sourceFirstDescriptor, sourceFirst);
    recorder.beforeListVariableElementAssigned(sourceSecondDescriptor, sourceSecond);
    recorder.beforeListVariableChanged(sourceSecondDescriptor, sourceOwner, 0, 0);
    sourceOwner.getSecondValueList().add(sourceSecond);
    recorder.afterListVariableChanged(sourceSecondDescriptor, sourceOwner, 0, 1);
    recorder.afterListVariableElementAssigned(sourceSecondDescriptor, sourceSecond);
    var undo = recorder.createUndoMove();

    // Recreate the changed state using independent working objects and model descriptors.
    destinationOwner.setBasicValue(2);
    destinationSecond.setQuantity(3);
    destinationOwner.getSecondValueList().add(destinationSecond);
    VariableDescriptorAwareScoreDirector<TestdataMultipleListSolution> destinationDirector =
        mock(VariableDescriptorAwareScoreDirector.class);
    when(destinationDirector.getSolutionDescriptor()).thenReturn(destinationDescriptor);
    MoveDirector<TestdataMultipleListSolution, SimpleScore> destinationView =
        mock(MoveDirector.class);
    when(destinationView.getScoreDirector()).thenReturn(destinationDirector);
    when(destinationView.lookUpWorkingObject(sourceOwner)).thenReturn(destinationOwner);
    when(destinationView.lookUpWorkingObject(sourceFirst)).thenReturn(destinationFirst);
    when(destinationView.lookUpWorkingObject(sourceSecond)).thenReturn(destinationSecond);
    when(destinationView.lookUpWorkingObject(0)).thenReturn(0);
    when(destinationView.lookUpWorkingObject(1)).thenReturn(1);

    undo.rebase(destinationView).execute(destinationView);

    assertThat(destinationOwner.getFirstValueList()).containsExactly(destinationFirst);
    assertThat(destinationOwner.getSecondValueList()).isEmpty();
    assertThat(destinationOwner.getBasicValue()).isZero();
    assertThat(destinationSecond.getQuantity()).isEqualTo(1);
    assertThat(sourceOwner.getFirstValueList()).isEmpty();
    assertThat(sourceOwner.getSecondValueList()).containsExactly(sourceSecond);
    assertThat(sourceOwner.getBasicValue()).isEqualTo(2);
    assertThat(sourceSecond.getQuantity()).isEqualTo(3);
    verify(destinationDirector)
        .beforeListVariableElementAssigned(destinationFirstDescriptor, destinationFirst);
    verify(destinationDirector)
        .afterListVariableElementAssigned(destinationFirstDescriptor, destinationFirst);
    verify(destinationDirector)
        .beforeListVariableElementUnassigned(destinationSecondDescriptor, destinationSecond);
    verify(destinationDirector)
        .afterListVariableElementUnassigned(destinationSecondDescriptor, destinationSecond);
    verify(destinationDirector)
        .beforeListVariableChanged(destinationFirstDescriptor, destinationOwner, 0, 0);
    verify(destinationDirector)
        .afterListVariableChanged(destinationFirstDescriptor, destinationOwner, 0, 1);
    verify(destinationDirector)
        .beforeListVariableChanged(destinationSecondDescriptor, destinationOwner, 0, 1);
    verify(destinationDirector)
        .afterListVariableChanged(destinationSecondDescriptor, destinationOwner, 0, 0);
    verify(destinationDirector).beforeVariableChanged(destinationBasic, destinationOwner);
    verify(destinationDirector).afterVariableChanged(destinationBasic, destinationOwner);
    verify(destinationDirector).beforeVariableChanged(destinationQuantity, destinationSecond);
    verify(destinationDirector).afterVariableChanged(destinationQuantity, destinationSecond);
  }

  @Test
  void executeReplaysActionsInReverseRecordingOrder() {
    var callOrder = new ArrayList<String>();
    var action1 = variableChangeAction("action1", callOrder);
    var action2 = variableChangeAction("action2", callOrder);
    var action3 = variableChangeAction("action3", callOrder);
    var move = new RecordedUndoMove<>(List.of(action1, action2, action3));

    var scoreDirector = mock(VariableDescriptorAwareScoreDirector.class);
    var solutionView = mock(InnerMutableSolutionView.class);
    doAnswer(invocation -> scoreDirector).when(solutionView).getScoreDirector();

    move.execute(solutionView);

    assertThat(callOrder).containsExactly("action3", "action2", "action1");
  }

  @SuppressWarnings("unchecked")
  private ChangeAction<Object> variableChangeAction(String label, List<String> callOrder) {
    var variableDescriptor = mock(VariableDescriptor.class);
    doAnswer(invocation -> callOrder.add(label)).when(variableDescriptor).setValue(any(), any());
    return new VariableChangeAction<>(new Object(), new Object(), variableDescriptor);
  }
}
