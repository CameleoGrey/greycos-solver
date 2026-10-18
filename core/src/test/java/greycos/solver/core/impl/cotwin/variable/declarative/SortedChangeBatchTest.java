package greycos.solver.core.impl.cotwin.variable.declarative;

import static greycos.solver.core.impl.cotwin.variable.declarative.SortedChangeBatch.sortKey;
import static greycos.solver.core.impl.cotwin.variable.declarative.SortedChangeBatch.sortKeyIndex;
import static greycos.solver.core.impl.cotwin.variable.declarative.SortedChangeBatch.sortKeyPosition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;

import org.junit.jupiter.api.Test;

class SortedChangeBatchTest {

  @Test
  void decodesIndexAndPosition() {
    for (var index : new int[] {0, 1, 5, Integer.MAX_VALUE}) {
      for (var position : new int[] {0, 1, 1000, Integer.MAX_VALUE}) {
        var sortKey = sortKey(index, position);
        assertThat(sortKeyIndex(sortKey)).isEqualTo(index);
        assertThat(sortKeyPosition(sortKey)).isEqualTo(position);
      }
    }
  }

  @Test
  void sortsByIndexThenByPosition() {
    // Ties keep the insertion order.
    var sortKeyArray =
        new long[] {
          sortKey(3, 0), sortKey(2, 1), sortKey(0, 2), sortKey(2, 3), sortKey(3, 4), sortKey(1, 5)
        };
    Arrays.sort(sortKeyArray);
    assertThat(Arrays.stream(sortKeyArray).mapToInt(SortedChangeBatch::sortKeyPosition))
        .containsExactly(2, 5, 1, 3, 0, 4);
  }

  @SuppressWarnings("unchecked")
  @Test
  void backwardBatchReadsInDescendingIndexOrder() {
    var ownerA = "A";
    var ownerB = "B";
    ListVariableState<?, Object, Object> listVariableState = mock(ListVariableState.class);
    when(listVariableState.getElementPosition("a1")).thenReturn(ElementPosition.of(ownerA, 1));
    when(listVariableState.getElementPosition("b2")).thenReturn(ElementPosition.of(ownerB, 2));
    when(listVariableState.getElementPosition("u")).thenReturn(ElementPosition.unassigned());
    when(listVariableState.getElementPosition("a3")).thenReturn(ElementPosition.of(ownerA, 3));
    when(listVariableState.getElementPosition("a0")).thenReturn(ElementPosition.of(ownerA, 0));

    var batch = new SortedChangeBatch(false);
    batch.load(List.of("a1", "b2", "u", "a3", "a0"), listVariableState);

    assertThat(IntStream.range(0, batch.size()).mapToObj(batch::entity))
        .containsExactly("a3", "b2", "a1", "u", "a0");
    assertThat(IntStream.range(0, batch.size()).map(batch::index)).containsExactly(3, 2, 1, 0, 0);

    var lastProcessedIndex = batch.lastProcessedIndexToAdvance(0);
    assertThat(lastProcessedIndex).isNotNull();
    lastProcessedIndex.setValue(1); // The walk from a3 stopped at a1.
    assertThat(batch.lastProcessedIndexToAdvance(2)).isNull();
    assertThat(batch.lastProcessedIndexToAdvance(4)).isSameAs(lastProcessedIndex);
  }

  @SuppressWarnings("unchecked")
  @Test
  void equalIndexesKeepInsertionOrderInBothDirectionsAndAfterReuse() {
    var ownerA = new String("owner");
    var ownerB = new String("owner"); // Equality must not combine owners.
    ListVariableState<?, Object, Object> state = mock(ListVariableState.class);
    when(state.getElementPosition("a0")).thenReturn(ElementPosition.of(ownerA, 0));
    when(state.getElementPosition("b0")).thenReturn(ElementPosition.of(ownerB, 0));
    when(state.getElementPosition("a2")).thenReturn(ElementPosition.of(ownerA, 2));
    when(state.getElementPosition("b2")).thenReturn(ElementPosition.of(ownerB, 2));
    when(state.getElementPosition("u")).thenReturn(ElementPosition.unassigned());
    for (var forward : List.of(true, false)) {
      var batch = new SortedChangeBatch(forward);
      for (var pass = 0; pass < 2; pass++) {
        batch.load(List.of("a2", "b2", "u", "a0", "b0"), state);
        assertThat(IntStream.range(0, batch.size()).mapToObj(batch::entity))
            .containsExactlyElementsOf(
                forward
                    ? List.of("u", "a0", "b0", "a2", "b2")
                    : List.of("a2", "b2", "u", "a0", "b0"));
        var firstOwnerPosition = forward ? 1 : 0;
        var secondOwnerPosition = forward ? 2 : 1;
        var firstProgress = batch.lastProcessedIndexToAdvance(firstOwnerPosition);
        var secondProgress = batch.lastProcessedIndexToAdvance(secondOwnerPosition);
        assertThat(firstProgress).isNotNull().isNotSameAs(secondProgress);
        firstProgress.setValue(forward ? 2 : 0);
        secondProgress.setValue(forward ? 0 : 2);
        assertThat(batch.lastProcessedIndexToAdvance(3)).isNull();
        assertThat(batch.lastProcessedIndexToAdvance(4)).isSameAs(secondProgress);
        batch.clear();
        assertThat(batch.size()).isZero();
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  void growsThenReusesBatchWithoutStaleOwnerProgress() {
    var owner = new Object();
    ListVariableState<?, Object, Object> state = mock(ListVariableState.class);
    var entities = IntStream.range(0, 40).mapToObj(i -> (Object) new Object()).toList();
    for (var i = 0; i < entities.size(); i++) {
      when(state.getElementPosition(entities.get(i))).thenReturn(ElementPosition.of(owner, i));
    }
    var batch = new SortedChangeBatch(true);
    batch.load(entities, state);
    assertThat(batch.size()).isEqualTo(40);
    batch.lastProcessedIndexToAdvance(0).setValue(39);
    assertThat(batch.lastProcessedIndexToAdvance(39)).isNull();
    batch.clear();
    batch.load(List.of(entities.get(1)), state);
    assertThat(batch.size()).isOne();
    assertThat(batch.entity(0)).isSameAs(entities.get(1));
    assertThat(batch.index(0)).isOne();
    assertThat(batch.lastProcessedIndexToAdvance(0)).isNotNull();
    batch.clear();
  }
}
