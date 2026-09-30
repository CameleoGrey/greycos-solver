package greycos.solver.core.api.localsearch;

import org.jspecify.annotations.NullMarked;

/**
 * Defines the features guided by GLS, independently of the business score calculator.
 *
 * <p>Select features that can usefully change, omitting known immutable features. For example,
 * route arcs inside a pinned prefix are fixed, but the outgoing boundary arc may still change.
 * Costs must be nonnegative. Scalar costs use the configured fixed target's units, or the last
 * business level's units in full-score exploration. Score-vector costs use each level's own units.
 * In full-score exploration, custom features supplement automatic assignment and adjacency
 * features. In fixed-target mode this provider supplies all features. There is no automatic
 * conversion from constraint matches or from hard to soft scores.
 *
 * <p>Providers may be shared by worker threads and therefore must be stateless or thread-safe. Each
 * working solution receives its own mutable session. Neither method may modify the solution.
 *
 * @param <Solution_> the planning solution type
 * @param <Key_> the immutable feature key type
 */
@NullMarked
public interface GuidedLocalSearchFeatureProvider<Solution_, Key_> {

  /**
   * Independently enumerates all currently selected features, each key exactly once. Used as a
   * correctness oracle; it must not read an incremental session's caches. Zero costs are allowed
   * but never receive penalty increments.
   */
  void extractFeatures(Solution_ solution, GuidedLocalSearchFeatureConsumer<Key_> consumer);

  /**
   * Creates a fresh session. Returning a shared instance or {@code null} is invalid. There is no
   * implicit full-solution extraction fallback during candidate evaluation.
   */
  GuidedLocalSearchFeatureSession<Solution_, Key_> newSession();
}
