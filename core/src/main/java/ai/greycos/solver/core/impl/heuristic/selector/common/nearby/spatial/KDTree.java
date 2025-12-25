package ai.greycos.solver.core.impl.heuristic.selector.common.nearby.spatial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * K-Dimensional tree for fast nearest neighbor searches.
 *
 * <p>A KD-tree is a space-partitioning data structure for organizing points in k-dimensional space.
 * It supports efficient nearest neighbor queries with average O(log n) complexity.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe for read operations after construction. The
 * tree is immutable after construction, making it safe for concurrent access without
 * synchronization.
 *
 * <p><b>Performance Characteristics:</b>
 *
 * <ul>
 *   <li>Construction: O(n log n) average case, O(n²) worst case
 *   <li>Nearest neighbor query: O(log n) average case, O(n) worst case
 *   <li>k-nearest neighbor query: O(k log n) average case
 *   <li>Memory: O(n)
 * </ul>
 *
 * @param <T> the type of elements stored in the tree
 */
public final class KDTree<T> {

  private final @Nullable Node<T> root;
  private final int dimensions;
  private final int size;
  private final @NonNull CoordinateExtractor<T> coordinateExtractor;
  private final @NonNull DistanceFunction<T> distanceFunction;

  /**
   * Creates a new KD-tree from a list of points.
   *
   * @param points the points to build the tree from
   * @param coordinateExtractor function to extract coordinates from points
   * @param distanceFunction function to calculate distance between points
   */
  public KDTree(
      @NonNull List<T> points,
      @NonNull CoordinateExtractor<T> coordinateExtractor,
      @NonNull DistanceFunction<T> distanceFunction) {
    if (points.isEmpty()) {
      this.root = null;
      this.size = 0;
      this.dimensions = 0;
      this.coordinateExtractor = coordinateExtractor;
      this.distanceFunction = distanceFunction;
      return;
    }

    this.coordinateExtractor = coordinateExtractor;
    this.distanceFunction = distanceFunction;
    this.dimensions = coordinateExtractor.getDimensions(points.get(0));
    this.size = points.size();

    // Build tree from points
    List<T> pointsCopy = new ArrayList<>(points);
    this.root = buildTree(pointsCopy, 0, pointsCopy.size(), 0);
  }

  /**
   * Finds the nearest neighbor to the given point.
   *
   * @param point the query point
   * @return the nearest neighbor, or null if the tree is empty
   */
  public @Nullable T findNearest(@NonNull T point) {
    if (root == null) {
      return null;
    }

    NearestNeighborResult<T> result = new NearestNeighborResult<>();
    findNearest(root, point, 0, result);
    return result.nearest;
  }

  /**
   * Finds the k nearest neighbors to the given point.
   *
   * @param point the query point
   * @param k the number of neighbors to find
   * @return list of k nearest neighbors, ordered by distance (closest first)
   */
  @NonNull
  public List<T> findKNearest(@NonNull T point, int k) {
    List<T> neighbors = new ArrayList<>();
    if (root == null || k <= 0) {
      return neighbors;
    }

    PriorityQueue<Neighbor<T>> pq =
        new PriorityQueue<>((a, b) -> Double.compare(b.distance, a.distance));

    findKNearest(root, point, 0, k, pq);

    // Extract in reverse order (farthest to closest)
    // Add to front of list so index 0 is closest
    for (int i = pq.size() - 1; i >= 0; i--) {
      neighbors.add(0, pq.poll().point);
    }

    return neighbors;
  }

  /**
   * Finds all points within a given radius of the query point.
   *
   * @param point the query point
   * @param radius the search radius
   * @return list of points within the radius
   */
  @NonNull
  public List<T> findWithinRadius(@NonNull T point, double radius) {
    List<T> result = new ArrayList<>();
    if (root == null) {
      return result;
    }

    findWithinRadius(root, point, 0, radius * radius, result);
    return result;
  }

  /**
   * Returns the number of points in the tree.
   *
   * @return the size of the tree
   */
  public int size() {
    return size;
  }

  /**
   * Returns the number of dimensions of the tree.
   *
   * @return the dimensionality
   */
  public int getDimensions() {
    return dimensions;
  }

  /**
   * Checks if the tree is empty.
   *
   * @return true if empty, false otherwise
   */
  public boolean isEmpty() {
    return root == null;
  }

  // ************************************************************************
  // Private methods
  // ************************************************************************

  private @Nullable Node<T> buildTree(@NonNull List<T> points, int start, int end, int depth) {
    if (start >= end) {
      return null;
    }

    int axis = depth % dimensions;

    // Sort points by current axis and find median
    points.subList(start, end).sort(Comparator.comparingDouble(p -> getCoordinate(p, axis)));

    int medianIndex = start + (end - start) / 2;

    Node<T> node = new Node<>(points.get(medianIndex), axis);
    node.left = buildTree(points, start, medianIndex, depth + 1);
    node.right = buildTree(points, medianIndex + 1, end, depth + 1);

    return node;
  }

  private void findNearest(
      @NonNull Node<T> node,
      @NonNull T point,
      int depth,
      @NonNull NearestNeighborResult<T> result) {
    if (node == null) {
      return;
    }

    int axis = node.axis;
    double pointCoord = getCoordinate(point, axis);
    double nodeCoord = getCoordinate(node.point, axis);

    // Calculate distance to current node
    double distance = distanceFunction.distance(point, node.point);
    if (result.nearest == null || distance < result.distance) {
      result.nearest = node.point;
      result.distance = distance;
    }

    // Decide which subtree to search first
    Node<T> nearChild = pointCoord < nodeCoord ? node.left : node.right;
    Node<T> farChild = pointCoord < nodeCoord ? node.right : node.left;

    // Search near subtree
    findNearest(nearChild, point, depth + 1, result);

    // Check if we need to search far subtree
    double distanceToSplittingPlane = pointCoord - nodeCoord;
    if (distanceToSplittingPlane * distanceToSplittingPlane < result.distance) {
      findNearest(farChild, point, depth + 1, result);
    }
  }

  private void findKNearest(
      @NonNull Node<T> node,
      @NonNull T point,
      int depth,
      int k,
      @NonNull PriorityQueue<Neighbor<T>> pq) {
    if (node == null) {
      return;
    }

    int axis = node.axis;
    double pointCoord = getCoordinate(point, axis);
    double nodeCoord = getCoordinate(node.point, axis);

    // Calculate distance to current node
    double distance = distanceFunction.distance(point, node.point);

    // Add to priority queue if it's better than current worst
    if (pq.size() < k) {
      pq.add(new Neighbor<>(node.point, distance));
    } else if (distance < pq.peek().getDistance()) {
      pq.poll();
      pq.add(new Neighbor<>(node.point, distance));
    }

    // Decide which subtree to search first
    Node<T> nearChild = pointCoord < nodeCoord ? node.left : node.right;
    Node<T> farChild = pointCoord < nodeCoord ? node.right : node.left;

    // Search near subtree
    findKNearest(nearChild, point, depth + 1, k, pq);

    // Check if we need to search far subtree
    double maxDistance = pq.size() == k ? pq.peek().getDistance() : Double.POSITIVE_INFINITY;
    double distanceToSplittingPlane = pointCoord - nodeCoord;
    if (distanceToSplittingPlane * distanceToSplittingPlane < maxDistance) {
      findKNearest(farChild, point, depth + 1, k, pq);
    }
  }

  private void findWithinRadius(
      @NonNull Node<T> node,
      @NonNull T point,
      int depth,
      double radiusSquared,
      @NonNull List<T> result) {
    if (node == null) {
      return;
    }

    int axis = node.axis;
    double pointCoord = getCoordinate(point, axis);
    double nodeCoord = getCoordinate(node.point, axis);

    // Calculate distance to current node
    double distance = distanceFunction.distance(point, node.point);
    if (distance <= radiusSquared) {
      result.add(node.point);
    }

    // Decide which subtree to search first
    Node<T> nearChild = pointCoord < nodeCoord ? node.left : node.right;
    Node<T> farChild = pointCoord < nodeCoord ? node.right : node.left;

    // Search near subtree
    findWithinRadius(nearChild, point, depth + 1, radiusSquared, result);

    // Check if we need to search far subtree
    double distanceToSplittingPlane = pointCoord - nodeCoord;
    if (distanceToSplittingPlane * distanceToSplittingPlane <= radiusSquared) {
      findWithinRadius(farChild, point, depth + 1, radiusSquared, result);
    }
  }

  private double getCoordinate(@NonNull T point, int axis) {
    return coordinateExtractor.getCoordinate(point, axis);
  }

  // ************************************************************************
  // Inner classes
  // ************************************************************************

  private static final class Node<T> {
    final @NonNull T point;
    final int axis;
    @Nullable Node<T> left;
    @Nullable Node<T> right;

    Node(@NonNull T point, int axis) {
      this.point = point;
      this.axis = axis;
    }
  }

  private static final class NearestNeighborResult<T> {
    @Nullable T nearest;
    double distance = Double.POSITIVE_INFINITY;
  }

  private static final class Neighbor<T> {
    final @NonNull T point;
    final double distance;

    Neighbor(@NonNull T point, double distance) {
      this.point = point;
      this.distance = distance;
    }

    double getDistance() {
      return distance;
    }
  }

  // ************************************************************************
  // Functional interfaces
  // ************************************************************************

  /**
   * Extracts coordinates from a point.
   *
   * @param <T> the point type
   */
  public interface CoordinateExtractor<T> {

    /**
     * Gets the coordinate at the specified axis.
     *
     * @param point the point
     * @param axis the axis (0 for x, 1 for y, etc.)
     * @return the coordinate value
     */
    double getCoordinate(@NonNull T point, int axis);

    /**
     * Gets the number of dimensions for a point.
     *
     * @param point the point
     * @return the number of dimensions
     */
    int getDimensions(@NonNull T point);
  }

  /**
   * Calculates the squared distance between two points.
   *
   * <p>Using squared distance avoids the expensive sqrt operation while maintaining the same
   * ordering.
   *
   * @param <T> the point type
   */
  @FunctionalInterface
  public interface DistanceFunction<T> {

    /**
     * Calculates the squared distance between two points.
     *
     * @param a the first point
     * @param b the second point
     * @return the squared distance
     */
    double distance(@NonNull T a, @NonNull T b);
  }

  // ************************************************************************
  // Factory methods for common use cases
  // ************************************************************************

  /**
   * Creates a 2D KD-tree for points with x and y coordinates.
   *
   * @param points the points
   * @param xGetter function to get x coordinate
   * @param yGetter function to get y coordinate
   * @param <T> the point type
   * @return a new KD-tree
   */
  @NonNull
  public static <T> KDTree<T> create2D(
      @NonNull List<T> points,
      java.util.function.ToDoubleFunction<T> xGetter,
      java.util.function.ToDoubleFunction<T> yGetter) {
    return new KDTree<>(
        points,
        new CoordinateExtractor<>() {
          @Override
          public double getCoordinate(@NonNull T point, int axis) {
            return switch (axis) {
              case 0 -> xGetter.applyAsDouble(point);
              case 1 -> yGetter.applyAsDouble(point);
              default -> throw new IllegalArgumentException("2D tree has only axes 0 and 1");
            };
          }

          @Override
          public int getDimensions(@NonNull T point) {
            return 2;
          }
        },
        (a, b) -> {
          double dx = xGetter.applyAsDouble(a) - xGetter.applyAsDouble(b);
          double dy = yGetter.applyAsDouble(a) - yGetter.applyAsDouble(b);
          return dx * dx + dy * dy;
        });
  }

  /**
   * Creates a 3D KD-tree for points with x, y, and z coordinates.
   *
   * @param points the points
   * @param xGetter function to get x coordinate
   * @param yGetter function to get y coordinate
   * @param zGetter function to get z coordinate
   * @param <T> the point type
   * @return a new KD-tree
   */
  @NonNull
  public static <T> KDTree<T> create3D(
      @NonNull List<T> points,
      java.util.function.ToDoubleFunction<T> xGetter,
      java.util.function.ToDoubleFunction<T> yGetter,
      java.util.function.ToDoubleFunction<T> zGetter) {
    return new KDTree<>(
        points,
        new CoordinateExtractor<>() {
          @Override
          public double getCoordinate(@NonNull T point, int axis) {
            return switch (axis) {
              case 0 -> xGetter.applyAsDouble(point);
              case 1 -> yGetter.applyAsDouble(point);
              case 2 -> zGetter.applyAsDouble(point);
              default -> throw new IllegalArgumentException("3D tree has only axes 0, 1, and 2");
            };
          }

          @Override
          public int getDimensions(@NonNull T point) {
            return 3;
          }
        },
        (a, b) -> {
          double dx = xGetter.applyAsDouble(a) - xGetter.applyAsDouble(b);
          double dy = yGetter.applyAsDouble(a) - yGetter.applyAsDouble(b);
          double dz = zGetter.applyAsDouble(a) - zGetter.applyAsDouble(b);
          return dx * dx + dy * dy + dz * dz;
        });
  }
}
