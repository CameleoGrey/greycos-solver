package greycos.solver.core.api.localsearch;

import org.jspecify.annotations.NullMarked;

/**
 * Maintains selected features for one working solution. Sessions receive genuine and shadow
 * variable notifications for trial moves, undo and committed moves alike.
 *
 * <p>Callbacks may observe intermediate states, including temporarily inconsistent shadows. Record
 * affected objects and defer dependent reads to {@link #flushChanges}. Several changes, including
 * an undo followed by another candidate, may arrive before a flush. Track every dependency of
 * feature presence, key and cost; do not infer updates from the move implementation.
 *
 * <p>The engine maintains automatic features separately in full-score exploration. This session
 * reports only custom features; its keys cannot collide with the engine's automatic keys.
 *
 * <p>The session is confined to its score director's thread. It must not mutate the solution or
 * retain a consumer/updater outside the invocation that received it.
 */
@NullMarked
public interface GuidedLocalSearchFeatureSession<Solution_, Key_> extends AutoCloseable {

  /**
   * Discards all previous state. Called lazily after the new solution's shadow variables have
   * settled. The next flush must emit every selected feature into an initially empty registry.
   */
  void resetWorkingSolution(Solution_ workingSolution);

  /**
   * Emits changes since the previous flush, after shadow variables have settled. The engine owns
   * feature costs and penalty counts; the session owns only its model-specific caches.
   */
  void flushChanges(GuidedLocalSearchFeatureUpdater<Key_> updater);

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

  /** Releases session resources. Called at most once. */
  @Override
  default void close() {}
}
