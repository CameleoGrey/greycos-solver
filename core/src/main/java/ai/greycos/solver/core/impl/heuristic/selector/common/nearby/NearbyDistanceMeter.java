package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

/**
 * Calculates distance between origins and destinations for nearby selection. Used to prioritize
 * moves involving spatially proximate items. Implementations must be thread-safe and stateless.
 *
 * @param <O> Origin type (typically entity or value)
 * @param <D> Destination type (typically entity or value)
 */
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {

  double getNearbyDistance(O origin, D destination);
}
