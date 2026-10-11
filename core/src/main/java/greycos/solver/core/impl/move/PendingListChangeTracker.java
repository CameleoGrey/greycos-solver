package greycos.solver.core.impl.move;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tracks, per list variable and entity, the {@link ListVariableBeforeChangeAction} awaiting its
 * matching {@code afterListVariableChanged} call, so the two can be merged into a single undo step
 * instead of two.
 *
 * <p>At most one bracket per list variable and entity may be open at a time. A second {@code
 * beforeListVariableChanged} for a list whose bracket is still open is rejected: such a sequence
 * cannot be undone (the two ranges shift each other), and silently keeping only one of the two
 * brackets corrupts the list variable instead. Different list variables on the same entity may have
 * concurrently open brackets. Sequential brackets for the same list are also fine.
 *
 * <p>The common case is exactly one entity pending at a time (every single-entity list move, such
 * as plain change/assign/unassign). Only lesser used moves (such as k-opt) ever have more than one
 * pending concurrently. This starts as a zero-allocation single slot and escalates to a real map
 * only once a second, distinct list variable or entity's bracket opens while the first is still
 * pending. Several owners of one list variable share one identity map; a second map level is
 * allocated only when different list variables have concurrently open brackets.
 */
@NullMarked
final class PendingListChangeTracker {

  private @Nullable Object singleEntity;
  private @Nullable ListVariableBeforeChangeAction<?, ?, ?> singleAction;
  private @Nullable ListVariableDescriptor<?> overflowDescriptor;
  private @Nullable Map<Object, ListVariableBeforeChangeAction<?, ?, ?>>
      singleDescriptorOverflowMap;
  private @Nullable
      Map<ListVariableDescriptor<?>, Map<Object, ListVariableBeforeChangeAction<?, ?, ?>>>
      overflowMap;

  void put(Object entity, ListVariableBeforeChangeAction<?, ?, ?> action) {
    var variableDescriptor = Objects.requireNonNull(action.variableDescriptor());
    if (singleDescriptorOverflowMap != null || overflowMap != null) {
      putOverflow(variableDescriptor, entity, action);
      return;
    }
    if (singleEntity == null) { // The slot is free.
      singleEntity = entity;
      singleAction = action;
      return;
    }
    if (singleEntity == entity
        && Objects.requireNonNull(singleAction).variableDescriptor() == variableDescriptor) {
      throw bracketAlreadyOpen(entity, Objects.requireNonNull(singleAction), action);
    }
    // Cross-owner moves of one descriptor need only the same single map as a one-list model.
    singleDescriptorOverflowMap = new IdentityHashMap<>(4);
    singleDescriptorOverflowMap.put(singleEntity, Objects.requireNonNull(singleAction));
    overflowDescriptor = singleAction.variableDescriptor();
    singleEntity = null;
    singleAction = null;
    putOverflow(variableDescriptor, entity, action);
  }

  private void putOverflow(
      ListVariableDescriptor<?> variableDescriptor,
      Object entity,
      ListVariableBeforeChangeAction<?, ?, ?> action) {
    Map<Object, ListVariableBeforeChangeAction<?, ?, ?>> entityMap;
    if (overflowMap != null) {
      entityMap =
          overflowMap.computeIfAbsent(variableDescriptor, ignored -> new IdentityHashMap<>(4));
    } else {
      entityMap = Objects.requireNonNull(singleDescriptorOverflowMap);
      if (overflowDescriptor != variableDescriptor) {
        if (entityMap.isEmpty()) {
          // Earlier brackets are closed; sequential fields can reuse the same owner map.
          overflowDescriptor = variableDescriptor;
        } else {
          // Retain the existing owner map when a different field opens concurrently.
          overflowMap = new IdentityHashMap<>(4);
          overflowMap.put(Objects.requireNonNull(overflowDescriptor), entityMap);
          entityMap = new IdentityHashMap<>(4);
          overflowMap.put(variableDescriptor, entityMap);
          singleDescriptorOverflowMap = null;
          overflowDescriptor = null;
        }
      }
    }
    var previousAction = entityMap.get(entity);
    if (previousAction != null) {
      throw bracketAlreadyOpen(entity, previousAction, action);
    }
    entityMap.put(entity, action);
  }

  private static IllegalArgumentException bracketAlreadyOpen(
      Object entity,
      ListVariableBeforeChangeAction<?, ?, ?> openAction,
      ListVariableBeforeChangeAction<?, ?, ?> newAction) {
    return new IllegalArgumentException(
        """
        The entity (%s) already has an open beforeListVariableChanged (%d, %d) for list variable (%s), \
        so another beforeListVariableChanged (%d, %d) for it must not be opened.
        Maybe close each beforeListVariableChanged with its afterListVariableChanged \
        before changing the same list variable on that entity again.\
        """
            .formatted(
                entity,
                openAction.fromIndex(),
                openAction.originalToIndex(),
                openAction.variableDescriptor(),
                newAction.fromIndex(),
                newAction.originalToIndex()));
  }

  @Nullable ListVariableBeforeChangeAction<?, ?, ?> remove(
      ListVariableDescriptor<?> variableDescriptor, Object entity) {
    if (overflowMap != null) {
      var entityMap = overflowMap.get(variableDescriptor);
      return entityMap == null ? null : entityMap.remove(entity);
    }
    if (singleDescriptorOverflowMap != null) {
      return overflowDescriptor == variableDescriptor
          ? singleDescriptorOverflowMap.remove(entity)
          : null;
    }
    if (singleEntity == entity
        && Objects.requireNonNull(singleAction).variableDescriptor() == variableDescriptor) {
      var action = singleAction;
      singleEntity = null;
      singleAction = null;
      return action;
    }
    return null;
  }

  void clear() {
    singleEntity = null;
    singleAction = null;
    overflowDescriptor = null;
    singleDescriptorOverflowMap = null;
    overflowMap = null;
  }
}
