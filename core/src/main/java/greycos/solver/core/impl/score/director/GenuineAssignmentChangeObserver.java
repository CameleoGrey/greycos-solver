package greycos.solver.core.impl.score.director;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

/** Scoped observation of genuine bindings, independent of other working-state observers. */
@FunctionalInterface
public interface GenuineAssignmentChangeObserver<Solution_> {
  void beforeAssignmentChanged(GenuineVariableDescriptor<Solution_> variable, Object entity);

  default void workingSolutionChanged() {
    throw new IllegalStateException(
        "The working solution structure changed while observing a committed move.");
  }

  /** A scope whose close never requires checked exception handling. */
  interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
