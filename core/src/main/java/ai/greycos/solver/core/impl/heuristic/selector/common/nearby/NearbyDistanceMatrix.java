package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;

import org.jspecify.annotations.NonNull;

/**
 * Caches destinations sorted by distance from each origin for nearby selection. Uses lazy
 * initialization and thread-safe ConcurrentHashMap for parallel solving. Supports full sorting (O(n
 * log n)) or partial k-nearest sorting (O(n log k)).
 */
public final class NearbyDistanceMatrix<Origin, Destination> implements Supply {

  private final @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter;
  private final @NonNull Map<Origin, Destination[]> originToDestinationsMap;
  private final @NonNull Function<Origin, Iterator<Destination>> destinationIteratorProvider;
  private final @NonNull ToIntFunction<Origin> destinationSizeFunction;
  private final int maxNearbySortSize;
  private final boolean strictDestinationSize;

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
    this(
        nearbyDistanceMeter,
        originSize,
        destinationIteratorProvider,
        destinationSizeFunction,
        maxNearbySortSize,
        true);
  }

  public NearbyDistanceMatrix(
      @NonNull NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter,
      int originSize,
      @NonNull Function<Origin, Iterator<Destination>> destinationIteratorProvider,
      @NonNull ToIntFunction<Origin> destinationSizeFunction,
      int maxNearbySortSize,
      boolean strictDestinationSize) {
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.originToDestinationsMap =
        new ConcurrentHashMap<>(originSize, 0.75f, Runtime.getRuntime().availableProcessors());
    this.destinationIteratorProvider = destinationIteratorProvider;
    this.destinationSizeFunction = destinationSizeFunction;
    this.maxNearbySortSize = maxNearbySortSize > 0 ? maxNearbySortSize : Integer.MAX_VALUE;
    this.strictDestinationSize = strictDestinationSize;
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
    Destination[] destinations = getOrComputeDestinations(origin);
    return destinations[nearbyIndex];
  }

  public int getDestinationSize(@NonNull Origin origin) {
    return getOrComputeDestinations(origin).length;
  }

  private Destination[] getOrComputeDestinations(@NonNull Origin origin) {
    return originToDestinationsMap.computeIfAbsent(origin, this::computeDestinations);
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
    Object[] destinationBuffer = destinationSize == 0 ? new Object[0] : new Object[destinationSize];
    double[] distanceBuffer = destinationSize == 0 ? new double[0] : new double[destinationSize];
    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int actualSize = 0;

    while (destinationIterator.hasNext()) {
      if (actualSize == destinationBuffer.length) {
        int newCapacity = growCapacity(destinationBuffer.length);
        destinationBuffer = Arrays.copyOf(destinationBuffer, newCapacity);
        distanceBuffer = Arrays.copyOf(distanceBuffer, newCapacity);
      }
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      destinationBuffer[actualSize] = destination;
      distanceBuffer[actualSize] = distance;
      actualSize++;
    }

    if (strictDestinationSize && actualSize != destinationSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + actualSize
              + ") differs from the expected destinationSize ("
              + destinationSize
              + ").");
    }

    if (actualSize > 1) {
      sortByDistance(destinationBuffer, distanceBuffer, 0, actualSize - 1);
    }
    Destination[] sorted = (Destination[]) new Object[actualSize];
    System.arraycopy(destinationBuffer, 0, sorted, 0, actualSize);
    return sorted;
  }

  @SuppressWarnings("unchecked")
  private Destination[] computePartialSort(@NonNull Origin origin, int k, int expectedSize) {
    Object[] heapDestinations = new Object[k];
    double[] heapDistances = new double[k];
    int heapSize = 0;

    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int actualSize = 0;

    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      actualSize++;

      if (heapSize < k) {
        heapDestinations[heapSize] = destination;
        heapDistances[heapSize] = distance;
        siftUpMaxHeap(heapDestinations, heapDistances, heapSize);
        heapSize++;
      } else if (Double.compare(distance, heapDistances[0]) < 0) {
        heapDestinations[0] = destination;
        heapDistances[0] = distance;
        siftDownMaxHeap(heapDestinations, heapDistances, heapSize, 0);
      }
    }

    if (strictDestinationSize && actualSize != expectedSize) {
      throw new IllegalStateException(
          "The destinationIterator's size ("
              + actualSize
              + ") differs from the expected destinationSize ("
              + expectedSize
              + ").");
    }

    if (heapSize > 1) {
      sortByDistance(heapDestinations, heapDistances, 0, heapSize - 1);
    }
    Destination[] result = (Destination[]) new Object[heapSize];
    System.arraycopy(heapDestinations, 0, result, 0, heapSize);
    return result;
  }

  private static int growCapacity(int previousCapacity) {
    if (previousCapacity == 0) {
      return 1;
    }
    if (previousCapacity >= 1024) {
      return previousCapacity + (previousCapacity >> 1);
    }
    return previousCapacity << 1;
  }

  private static void siftUpMaxHeap(Object[] destinations, double[] distances, int index) {
    int current = index;
    while (current > 0) {
      int parent = (current - 1) >>> 1;
      if (Double.compare(distances[parent], distances[current]) >= 0) {
        return;
      }
      swap(destinations, distances, parent, current);
      current = parent;
    }
  }

  private static void siftDownMaxHeap(
      Object[] destinations, double[] distances, int size, int index) {
    int current = index;
    while (true) {
      int leftChild = (current << 1) + 1;
      if (leftChild >= size) {
        return;
      }
      int rightChild = leftChild + 1;
      int maxChild = leftChild;
      if (rightChild < size && Double.compare(distances[rightChild], distances[leftChild]) > 0) {
        maxChild = rightChild;
      }
      if (Double.compare(distances[current], distances[maxChild]) >= 0) {
        return;
      }
      swap(destinations, distances, current, maxChild);
      current = maxChild;
    }
  }

  private static void sortByDistance(
      Object[] destinations, double[] distances, int lowInclusive, int highInclusive) {
    int left = lowInclusive;
    int right = highInclusive;
    double pivot = distances[(lowInclusive + highInclusive) >>> 1];

    while (left <= right) {
      while (Double.compare(distances[left], pivot) < 0) {
        left++;
      }
      while (Double.compare(distances[right], pivot) > 0) {
        right--;
      }
      if (left <= right) {
        swap(destinations, distances, left, right);
        left++;
        right--;
      }
    }

    if (lowInclusive < right) {
      sortByDistance(destinations, distances, lowInclusive, right);
    }
    if (left < highInclusive) {
      sortByDistance(destinations, distances, left, highInclusive);
    }
  }

  private static void swap(
      Object[] destinations, double[] distances, int leftIndex, int rightIndex) {
    if (leftIndex == rightIndex) {
      return;
    }
    Object destination = destinations[leftIndex];
    destinations[leftIndex] = destinations[rightIndex];
    destinations[rightIndex] = destination;

    double distance = distances[leftIndex];
    distances[leftIndex] = distances[rightIndex];
    distances[rightIndex] = distance;
  }
}
