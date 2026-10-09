package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;

import org.junit.jupiter.api.Test;

class SolutionAssignmentsTest {

  @Test
  void equalButDistinctCanonicalValuesAreReorderedAndUndoneByIdentity() {
    var first = new EqualValue("first");
    var second = new EqualValue("second");
    var entity = new TestdataPinnedWithIndexListEntity("owner", first, second);
    var solution = solution(entity, first, second);
    var variable = TestdataPinnedWithIndexListEntity.buildVariableDescriptorForValueList();
    var backing = backing(solution);
    var recorder =
        new VariableChangeRecordingScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore>(
            backing);
    var assignments =
        SolutionAssignments.<TestdataPinnedWithIndexListSolution>of(
            Map.of(),
            Map.of(
                variable,
                List.of(
                    new SolutionAssignments.ListChangeRecord<>(entity, List.of(second, first)))));

    assignments.apply(recorder);

    assertThat(entity.getValueList().get(0)).isSameAs(second);
    assertThat(entity.getValueList().get(1)).isSameAs(first);
    verify(backing).beforeListVariableChanged(variable, entity, 0, 2);
    verify(backing).afterListVariableChanged(variable, entity, 0, 2);
    verify(backing, never()).beforeListVariableElementAssigned(any(), any());
    verify(backing, never()).beforeListVariableElementUnassigned(any(), any());

    recorder.undoChanges();

    assertThat(entity.getValueList().get(0)).isSameAs(first);
    assertThat(entity.getValueList().get(1)).isSameAs(second);
    verify(backing).updateShadowVariables();
  }

  @Test
  void equalButDistinctValueCannotReplacePinnedPrefix() {
    var pinned = new EqualValue("pinned");
    var other = new EqualValue("other");
    var entity = new TestdataPinnedWithIndexListEntity("owner", pinned, other);
    entity.setPinIndex(1);
    var variable = TestdataPinnedWithIndexListEntity.buildVariableDescriptorForValueList();
    var backing = backing(solution(entity, pinned, other));
    var recorder =
        new VariableChangeRecordingScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore>(
            backing);
    var assignments =
        SolutionAssignments.<TestdataPinnedWithIndexListSolution>of(
            Map.of(),
            Map.of(
                variable,
                List.of(
                    new SolutionAssignments.ListChangeRecord<>(entity, List.of(other, pinned)))));

    assertThatThrownBy(() -> assignments.apply(recorder))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Pinned list segment differs");

    assertThat(entity.getValueList().get(0)).isSameAs(pinned);
    assertThat(entity.getValueList().get(1)).isSameAs(other);
    verify(backing, never())
        .beforeListVariableChanged(
            any(ListVariableDescriptor.class), any(Object.class), anyInt(), anyInt());
    verify(backing, never())
        .beforeVariableChanged(any(VariableDescriptor.class), any(Object.class));
  }

  private static TestdataPinnedWithIndexListSolution solution(
      TestdataPinnedWithIndexListEntity entity, TestdataPinnedWithIndexListValue... values) {
    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(values));
    return solution;
  }

  @SuppressWarnings("unchecked")
  private static InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore> backing(
      TestdataPinnedWithIndexListSolution solution) {
    var director =
        (InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore>)
            mock(InnerScoreDirector.class);
    when(director.getWorkingSolution()).thenReturn(solution);
    return director;
  }

  private static final class EqualValue extends TestdataPinnedWithIndexListValue {
    private EqualValue(String code) {
      super(code);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualValue;
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }
}
