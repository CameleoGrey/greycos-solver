package greycos.solver.core.api.localsearch;

import org.jspecify.annotations.NullMarked;

/**
 * Applies incremental changes to the active features. The inherited {@code accept} methods insert a
 * feature or replace its current cost. They do not reset its accumulated penalty count.
 *
 * @param <Key_> the immutable feature key type
 */
@NullMarked
public interface GuidedLocalSearchFeatureUpdater<Key_>
    extends GuidedLocalSearchFeatureConsumer<Key_> {

  /** Removes an active feature. Removing an absent feature is an error. */
  void remove(Key_ key);
}
