package greycos.solver.core.api.localsearch;

import java.math.BigDecimal;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;

/**
 * Receives a selected feature of a solution and its nonnegative cost in the units of the score
 * level guided by GLS. A feature is present or absent; repeated occurrences need distinct keys.
 *
 * <p>Keys must be immutable, with stable equality and hash codes across planning clones. In
 * particular, do not use mutable planning entities as keys. Costs may change while a key remains
 * the same; its penalty history then remains associated with that key.
 *
 * @param <Key_> the immutable feature key type
 */
@NullMarked
public interface GuidedLocalSearchFeatureConsumer<Key_> {

  /** Receives a feature with an integral cost, without requiring a boxed number. */
  void accept(Key_ key, long cost);

  /** Receives a feature with an exact decimal cost. */
  void accept(Key_ key, BigDecimal cost);

  /** Receives a finite float cost, preserving its exact represented binary value. */
  default void acceptFloat(Key_ key, float cost) {
    if (!Float.isFinite(cost)) {
      throw new IllegalArgumentException("The GLS feature cost (" + cost + ") must be finite.");
    }
    accept(key, new BigDecimal((double) cost));
  }

  /** Receives a finite double cost, preserving its exact represented binary value. */
  default void acceptDouble(Key_ key, double cost) {
    if (!Double.isFinite(cost)) {
      throw new IllegalArgumentException("The GLS feature cost (" + cost + ") must be finite.");
    }
    accept(key, new BigDecimal(cost));
  }

  /**
   * Receives nonnegative costs for every business score level. The score must have the solution's
   * score type and dimensions, and a zero structural component. Replaces the entire cost vector of
   * this feature. Existing scalar methods address the configured target (the last level in
   * full-score exploration). Consumers which predate vector features may reject this operation.
   */
  default void acceptScore(Key_ key, Score<?> cost) {
    throw new UnsupportedOperationException(
        "This GLS feature consumer does not support score-vector costs.");
  }
}
