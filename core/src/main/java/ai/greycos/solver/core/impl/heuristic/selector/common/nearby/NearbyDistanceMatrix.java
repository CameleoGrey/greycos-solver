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
 * Caches destinations sorted by distance from each origin for nearby selection.
 * Uses lazy initialization and thread-safe ConcurrentHashMap for parallel solving.
 * Supports full sorting (O(n log n)) or partial k-nearest sorting (O(n log k)).
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
      originToDestinationsMap.put(origin, computeFullSort(origin, destinationSize));
    } else {
      originToDestinationsMap.put(origin, computePartialSort(origin, sortLimit, destinationSize));
    }
  }

  public @NonNull Object getDestination(@NonNull Origin origin, int nearbyIndex) {
    Destination[] destinations = originToDestinationsMap.get(origin);
    if (destinations == null) {
      destinations = originToDestinationsMap.computeIfAbsent(origin, this::computeDestinations);
    }
    return destinations[nearbyIndex];
  }

  private Destination[] computeDestinations(@NonNull Origin origin) {
    int destinationSize = destinationSizeFunction.applyAsInt(origin);
    int sortLimit = Math.min(maxNearbySortSize, destinationSize);

    if (sortLimit >= destinationSize) {
      return computeFullSort(origin, destinationSize);
    } else {
      return computePartialSort(origin, sortLimit, destinationSize);
    }
  }

  @SuppressWarnings("unchecked")
  private Destination[] computeFullSort(@NonNull Origin origin, int destinationSize) {
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

    pairs.sort(Comparator.comparingDouble(p -> p.distance));

    Destination[] sorted = (Destination[]) new Object[destinationSize];
    for (int i = 0; i < pairs.size(); i++) {
      sorted[i] = (Destination) pairs.get(i).destination;
    }

    return sorted;
  }

  @SuppressWarnings("unchecked")
  private Destination[] computePartialSort(@NonNull Origin origin, int k, int expectedSize) {
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

    List<DestinationDistance> kNearest = new ArrayList<>(heap);
    kNearest.sort(Comparator.comparingDouble(p -> p.distance));

    Destination[] result = (Destination[]) new Object[kNearest.size()];
    for (int i = 0; i < kNearest.size(); i++) {
      result[i] = (Destination) kNearest.get(i).destination;
    }

    return result;
  }

  private static class DestinationDistance {
    final Object destination;
    final double distance;

    DestinationDistance(Object destination, double distance) {
      this.destination = destination;
      this.distance = distance;
    }
  }
}
