package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.originToDestinationsMap =
        new ConcurrentHashMap<>(originSize, 0.75f, Runtime.getRuntime().availableProcessors());
    this.destinationIteratorProvider = destinationIteratorProvider;
    this.destinationSizeFunction = destinationSizeFunction;
  }

  public void addAllDestinations(@NonNull Origin origin) {
    int destinationSize = destinationSizeFunction.applyAsInt(origin);
    Destination[] destinations = (Destination[]) new Object[destinationSize];
    double[] distances = new double[destinationSize];
    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int size = 0;
    double highestDistance = Double.MAX_VALUE;
    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      if (distance < highestDistance || size < destinationSize) {
        int insertIndex = Arrays.binarySearch(distances, 0, size, distance);
        if (insertIndex < 0) {
          insertIndex = -insertIndex - 1;
        } else {
          while (insertIndex < size && distances[insertIndex] == distance) {
            insertIndex++;
          }
        }
        if (size < destinationSize) {
          size++;
        }
        System.arraycopy(
            destinations, insertIndex, destinations, insertIndex + 1, size - insertIndex - 1);
        System.arraycopy(
            distances, insertIndex, distances, insertIndex + 1, size - insertIndex - 1);
        destinations[insertIndex] = destination;
        distances[insertIndex] = distance;
        highestDistance = distances[size - 1];
      }
    }
    if (size != destinationSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + size
              + ") differs from the expected destinationSize ("
              + destinationSize
              + ").");
    }
    originToDestinationsMap.put(origin, destinations);
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
    Destination[] destinations = (Destination[]) new Object[destinationSize];
    double[] distances = new double[destinationSize];
    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int size = 0;
    double highestDistance = Double.MAX_VALUE;
    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      if (distance < highestDistance || size < destinationSize) {
        int insertIndex = Arrays.binarySearch(distances, 0, size, distance);
        if (insertIndex < 0) {
          insertIndex = -insertIndex - 1;
        } else {
          while (insertIndex < size && distances[insertIndex] == distance) {
            insertIndex++;
          }
        }
        if (size < destinationSize) {
          size++;
        }
        System.arraycopy(
            destinations, insertIndex, destinations, insertIndex + 1, size - insertIndex - 1);
        System.arraycopy(
            distances, insertIndex, distances, insertIndex + 1, size - insertIndex - 1);
        destinations[insertIndex] = destination;
        distances[insertIndex] = distance;
        highestDistance = distances[size - 1];
      }
    }
    if (size != destinationSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + size
              + ") differs from the expected destinationSize ("
              + destinationSize
              + ").");
    }
    return destinations;
  }
}
