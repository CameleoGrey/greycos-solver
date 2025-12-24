package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

/**
 * Functional interface for calculating distance between an origin and a destination.
 *
 * <p><b>Thread Safety:</b> Implementations MUST be thread-safe and stateless. The solver may reuse
 * a single instance across multiple threads in multithreaded solving mode. Implementations should
 * not maintain any mutable state between calls to {@link #getNearbyDistance(Object, Object)}.
 *
 * <p><b>Examples of thread-safe implementations:</b>
 *
 * <ul>
 *   <li>Pure mathematical calculations (e.g., Euclidean distance)
 *   <li>Read-only lookups in immutable data structures
 *   <li>Thread-safe cache with concurrent access
 * </ul>
 *
 * <p><b>Examples of thread-unsafe implementations:</b>
 *
 * <ul>
 *   <li>Mutable instance fields modified during distance calculation
 *   <li>Non-thread-safe caches (e.g., HashMap) without synchronization
 *   <li>External API calls with shared mutable state
 * </ul>
 *
 * @param <O> Origin type (typically an entity or value)
 * @param <D> Destination type (typically an entity or value)
 */
@FunctionalInterface
public interface NearbyDistanceMeter<O, D> {

  /**
   * Measures distance from origin to destination.
   *
   * <p>The distance can be in any unit, such as meters, feet, seconds, or milliseconds. For
   * example, vehicle routing often uses driving time in seconds.
   *
   * <p>Distances can be asymmetrical: distance from an origin to a destination often differs from
   * the distance from that destination to that origin.
   *
   * <p><b>Thread Safety:</b> This method may be called concurrently from multiple threads.
   * Implementations must ensure thread-safe behavior without external synchronization.
   *
   * @param origin never null
   * @param destination never null
   * @return Preferably always {@code >= 0.0}. If origin == destination, it usually returns 0.0.
   */
  double getNearbyDistance(O origin, D destination);
}
