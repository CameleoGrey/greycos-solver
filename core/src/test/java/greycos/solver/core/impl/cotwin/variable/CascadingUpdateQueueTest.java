package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CascadingUpdateQueueTest {

  @Test
  void preservesIdentityAndFirstOccurrenceOrderWithoutDuplicateWork() {
    var queue = new CascadingUpdateQueue();
    var first = new EqualValue("same");
    var second = new EqualValue("same");
    queue.addChangedElement(first);
    queue.addChangedElement(second);
    queue.addChangedElement(first);
    assertThat(queue.changedElementCount()).isEqualTo(2);
    assertThat(queue.changedElement(0)).isSameAs(first);
    assertThat(queue.changedElement(1)).isSameAs(second);
    queue.addRange(second, 4, 6);
    queue.addRange(first, 0, 1);
    queue.addRange(second, 5, 6);
    assertThat(queue.updateCount()).isEqualTo(2);
    assertThat(queue.updates(0).entity()).isSameAs(second);
    assertThat(queue.updates(1).entity()).isSameAs(first);
    assertThat(queue.updates(0).rangeCount()).isOne();
  }

  @Test
  void mergesOverlappingAndAdjacentRangesButKeepsUntouchedGaps() {
    var queue = new CascadingUpdateQueue();
    var entity = new Object();
    queue.addRange(entity, 4, 6);
    queue.addRange(entity, 0, 1);
    queue.addRange(entity, 1, 3);
    queue.addRange(entity, 2, 5);
    queue.addRange(entity, 1, 2);
    queue.prepareRanges();
    var updates = queue.updates(0);
    assertThat(updates.rangeCount()).isOne();
    assertThat(updates.fromIndex(0)).isZero();
    assertThat(updates.toIndex(0)).isEqualTo(6);
    queue.addRange(entity, 10, 11);
    queue.addRange(entity, 8, 9);
    queue.prepareRanges();
    assertThat(updates.rangeCount()).isEqualTo(3);
    assertThat(updates.fromIndex(1)).isEqualTo(8);
    assertThat(updates.toIndex(1)).isEqualTo(9);
    queue.addRange(entity, 6, 10);
    queue.prepareRanges();
    assertThat(updates.rangeCount()).isOne();
    assertThat(updates.fromIndex(0)).isZero();
    assertThat(updates.toIndex(0)).isEqualTo(11);
  }

  @Test
  void largeReverseOrderedDisjointBatchIsSortedOnceAtFlush() {
    var queue = new CascadingUpdateQueue();
    var entity = new Object();
    for (var i = 9999; i >= 0; i--) {
      queue.addRange(entity, i * 3, i * 3 + 1);
    }
    queue.prepareRanges();
    var updates = queue.updates(0);
    assertThat(updates.rangeCount()).isEqualTo(10000);
    for (var i = 0; i < 10000; i++) {
      assertThat(updates.fromIndex(i)).isEqualTo(i * 3);
      assertThat(updates.toIndex(i)).isEqualTo(i * 3 + 1);
    }
    queue.close();
    assertThat(updates.entity()).isNull();
  }

  @Test
  void clearReleasesSolutionReferencesAndReusesWorkStorage() {
    var queue = new CascadingUpdateQueue();
    var originalEntity = new Object();
    var originalElement = new Object();
    queue.addRange(originalEntity, 3, 4);
    queue.addChangedElement(originalElement);
    queue.addUnassignedElement(originalElement);
    var pooledUpdates = queue.updates(0);
    queue.clear();
    assertThat(queue.isEmpty()).isTrue();
    assertThat(queue.changedElementCount()).isZero();
    assertThat(queue.unassignedElementCount()).isZero();
    assertThat(pooledUpdates.entity()).isNull();
    assertThat(pooledUpdates.rangeCount()).isZero();
    // Equal old keys must be insertable again, and must not recover an inactive map entry.
    for (var i = 0; i < 100; i++) {
      queue.addChangedElement(originalElement);
      queue.addRange(originalEntity, i, i + 1);
      assertThat(queue.changedElementCount()).isOne();
      assertThat(queue.updateCount()).isOne();
      assertThat(queue.updates(0)).isSameAs(pooledUpdates);
      assertThat(pooledUpdates.fromIndex(0)).isEqualTo(i);
      queue.clear();
      assertThat(pooledUpdates.entity()).isNull();
    }
    queue.close();
    assertThat(queue.isEmpty()).isTrue();
  }

  private record EqualValue(String value) {}
}
