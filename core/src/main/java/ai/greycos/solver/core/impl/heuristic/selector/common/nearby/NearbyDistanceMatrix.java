package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import ai.greycos.solver.core.impl.domain.variable.supply.Supply;

import org.jspecify.annotations.NonNull;

/**
 * Caches pre-computed distances between origins and destinations to improve performance. Implements
 * the Supply interface for lazy initialization.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>Lazy computation: distances are computed on-demand
 *   <li>Sorted storage: destinations are stored sorted by distance from each origin
 *   <li>Memory efficient: uses a map to store only needed distance arrays
 *   <li>Thread-safe: uses ConcurrentHashMap for concurrent access in multithreaded solving
 *   <li>Optimized sorting: uses O(n log n) TimSort instead of O(n²) binary insertion sort
 *   <li>Optional K-limiting: can limit sorted neighborhood size for memory efficiency
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and can be safely used in multithreaded solver
 * configurations. Multiple threads can call {@link #getDestination(Object, int)} concurrently
 * without external synchronization.
 */
public final class NearbyDistanceMatrix<Origin, Destination> implements Supply {

  private final @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter;
  private final @NonNull Map<Origin, Destination[]> originToDestinationsMap;
  private final @NonNull Function<Origin, Iterator<Destination>> destinationIteratorProvider;
  private final @NonNull ToIntFunction<Origin> destinationSizeFunction;
  private final int maxNearbySortSize;

  public NearbyDistanceMatrix(
      @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter,
      int originSize,
      @NonNull List<Destination> destinationSelector,
      @NonNull ToIntFunction<Origin> destinationSizeFunction) {
    this(
        nearbyDistanceMeter,
        originSize,
        origin -> destinationSelector.iterator(),
        destinationSizeFunction);
  }

  public NearbyDistanceMatrix(
      @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter,
      int originSize,
      @NonNull Function<Origin, Iterator<Destination>> destinationIteratorProvider,
      @NonNull ToIntFunction<Origin> destinationSizeFunction) {
    this(
        nearbyDistanceMeter,
        originSize,
        destinationIteratorProvider,
        destinationSizeFunction,
        Integer.MAX_VALUE);
  }

  public NearbyDistanceMatrix(
      @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter,
      int originSize,
      @NonNull Function<Origin, Iterator<Destination>> destinationIteratorProvider,
      @NonNull ToIntFunction<Origin> destinationSizeFunction,
      int maxNearbySortSize) {
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.originToDestinationsMap =
        new ConcurrentHashMap<>(originSize, 0.75f, Runtime.getRuntime().availableProcessors());
    this.destinationIteratorProvider = destinationIteratorProvider;
    this.destinationSizeFunction = destinationSizeFunction;
    this.maxNearbySortSize = maxNearbySortSize > 0 ? maxNearbySortSize : Integer.MAX_VALUE;
  }

  public void addAllDestinations(@NonNull Origin origin) {
    int destinationSize = destinationSizeFunction.applyAsInt(origin);
    int sortLimit = Math.min(maxNearbySortSize, destinationSize);

    if (sortLimit >= destinationSize) {
      // Sort all destinations using optimized TimSort (O(n log n))
      originToDestinationsMap.put(origin, computeFullSort(origin, destinationSize));
    } else {
      // Sort only the k nearest destinations using priority queue (O(n log k))
      originToDestinationsMap.put(origin, computePartialSort(origin, sortLimit, destinationSize));
    }
  }

  public @NonNull Object getDestination(@NonNull Origin origin, int nearbyIndex) {
    Destination[] destinations = originToDestinationsMap.get(origin);
    if (destinations == null) {
      /*
       * The item may be missing in the distance matrix due to an underlying filtering selector.
       * In such a case, the distance matrix needs to be updated.
       *
       * Use computeIfAbsent for thread-safe lazy initialization. Only one thread will compute
       * the destinations for a given origin, avoiding duplicate distance calculations.
       */
      destinations = originToDestinationsMap.computeIfAbsent(origin, this::computeDestinations);
    }
    return destinations[nearbyIndex];
  }

  /**
   * Computes and returns the sorted destinations array for a given origin.
   *
   * <p>This method is called by {@link #getDestination(Object, int)} via {@link
   * ConcurrentHashMap#computeIfAbsent(Object, java.util.function.Function)} to ensure thread-safe
   * lazy initialization.
   *
   * @param origin the origin for which to compute destinations
   * @return array of destinations sorted by distance from the origin
   */
  private Destination[] computeDestinations(@NonNull Origin origin) {
    int destinationSize = destinationSizeFunction.applyAsInt(origin);
    int sortLimit = Math.min(maxNearbySortSize, destinationSize);

    if (sortLimit >= destinationSize) {
      // Sort all destinations using optimized TimSort (O(n log n))
      return computeFullSort(origin, destinationSize);
    } else {
      // Sort only the k nearest destinations using priority queue (O(n log k))
      return computePartialSort(origin, sortLimit, destinationSize);
    }
  }

  /**
   * Computes full sorted destinations using optimized TimSort (O(n log n)). This replaces the
   * previous O(n²) binary insertion sort algorithm.
   *
   * @param origin the origin for which to compute destinations
   * @param destinationSize the total number of destinations
   * @return array of all destinations sorted by distance from the origin
   */
  @SuppressWarnings("unchecked")
  private Destination[] computeFullSort(@NonNull Origin origin, int destinationSize) {
    // Create list of destination-distance pairs
    List<DestinationDistance> pairs = new ArrayList<>(destinationSize);
    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);

    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      pairs.add(new DestinationDistance(destination, distance));
    }

    if (pairs.size() != destinationSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + pairs.size()
              + ") differs from the expected destinationSize ("
              + destinationSize
              + ").");
    }

    // Use Java's optimized TimSort: O(n log n)
    pairs.sort(Comparator.comparingDouble(p -> p.distance));

    // Extract sorted destinations
    Destination[] sorted = (Destination[]) new Object[destinationSize];
    for (int i = 0; i < pairs.size(); i++) {
      sorted[i] = (Destination) pairs.get(i).destination;
    }

    return sorted;
  }

  /**
   * Computes only the k nearest destinations using a priority queue (O(n log k)). This is more
   * memory-efficient when k << n.
   *
   * @param origin the origin for which to compute destinations
   * @param k the number of nearest destinations to compute
   * @param expectedSize the expected total number of destinations (for validation)
   * @return array of k destinations sorted by distance from the origin
   */
  @SuppressWarnings("unchecked")
  private Destination[] computePartialSort(@NonNull Origin origin, int k, int expectedSize) {
    // Use max-heap to keep k smallest elements
    PriorityQueue<DestinationDistance> heap =
        new PriorityQueue<>(
            k, Comparator.comparingDouble((DestinationDistance p) -> p.distance).reversed());

    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int actualSize = 0;

    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      actualSize++;

      if (heap.size() < k) {
        heap.offer(new DestinationDistance(destination, distance));
      } else if (distance < heap.peek().distance) {
        heap.poll();
        heap.offer(new DestinationDistance(destination, distance));
      }
    }

    if (actualSize != expectedSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + actualSize
              + ") differs from the expected destinationSize ("
              + expectedSize
              + ").");
    }

    // Extract and sort the k nearest
    List<DestinationDistance> kNearest = new ArrayList<>(heap);
    kNearest.sort(Comparator.comparingDouble(p -> p.distance));

    Destination[] result = (Destination[]) new Object[kNearest.size()];
    for (int i = 0; i < kNearest.size(); i++) {
      result[i] = (Destination) kNearest.get(i).destination;
    }

    return result;
  }

  /** Helper class to store destination-distance pairs for sorting. */
  private static class DestinationDistance {
    final Object destination;
    final double distance;

    DestinationDistance(Object destination, double distance) {
      this.destination = destination;
      this.distance = distance;
    }
  }
}
