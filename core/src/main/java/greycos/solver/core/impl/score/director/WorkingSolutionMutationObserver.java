package greycos.solver.core.impl.score.director;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

/**
 * Internal observation of all working-state mutations, including trial moves and undo. Unlike
 * neighborhood notifications, these notifications are never suppressed for temporary changes.
 */
public interface WorkingSolutionMutationObserver<Solution_> extends AutoCloseable {

  /** Invalidates caches; the solution may still require shadow-variable repair. */
  void workingSolutionChanged();

  /** Whether internal list ownership and adjacency changes are needed, including undo. */
  default boolean requiresListVariableRelationshipChanges() {
    return false;
  }

  /** Called only when an element's owner, predecessor or successor actually changed. */
  default void afterListVariableRelationshipChanged(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {}

  default void beforeVariableChanged(Object entity, String variableName) {}

  default void afterVariableChanged(Object entity, String variableName) {}

  default void beforeListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {}

  default void afterListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {}

  default void beforeListVariableElementAssigned(String variableName, Object element) {}

  default void afterListVariableElementAssigned(String variableName, Object element) {}

  default void beforeListVariableElementUnassigned(String variableName, Object element) {}

  default void afterListVariableElementUnassigned(String variableName, Object element) {}

  @Override
  default void close() {}
}
