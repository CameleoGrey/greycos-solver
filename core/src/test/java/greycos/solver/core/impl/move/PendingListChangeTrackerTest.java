package greycos.solver.core.impl.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class PendingListChangeTrackerTest {

  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();

  private ListVariableBeforeChangeAction<?, ?, ?> mockAction() {
    return mockAction(variableDescriptor);
  }

  @SuppressWarnings("unchecked")
  private ListVariableBeforeChangeAction<?, ?, ?> mockAction(
      ListVariableDescriptor<TestdataListSolution> descriptor) {
    ListVariableBeforeChangeAction<TestdataListSolution, Object, Object> action =
        mock(ListVariableBeforeChangeAction.class);
    when(action.variableDescriptor()).thenReturn(descriptor);
    return action;
  }

  @Test
  void putThenRemove_singleSlot_noEscalation() {
    var tracker = new PendingListChangeTracker();
    var entity = new TestdataListEntity("e", new TestdataListValue("v"));
    var action = mockAction();

    tracker.put(entity, action);

    assertThat(tracker.remove(variableDescriptor, entity)).isSameAs(action);
    // Consumed: a second remove for the same entity finds nothing.
    assertThat(tracker.remove(variableDescriptor, entity)).isNull();
  }

  @Test
  void sameEntityReopeningAFreshBracket_doesNotEscalate() {
    var tracker = new PendingListChangeTracker();
    var entity = new TestdataListEntity("e", new TestdataListValue("v"));
    var action1 = mockAction();
    var action2 = mockAction();

    tracker.put(entity, action1);
    assertThat(tracker.remove(variableDescriptor, entity)).isSameAs(action1); // Bracket 1 resolved.
    tracker.put(entity, action2); // Bracket 2 reopens the same entity - sequential, so legal.

    // Still resolvable as a single slot - the previous, already-resolved bracket for the same
    // entity must not have forced an unnecessary escalation to the map.
    assertThat(tracker.remove(variableDescriptor, entity)).isSameAs(action2);
  }

  @Test
  void secondDistinctEntity_escalatesToMap() {
    var tracker = new PendingListChangeTracker();
    var entityA = new TestdataListEntity("a", new TestdataListValue("v"));
    var entityB = new TestdataListEntity("b", new TestdataListValue("v"));
    var actionA = mockAction();
    var actionB = mockAction();

    tracker.put(entityA, actionA);
    tracker.put(entityB, actionB); // entityA is still pending: forces escalation.

    // This allocation contract matters for ordinary one-list swaps and k-opt moves.
    assertThat(tracker).extracting("singleDescriptorOverflowMap").isNotNull();
    assertThat(tracker).extracting("overflowMap").isNull();
    assertThat(tracker.remove(variableDescriptor, entityA)).isSameAs(actionA);
    assertThat(tracker.remove(variableDescriptor, entityB)).isSameAs(actionB);
  }

  @Test
  void secondBracketWhileTheFirstIsStillPending_failsFast_singleSlot() {
    // Two concurrently open brackets for one entity cannot be undone - the two ranges shift each
    // other - and keeping only one of them silently corrupts the list variable instead.
    var tracker = new PendingListChangeTracker();
    var entity = new TestdataListEntity("e", new TestdataListValue("v"));
    var originalAction = mockAction();
    tracker.put(entity, originalAction);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(entity, mockAction()))
        .withMessageContaining("already has an open beforeListVariableChanged");
    assertThat(tracker.remove(variableDescriptor, entity)).isSameAs(originalAction);
  }

  @Test
  void secondBracketWhileTheFirstIsStillPending_failsFast_afterEscalation() {
    // The overflow map is also one entry per entity, so it must reject the same thing.
    var tracker = new PendingListChangeTracker();
    var entityA = new TestdataListEntity("a", new TestdataListValue("v"));
    var entityB = new TestdataListEntity("b", new TestdataListValue("v"));
    var actionA = mockAction();
    var actionB = mockAction();
    tracker.put(entityA, actionA);
    tracker.put(entityB, actionB); // Escalates to the overflow map.

    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(entityA, mockAction()))
        .withMessageContaining("already has an open beforeListVariableChanged");
    assertThat(tracker.remove(variableDescriptor, entityA)).isSameAs(actionA);
    assertThat(tracker.remove(variableDescriptor, entityB)).isSameAs(actionB);
  }

  @Test
  void removeForUnknownEntity_returnsNull() {
    var tracker = new PendingListChangeTracker();
    var entity = new TestdataListEntity("e", new TestdataListValue("v"));

    assertThat(tracker.remove(variableDescriptor, entity)).isNull();
  }

  @Test
  void clear_removesEveryPendingEntry() {
    var tracker = new PendingListChangeTracker();
    var entityA = new TestdataListEntity("a", new TestdataListValue("v"));
    var entityB = new TestdataListEntity("b", new TestdataListValue("v"));
    tracker.put(entityA, mockAction());
    tracker.put(entityB, mockAction()); // Escalated to map.

    tracker.clear();

    assertThat(tracker.remove(variableDescriptor, entityA)).isNull();
    assertThat(tracker.remove(variableDescriptor, entityB)).isNull();
  }

  @Test
  void clear_alsoResetsTheSingleSlotBeforeAnyEscalation() {
    var tracker = new PendingListChangeTracker();
    var entity = new TestdataListEntity("e", new TestdataListValue("v"));
    tracker.put(entity, mockAction());

    tracker.clear();

    assertThat(tracker.remove(variableDescriptor, entity)).isNull();
  }

  @Test
  void sameOwnerDifferentDescriptorsRemainIndependent() {
    var tracker = new PendingListChangeTracker();
    var entity = new Object();
    var otherDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var firstAction = mockAction();
    var secondAction = mockAction(otherDescriptor);

    tracker.put(entity, firstAction);
    // A wrong descriptor must not consume the single pending slot.
    assertThat(tracker.remove(otherDescriptor, entity)).isNull();
    tracker.put(entity, secondAction);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(entity, mockAction(otherDescriptor)))
        .withMessageContaining("already has an open beforeListVariableChanged");

    assertThat(tracker.remove(otherDescriptor, entity)).isSameAs(secondAction);
    assertThat(tracker.remove(variableDescriptor, entity)).isSameAs(firstAction);
  }

  @Test
  void differentDescriptorPromotesPendingOwnerMapAndRejectsDuplicatesWithoutReplacingActions() {
    var tracker = new PendingListChangeTracker();
    var ownerA = new Object();
    var ownerB = new Object();
    var otherDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var actionA = mockAction();
    var actionB = mockAction();
    var otherActionA = mockAction(otherDescriptor);
    tracker.put(ownerA, actionA);
    tracker.put(ownerB, actionB);
    assertThat(tracker.remove(otherDescriptor, ownerA)).isNull();

    tracker.put(ownerA, otherActionA);

    assertThat(tracker).extracting("singleDescriptorOverflowMap").isNull();
    assertThat(tracker).extracting("overflowMap").isNotNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(ownerA, mockAction()))
        .withMessageContaining("already has an open beforeListVariableChanged");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(ownerA, mockAction(otherDescriptor)))
        .withMessageContaining("already has an open beforeListVariableChanged");
    assertThat(tracker.remove(variableDescriptor, ownerB)).isSameAs(actionB);
    assertThat(tracker.remove(otherDescriptor, ownerA)).isSameAs(otherActionA);
    assertThat(tracker.remove(variableDescriptor, ownerA)).isSameAs(actionA);
  }

  @Test
  void emptyOwnerMapRetargetsWithoutAllocatingDescriptorMap() {
    var tracker = new PendingListChangeTracker();
    var ownerA = new Object();
    var ownerB = new Object();
    var otherDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    var firstAction = mockAction();
    var secondAction = mockAction();
    tracker.put(ownerA, firstAction);
    tracker.put(ownerB, secondAction);
    assertThat(tracker.remove(variableDescriptor, ownerA)).isSameAs(firstAction);
    assertThat(tracker.remove(variableDescriptor, ownerB)).isSameAs(secondAction);
    var otherAction = mockAction(otherDescriptor);

    assertThat(tracker)
        .extracting("singleDescriptorOverflowMap")
        .satisfies(
            originalOwnerMap -> {
              tracker.put(ownerA, otherAction);
              assertThat(tracker)
                  .extracting("singleDescriptorOverflowMap")
                  .isSameAs(originalOwnerMap);
              assertThat(tracker).extracting("overflowMap").isNull();
            });

    assertThat(tracker.remove(variableDescriptor, ownerA)).isNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> tracker.put(ownerA, mockAction(otherDescriptor)))
        .withMessageContaining("already has an open beforeListVariableChanged");
    assertThat(tracker.remove(otherDescriptor, ownerA)).isSameAs(otherAction);
    tracker.put(ownerB, firstAction);
    assertThat(tracker.remove(otherDescriptor, ownerB)).isNull();
    assertThat(tracker.remove(variableDescriptor, ownerB)).isSameAs(firstAction);
    assertThat(tracker).extracting("overflowMap").isNull();
  }

  @Test
  void clearPromotedTrackerRestoresSingleSlot() {
    var tracker = new PendingListChangeTracker();
    var owner = new Object();
    var otherDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();
    tracker.put(owner, mockAction());
    tracker.put(owner, mockAction(otherDescriptor));

    tracker.clear();

    assertThat(tracker.remove(variableDescriptor, owner)).isNull();
    assertThat(tracker.remove(otherDescriptor, owner)).isNull();
    var action = mockAction();
    tracker.put(owner, action);
    assertThat(tracker).extracting("singleDescriptorOverflowMap").isNull();
    assertThat(tracker).extracting("overflowMap").isNull();
    assertThat(tracker.remove(variableDescriptor, owner)).isSameAs(action);
  }

  @Test
  void equalOwnersAreMatchedByIdentity() {
    var tracker = new PendingListChangeTracker();
    var firstOwner = new String("owner");
    var secondOwner = new String("owner");
    var firstAction = mockAction();
    var secondAction = mockAction();

    tracker.put(firstOwner, firstAction);
    tracker.put(secondOwner, secondAction);

    assertThat(tracker.remove(variableDescriptor, secondOwner)).isSameAs(secondAction);
    assertThat(tracker.remove(variableDescriptor, firstOwner)).isSameAs(firstAction);
  }
}
