package ai.greycos.solver.core.impl.heuristic.selector.common.nearby.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link KDTree}. */
class KDTreeTest {

  /** Simple 2D point class for testing. */
  private static final class Point2D {
    final double x;
    final double y;
    final String name;

    Point2D(String name, double x, double y) {
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
  void testEmptyTree() {
    List<Point2D> points = new ArrayList<>();
    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    assertTrue(tree.isEmpty());
    assertEquals(0, tree.size());
    assertEquals(0, tree.getDimensions());
  }

  @Test
  void testSinglePoint() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("origin", 0.0, 0.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    assertEquals(1, tree.size());
    Point2D nearest = tree.findNearest(new Point2D("query", 0.1, 0.1));
    assertEquals("origin", nearest.name);
  }

  @Test
  void testFindNearestSimple() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 0.0, 0.0));
    points.add(new Point2D("B", 10.0, 0.0));
    points.add(new Point2D("C", 0.0, 10.0));
    points.add(new Point2D("D", 10.0, 10.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    Point2D nearest = tree.findNearest(new Point2D("query", 1.0, 1.0));
    assertEquals("A", nearest.name);
  }

  @Test
  void testFindNearestTieBreak() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 0.0, 0.0));
    points.add(new Point2D("B", 1.0, 0.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    Point2D nearest = tree.findNearest(new Point2D("query", 0.5, 0.0));
    // Either A or B is acceptable (equal distance)
    assertTrue(nearest.name.equals("A") || nearest.name.equals("B"));
  }

  @Test
  void testFindKNearest() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 0.0, 0.0));
    points.add(new Point2D("B", 1.0, 0.0));
    points.add(new Point2D("C", 2.0, 0.0));
    points.add(new Point2D("D", 3.0, 0.0));
    points.add(new Point2D("E", 4.0, 0.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    List<Point2D> nearest = tree.findKNearest(new Point2D("query", 2.5, 0.0), 3);

    assertEquals(3, nearest.size());
    assertEquals("C", nearest.get(0).name);
    assertEquals("D", nearest.get(1).name);
    assertEquals("B", nearest.get(2).name);
  }

  @Test
  void testFindKNearestAll() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 0.0, 0.0));
    points.add(new Point2D("B", 1.0, 0.0));
    points.add(new Point2D("C", 2.0, 0.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    List<Point2D> nearest = tree.findKNearest(new Point2D("query", 10.0, 0.0), 10);

    assertEquals(3, nearest.size());
    // Should be sorted by distance (closest first)
    assertEquals("A", nearest.get(0).name);
    assertEquals("B", nearest.get(1).name);
    assertEquals("C", nearest.get(2).name);
  }

  @Test
  void testFindWithinRadius() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 0.0, 0.0));
    points.add(new Point2D("B", 1.0, 0.0));
    points.add(new Point2D("C", 2.0, 0.0));
    points.add(new Point2D("D", 3.0, 0.0));
    points.add(new Point2D("E", 4.0, 0.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    // Radius of 1.5 should include A and B
    List<Point2D> within = tree.findWithinRadius(new Point2D("query", 0.5, 0.0), 1.5);

    assertEquals(2, within.size());
    assertTrue(within.stream().anyMatch(p -> p.name.equals("A")));
    assertTrue(within.stream().anyMatch(p -> p.name.equals("B")));
  }

  @Test
  void testFindWithinRadiusEmpty() {
    List<Point2D> points = new ArrayList<>();
    points.add(new Point2D("A", 10.0, 10.0));
    points.add(new Point2D("B", 20.0, 20.0));

    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);

    List<Point2D> within = tree.findWithinRadius(new Point2D("query", 0.0, 0.0), 1.0);

    assertTrue(within.isEmpty());
  }

  @Test
  void testLargeDatasetPerformance() {
    // Create a grid of 100x100 points
    List<Point2D> points = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      for (int j = 0; j < 100; j++) {
        points.add(new Point2D("P" + i + "_" + j, i * 1.0, j * 1.0));
      }
    }

    long startTime = System.currentTimeMillis();
    KDTree<Point2D> tree = KDTree.create2D(points, p -> p.x, p -> p.y);
    long buildTime = System.currentTimeMillis() - startTime;

    assertEquals(10000, tree.size());

    // Query nearest neighbor many times
    startTime = System.currentTimeMillis();
    for (int i = 0; i < 1000; i++) {
      double qx = Math.random() * 100;
      double qy = Math.random() * 100;
      Point2D nearest = tree.findNearest(new Point2D("query", qx, qy));
      assertNotNull(nearest);
    }
    long queryTime = System.currentTimeMillis() - startTime;

    // Build time should be reasonable (< 1 second for 10k points)
    assertTrue(buildTime < 1000, "Build time: " + buildTime + "ms");
    // Query time should be fast (< 100ms for 1000 queries)
    assertTrue(queryTime < 100, "Query time: " + queryTime + "ms");
  }

  @Test
  void test3DTree() {
    List<Point3D> points = new ArrayList<>();
    points.add(new Point3D("A", 0.0, 0.0, 0.0));
    points.add(new Point3D("B", 10.0, 0.0, 0.0));
    points.add(new Point3D("C", 0.0, 10.0, 0.0));
    points.add(new Point3D("D", 0.0, 0.0, 10.0));

    KDTree<Point3D> tree = KDTree.create3D(points, p -> p.x, p -> p.y, p -> p.z);

    assertEquals(4, tree.size());
    assertEquals(3, tree.getDimensions());

    Point3D nearest = tree.findNearest(new Point3D("query", 1.0, 1.0, 1.0));
    assertEquals("A", nearest.name);
  }

  /** Simple 3D point class for testing. */
  private static final class Point3D {
    final double x;
    final double y;
    final double z;
    final String name;

    Point3D(String name, double x, double y, double z) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }
}
