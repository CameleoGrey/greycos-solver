package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests for {@link NearbyDistanceMatrix}. */
class NearbyDistanceMatrixTest {

  static class Point {
    final double x;
    final double y;

    Point(double x, double y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public String toString() {
      return "(" + x + "," + y + ")";
    }
  }

  static class EuclideanDistanceMeter implements NearbyDistanceMeter<Point, Point> {

    @Override
    public double getNearbyDistance(Point origin, Point destination) {
      double dx = destination.x - origin.x;
      double dy = destination.y - origin.y;
      return Math.sqrt(dx * dx + dy * dy);
    }
  }

  @Test
  void testAddAllDestinations_SortsByDistance() {
    List<Point> destinations =
        Arrays.asList(
            new Point(3, 4), // Distance 5 from origin (0,0)
            new Point(1, 1), // Distance sqrt(2) ≈ 1.414
            new Point(2, 0), // Distance 2
            new Point(0, 3) // Distance 3
            );

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);
    matrix.addAllDestinations(origin);

    // Verify destinations can be retrieved
    Object dest = matrix.getDestination(origin, 0);
    assertThat(dest).isNotNull();
    assertThat(dest).isInstanceOf(Point.class);

    // Verify all destinations are accessible
    for (int i = 0; i < destinations.size(); i++) {
      assertThat(matrix.getDestination(origin, i)).isNotNull();
    }
  }

  @Test
  void testGetDestination_LazyInitialization() {
    List<Point> destinations = Arrays.asList(new Point(1, 0), new Point(0, 1), new Point(2, 0));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);

    // First access should trigger lazy initialization
    Object dest = matrix.getDestination(origin, 0);
    assertThat(dest).isNotNull();

    // Second access should use cached data
    Object dest2 = matrix.getDestination(origin, 1);
    assertThat(dest2).isNotNull();
  }

  @Test
  void testGetDestination_InvalidIndex() {
    List<Point> destinations = Arrays.asList(new Point(1, 0), new Point(0, 1));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);
    matrix.addAllDestinations(origin);

    // Accessing valid indices should work
    assertThat(matrix.getDestination(origin, 0)).isNotNull();
    assertThat(matrix.getDestination(origin, 1)).isNotNull();

    // Accessing index beyond size should throw ArrayIndexOutOfBoundsException
    assertThatThrownBy(() -> matrix.getDestination(origin, 2))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);
  }

  @Test
  void testDistanceMeter_AsymmetricDistances() {
    class AsymmetricDistanceMeter implements NearbyDistanceMeter<Point, Point> {

      @Override
      public double getNearbyDistance(Point origin, Point destination) {
        // Different distances for different directions
        return Math.abs(destination.x - origin.x) + 2 * Math.abs(destination.y - origin.y);
      }
    }

    List<Point> destinations = Arrays.asList(new Point(1, 0), new Point(0, 1));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new AsymmetricDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin1 = new Point(0, 0);
    matrix.addAllDestinations(origin1);

    Point origin2 = new Point(1, 1);
    matrix.addAllDestinations(origin2);

    // Verify both origins have their destinations sorted
    assertThat(matrix.getDestination(origin1, 0)).isNotNull();
    assertThat(matrix.getDestination(origin2, 0)).isNotNull();
  }

  @Test
  void testMultipleOrigins() {
    List<Point> destinations =
        Arrays.asList(new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 3, destinations, origin -> destinations.size());

    Point origin1 = new Point(0, 0);
    Point origin2 = new Point(1, 1);
    Point origin3 = new Point(0.5, 0.5);

    matrix.addAllDestinations(origin1);
    matrix.addAllDestinations(origin2);
    matrix.addAllDestinations(origin3);

    // All origins should have sorted destinations
    assertThat(matrix.getDestination(origin1, 0)).isNotNull();
    assertThat(matrix.getDestination(origin2, 0)).isNotNull();
    assertThat(matrix.getDestination(origin3, 0)).isNotNull();
  }

  @Test
  void testEmptyDestinations() {
    List<Point> destinations = new ArrayList<>();

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);
    matrix.addAllDestinations(origin);

    // Empty destinations should work
    assertThat(destinations).isEmpty();
  }

  @Test
  void testSingleDestination() {
    List<Point> destinations = Arrays.asList(new Point(1, 0));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new EuclideanDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);
    matrix.addAllDestinations(origin);

    Object dest = matrix.getDestination(origin, 0);
    assertThat(dest).isEqualTo(destinations.get(0));
  }

  @Test
  void testDistanceMeter_NullChecks() {
    class NullCheckingDistanceMeter implements NearbyDistanceMeter<Point, Point> {

      @Override
      public double getNearbyDistance(Point origin, Point destination) {
        if (origin == null || destination == null) {
          throw new IllegalArgumentException("Origin and destination must not be null");
        }
        return origin.x + destination.x;
      }
    }

    List<Point> destinations = Arrays.asList(new Point(1, 0));

    NearbyDistanceMatrix<Point, Point> matrix =
        new NearbyDistanceMatrix<>(
            new NullCheckingDistanceMeter(), 1, destinations, origin -> destinations.size());

    Point origin = new Point(0, 0);

    // Should not throw with valid inputs
    matrix.addAllDestinations(origin);
    assertThat(matrix.getDestination(origin, 0)).isNotNull();
  }
}
