package greycos.solver.core.impl.heuristic.selector.common.nearby;

/**
 * Calculates distance between origins and destinations for nearby selection. Used to prioritize
 * moves involving spatially proximate items. Implementations must be thread-safe and stateless.
 * Distances are cached for the duration of a phase and must depend only on facts that remain
 * constant during that phase. For list moves, a sublist origin is represented by its first planning
 * value, not by the sublist or its current position.
 *
 * @param <O> Origin type (typically entity or value)
 * @param <D> Destination type (typically entity or value)
 */
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {

  double getNearbyDistance(O origin, D destination);
}
