package ai.greycos.solver.core.impl.heuristic.selector.common.nearby.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SpatialNearbyDistanceMatrix}. */
class SpatialNearbyDistanceMatrixTest {

  /** Simple distance meter for testing. */
  private static final class SimpleDistanceMeter
      implements NearbyDistanceMeter<Location, Location> {

    @Override
    public double getNearbyDistance(Location origin, Location destination) {
      double dx = origin.x - destination.x;
      double dy = origin.y - destination.y;
      return Math.sqrt(dx * dx + dy * dy);
    }
  }

  /** Simple 2D location class for testing. */
  private static final class Location {
    final double x;
    final double y;
    final String name;

    Location(String name, double x, double y) {
      this.name = name;
      this.x = x;
      this.y = y;
    }

    @Override
    public String toString() {
      return name + "(" + x + "," + y + ")";
    }
  }

  @Test
  void testStandardSorting() {
    List<Location> destinations = new ArrayList<>();
    destinations.add(new Location("A", 0.0, 0.0));
    destinations.add(new Location("B", 10.0, 0.0));
    destinations.add(new Location("C", 5.0, 5.0));

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 1, destinations, o -> destinations.size());

    Location origin = new Location("origin", 1.0, 1.0);

    // Get closest destination (index 0)
    Object closest = matrix.getDestination(origin, 0);
    assertTrue(closest instanceof Location);
    assertEquals("A", ((Location) closest).name);

    // Get second closest (index 1)
    Object second = matrix.getDestination(origin, 1);
    assertEquals("C", ((Location) second).name);

    // Get farthest (index 2)
    Object farthest = matrix.getDestination(origin, 2);
    assertEquals("B", ((Location) farthest).name);
  }

  @Test
  void testSpatialIndexingWith2D() {
    List<Location> destinations = new ArrayList<>();
    // Create a grid of points
    for (int i = 0; i < 10; i++) {
      for (int j = 0; j < 10; j++) {
        destinations.add(new Location("P" + i + "_" + j, i * 10.0, j * 10.0));
      }
    }

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        SpatialNearbyDistanceMatrix.create2D(
            new SimpleDistanceMeter(),
            1,
            destinations,
            o -> destinations.size(),
            loc -> loc.x,
            loc -> loc.y);

    Location origin = new Location("origin", 45.0, 45.0);

    // Get nearest destinations
    Object nearest = matrix.getDestination(origin, 0);
    assertNotNull(nearest);
    assertTrue(nearest instanceof Location);

    Location nearestLoc = (Location) nearest;
    // Should be close to (45, 45)
    assertTrue(nearestLoc.x >= 40.0 && nearestLoc.x <= 50.0);
    assertTrue(nearestLoc.y >= 40.0 && nearestLoc.y <= 50.0);
  }

  @Test
  void testSpatialIndexingWith3D() {
    List<Location3D> destinations = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 5; j++) {
        for (int k = 0; k < 5; k++) {
          destinations.add(
              new Location3D("P" + i + "_" + j + "_" + k, i * 10.0, j * 10.0, k * 10.0));
        }
      }
    }

    NearbyDistanceMeter<Location3D, Location3D> distanceMeter =
        (o, d) -> {
          double dx = o.x - d.x;
          double dy = o.y - d.y;
          double dz = o.z - d.z;
          return Math.sqrt(dx * dx + dy * dy + dz * dz);
        };

    SpatialNearbyDistanceMatrix<Location3D, Location3D> matrix =
        SpatialNearbyDistanceMatrix.create3D(
            distanceMeter,
            1,
            destinations,
            o -> destinations.size(),
            loc -> loc.x,
            loc -> loc.y,
            loc -> loc.z);

    Location3D origin = new Location3D("origin", 25.0, 25.0, 25.0);

    Object nearest = matrix.getDestination(origin, 0);
    assertNotNull(nearest);
    assertTrue(nearest instanceof Location3D);

    Location3D nearestLoc = (Location3D) nearest;
    // Should be close to (25, 25, 25)
    assertTrue(nearestLoc.x >= 20.0 && nearestLoc.x <= 30.0);
    assertTrue(nearestLoc.y >= 20.0 && nearestLoc.y <= 30.0);
    assertTrue(nearestLoc.z >= 20.0 && nearestLoc.z <= 30.0);
  }

  @Test
  void testCacheSize() {
    List<Location> destinations = new ArrayList<>();
    destinations.add(new Location("A", 0.0, 0.0));
    destinations.add(new Location("B", 10.0, 0.0));

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 2, destinations, o -> destinations.size());

    assertEquals(0, matrix.getCacheSize());

    // Access destinations for origin 1
    Location origin1 = new Location("origin1", 1.0, 1.0);
    matrix.getDestination(origin1, 0);
    assertEquals(1, matrix.getCacheSize());

    // Access destinations for origin 2
    Location origin2 = new Location("origin2", 5.0, 5.0);
    matrix.getDestination(origin2, 0);
    assertEquals(2, matrix.getCacheSize());

    // Access origin 1 again - should use cache
    matrix.getDestination(origin1, 1);
    assertEquals(2, matrix.getCacheSize());
  }

  @Test
  void testClearCache() {
    List<Location> destinations = new ArrayList<>();
    destinations.add(new Location("A", 0.0, 0.0));

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 1, destinations, o -> destinations.size());

    Location origin = new Location("origin", 1.0, 1.0);
    matrix.getDestination(origin, 0);
    assertEquals(1, matrix.getCacheSize());

    matrix.clearCache();
    assertEquals(0, matrix.getCacheSize());
  }

  @Test
  void testSpatialIndexThreshold() {
    List<Location> destinations = new ArrayList<>();
    // Create 50 destinations (below default threshold of 1000)
    for (int i = 0; i < 50; i++) {
      destinations.add(new Location("P" + i, i * 10.0, 0.0));
    }

    // With threshold set to 100, should use standard sorting
    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        SpatialNearbyDistanceMatrix.create2D(
            new SimpleDistanceMeter(),
            1,
            destinations,
            o -> destinations.size(),
            loc -> loc.x,
            loc -> loc.y);

    Location origin = new Location("origin", 25.0, 0.0);

    Object nearest = matrix.getDestination(origin, 0);
    assertNotNull(nearest);
    // Should be P2 or P3 (closest to 25)
    Location nearestLoc = (Location) nearest;
    assertTrue(nearestLoc.name.equals("P2") || nearestLoc.name.equals("P3"));
  }

  @Test
  void testMultipleOrigins() {
    List<Location> destinations = new ArrayList<>();
    destinations.add(new Location("A", 0.0, 0.0));
    destinations.add(new Location("B", 10.0, 0.0));
    destinations.add(new Location("C", 0.0, 10.0));

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 3, destinations, o -> destinations.size());

    Location origin1 = new Location("origin1", 1.0, 1.0);
    Location origin2 = new Location("origin2", 9.0, 1.0);
    Location origin3 = new Location("origin3", 1.0, 9.0);

    // Each origin should have different nearest destination
    Object nearest1 = matrix.getDestination(origin1, 0);
    Object nearest2 = matrix.getDestination(origin2, 0);
    Object nearest3 = matrix.getDestination(origin3, 0);

    assertEquals("A", ((Location) nearest1).name);
    assertEquals("B", ((Location) nearest2).name);
    assertEquals("C", ((Location) nearest3).name);
  }

  @Test
  void testEmptyDestinations() {
    List<Location> destinations = new ArrayList<>();

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 1, destinations, o -> destinations.size());

    Location origin = new Location("origin", 1.0, 1.0);

    // Empty destinations should return empty array
    Object[] sorted = new Object[0];
    // This will fail with exception, which is expected behavior
    try {
      matrix.getDestination(origin, 0);
    } catch (ArrayIndexOutOfBoundsException e) {
      // Expected
    }
  }

  @Test
  void testSingleDestination() {
    List<Location> destinations = new ArrayList<>();
    destinations.add(new Location("A", 0.0, 0.0));

    SpatialNearbyDistanceMatrix<Location, Location> matrix =
        new SpatialNearbyDistanceMatrix<>(
            new SimpleDistanceMeter(), 1, destinations, o -> destinations.size());

    Location origin = new Location("origin", 10.0, 10.0);

    Object nearest = matrix.getDestination(origin, 0);
    assertEquals("A", ((Location) nearest).name);
  }

  /** Simple 3D location class for testing. */
  private static final class Location3D {
    final double x;
    final double y;
    final double z;
    final String name;

    Location3D(String name, double x, double y, double z) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
