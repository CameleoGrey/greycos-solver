package greycos.solver.core.api.localsearch;

import java.math.BigDecimal;

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
}
