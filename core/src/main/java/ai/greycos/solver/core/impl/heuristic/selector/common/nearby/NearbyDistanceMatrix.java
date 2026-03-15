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
      heapSortByDistance(destinationBuffer, distanceBuffer, actualSize);
    }
    Destination[] sorted = (Destination[]) new Object[actualSize];
    System.arraycopy(destinationBuffer, 0, sorted, 0, actualSize);
    return sorted;
  }

  @SuppressWarnings("unchecked")
  private Destination[] computePartialSort(@NonNull Origin origin, int k, int expectedSize) {
    Object[] heapDestinations = new Object[k];
    double[] heapDistances = new double[k];
    int[] heapInsertionOrders = new int[k];
    int heapSize = 0;

    Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
    int actualSize = 0;

    while (destinationIterator.hasNext()) {
      Destination destination = destinationIterator.next();
      double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
      int insertionOrder = actualSize;
      actualSize++;

      if (heapSize < k) {
        heapDestinations[heapSize] = destination;
        heapDistances[heapSize] = distance;
        heapInsertionOrders[heapSize] = insertionOrder;
        siftUpMaxHeapByDistanceThenOrder(
            heapDestinations, heapDistances, heapInsertionOrders, heapSize);
        heapSize++;
      } else if (compareDistanceThenOrder(
              distance, insertionOrder, heapDistances[0], heapInsertionOrders[0])
          < 0) {
        heapDestinations[0] = destination;
        heapDistances[0] = distance;
        heapInsertionOrders[0] = insertionOrder;
        siftDownMaxHeapByDistanceThenOrder(
            heapDestinations, heapDistances, heapInsertionOrders, heapSize, 0);
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

    if (heapSize < 2) {
      if (heapSize == heapDestinations.length) {
        return (Destination[]) heapDestinations;
      }
      Destination[] result = (Destination[]) new Object[heapSize];
      if (heapSize > 0) {
        System.arraycopy(heapDestinations, 0, result, 0, heapSize);
      }
      return result;
    }
    heapSortByDistanceThenOrder(heapDestinations, heapDistances, heapInsertionOrders, heapSize);
    if (heapSize == heapDestinations.length) {
      return (Destination[]) heapDestinations;
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

  private static int compareDistanceThenOrder(
      double leftDistance, int leftOrder, double rightDistance, int rightOrder) {
    int distanceComparison = Double.compare(leftDistance, rightDistance);
    if (distanceComparison != 0) {
      return distanceComparison;
    }
    return Integer.compare(leftOrder, rightOrder);
  }

  private static void heapSortByDistance(
      Object[] heapDestinations, double[] heapDistances, int heapSize) {
    for (int index = (heapSize >>> 1) - 1; index >= 0; index--) {
      siftDownMaxHeapByDistance(heapDestinations, heapDistances, heapSize, index);
    }
    for (int end = heapSize - 1; end > 0; end--) {
      swapHeapElements(heapDestinations, heapDistances, 0, end);
      siftDownMaxHeapByDistance(heapDestinations, heapDistances, end, 0);
    }
  }

  private static void siftDownMaxHeapByDistance(
      Object[] heapDestinations, double[] heapDistances, int heapSize, int index) {
    int parentIndex = index;
    while (true) {
      int leftChildIndex = (parentIndex << 1) + 1;
      if (leftChildIndex >= heapSize) {
        return;
      }
      int rightChildIndex = leftChildIndex + 1;
      int largestIndex = leftChildIndex;
      if (rightChildIndex < heapSize
          && Double.compare(heapDistances[rightChildIndex], heapDistances[leftChildIndex]) > 0) {
        largestIndex = rightChildIndex;
      }
      if (Double.compare(heapDistances[parentIndex], heapDistances[largestIndex]) >= 0) {
        return;
      }
      swapHeapElements(heapDestinations, heapDistances, parentIndex, largestIndex);
      parentIndex = largestIndex;
    }
  }

  private static void siftUpMaxHeapByDistanceThenOrder(
      Object[] heapDestinations, double[] heapDistances, int[] heapInsertionOrders, int index) {
    int childIndex = index;
    while (childIndex > 0) {
      int parentIndex = (childIndex - 1) >>> 1;
      if (compareDistanceThenOrder(
              heapDistances[childIndex],
              heapInsertionOrders[childIndex],
              heapDistances[parentIndex],
              heapInsertionOrders[parentIndex])
          <= 0) {
        return;
      }
      swapHeapElements(
          heapDestinations, heapDistances, heapInsertionOrders, childIndex, parentIndex);
      childIndex = parentIndex;
    }
  }

  private static void siftDownMaxHeapByDistanceThenOrder(
      Object[] heapDestinations,
      double[] heapDistances,
      int[] heapInsertionOrders,
      int heapSize,
      int index) {
    int parentIndex = index;
    while (true) {
      int leftChildIndex = (parentIndex << 1) + 1;
      if (leftChildIndex >= heapSize) {
        return;
      }
      int rightChildIndex = leftChildIndex + 1;
      int largestIndex = leftChildIndex;
      if (rightChildIndex < heapSize
          && compareDistanceThenOrder(
                  heapDistances[rightChildIndex],
                  heapInsertionOrders[rightChildIndex],
                  heapDistances[leftChildIndex],
                  heapInsertionOrders[leftChildIndex])
              > 0) {
        largestIndex = rightChildIndex;
      }
      if (compareDistanceThenOrder(
              heapDistances[parentIndex],
              heapInsertionOrders[parentIndex],
              heapDistances[largestIndex],
              heapInsertionOrders[largestIndex])
          >= 0) {
        return;
      }
      swapHeapElements(
          heapDestinations, heapDistances, heapInsertionOrders, parentIndex, largestIndex);
      parentIndex = largestIndex;
    }
  }

  private static void heapSortByDistanceThenOrder(
      Object[] heapDestinations, double[] heapDistances, int[] heapInsertionOrders, int heapSize) {
    for (int end = heapSize - 1; end > 0; end--) {
      swapHeapElements(heapDestinations, heapDistances, heapInsertionOrders, 0, end);
      siftDownMaxHeapByDistanceThenOrder(
          heapDestinations, heapDistances, heapInsertionOrders, end, 0);
    }
  }

  private static void swapHeapElements(
      Object[] heapDestinations,
      double[] heapDistances,
      int[] heapInsertionOrders,
      int leftIndex,
      int rightIndex) {
    Object destination = heapDestinations[leftIndex];
    heapDestinations[leftIndex] = heapDestinations[rightIndex];
    heapDestinations[rightIndex] = destination;

    double distance = heapDistances[leftIndex];
    heapDistances[leftIndex] = heapDistances[rightIndex];
    heapDistances[rightIndex] = distance;

    int insertionOrder = heapInsertionOrders[leftIndex];
    heapInsertionOrders[leftIndex] = heapInsertionOrders[rightIndex];
    heapInsertionOrders[rightIndex] = insertionOrder;
  }

  private static void swapHeapElements(
      Object[] heapDestinations, double[] heapDistances, int leftIndex, int rightIndex) {
    Object destination = heapDestinations[leftIndex];
    heapDestinations[leftIndex] = heapDestinations[rightIndex];
    heapDestinations[rightIndex] = destination;

    double distance = heapDistances[leftIndex];
    heapDistances[leftIndex] = heapDistances[rightIndex];
    heapDistances[rightIndex] = distance;
  }
}
