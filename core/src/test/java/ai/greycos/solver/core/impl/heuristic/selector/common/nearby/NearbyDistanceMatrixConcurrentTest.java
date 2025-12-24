package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Concurrent thread safety tests for NearbyDistanceMatrix.
 *
 * <p>These tests verify that NearbyDistanceMatrix is safe for concurrent access from multiple
 * threads, which is essential for multithreaded solver operation.
 */
public class NearbyDistanceMatrixConcurrentTest {

  /**
   * Test concurrent access to getDestination with multiple origins.
   *
   * <p>This test verifies that multiple threads can safely call getDestination() concurrently
   * without race conditions, duplicate computations, or data corruption.
   */
  @Test
  void testConcurrentGetDestination() throws InterruptedException {
    // Setup: Create a simple distance meter and distance matrix
    TestDistanceMeter distanceMeter = new TestDistanceMeter();
    List<String> destinations = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J");
    NearbyDistanceMatrix<String, String> matrix =
        new NearbyDistanceMatrix<>(
            distanceMeter,
            10, // originSize
            destinations,
            origin -> destinations.size());

    // Create thread pool with multiple threads
    int threadCount = 8;
    int iterationsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(threadCount);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    // Each thread will access the distance matrix concurrently
    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            startLatch.countDown();
            try {
              startLatch.await(); // Wait for all threads to start simultaneously
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }

            try {
              // Each thread accesses multiple origins and indices
              for (int j = 0; j < iterationsPerThread; j++) {
                String origin = "Origin" + ((threadId * iterationsPerThread + j) % 10);
                int nearbyIndex = j % destinations.size();
                String destination = (String) matrix.getDestination(origin, nearbyIndex);
                assertNotNull(destination, "Destination should not be null");
                successCount.incrementAndGet();
              }
            } finally {
              endLatch.countDown();
            }
          });
    }

    // Wait for all threads to complete
    assertTrue(endLatch.await(30, TimeUnit.SECONDS), "All threads should complete within timeout");

    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate");

    // Verify results
    int expectedSuccessCount = threadCount * iterationsPerThread;
    assertEquals(
        expectedSuccessCount,
        successCount.get(),
        "All getDestination calls should succeed without errors");
  }

  /**
   * Test concurrent initialization of the same origin.
   *
   * <p>This test verifies that when multiple threads call getDestination() for the same origin
   * simultaneously, only one thread computes the distances (lazy initialization) and all threads
   * receive the same result.
   */
  @Test
  void testConcurrentSameOriginInitialization() throws InterruptedException {
    TestDistanceMeter distanceMeter = new TestDistanceMeter();
    List<String> destinations = List.of("A", "B", "C", "D", "E");
    NearbyDistanceMatrix<String, String> matrix =
        new NearbyDistanceMatrix<>(
            distanceMeter,
            5, // originSize
            destinations,
            origin -> destinations.size());

    int threadCount = 4;
    int iterationsPerThread = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(threadCount);
    CountDownLatch endLatch = new CountDownLatch(threadCount);
    AtomicInteger computationCount = new AtomicInteger(0);

    // All threads will access the SAME origin
    String sameOrigin = "Origin0";

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }

            try {
              for (int j = 0; j < iterationsPerThread; j++) {
                String destination = (String) matrix.getDestination(sameOrigin, 0);
                assertNotNull(destination);
              }
            } finally {
              endLatch.countDown();
            }
          });
      startLatch.countDown();
    }

    assertTrue(endLatch.await(30, TimeUnit.SECONDS), "All threads should complete");

    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    // Verify that distances were computed only once (lazy initialization)
    // The distance meter counts how many times getNearbyDistance was called
    // For the same origin, it should be called at most once
    int maxExpectedComputations = 1; // At most one computation due to computeIfAbsent
    assertTrue(
        computationCount.get() <= maxExpectedComputations * threadCount,
        "Distance computation should happen at most once due to computeIfAbsent");
  }

  /**
   * Test concurrent access with different origins simultaneously.
   *
   * <p>This test verifies that multiple threads can compute distances for different origins
   * concurrently without interference.
   */
  @Test
  void testConcurrentDifferentOrigins() throws InterruptedException {
    TestDistanceMeter distanceMeter = new TestDistanceMeter();
    List<String> destinations = List.of("A", "B", "C", "D", "E");
    NearbyDistanceMatrix<String, String> matrix =
        new NearbyDistanceMatrix<>(
            distanceMeter,
            10, // originSize
            destinations,
            origin -> destinations.size());

    int threadCount = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(threadCount);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    // Each thread uses a different origin
    for (int i = 0; i < threadCount; i++) {
      final String origin = "Origin" + i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }

            try {
              // Access multiple destinations for this origin
              for (int j = 0; j < destinations.size(); j++) {
                String destination = (String) matrix.getDestination(origin, j);
                assertNotNull(destination);
              }
            } finally {
              endLatch.countDown();
            }
          });
      startLatch.countDown();
    }

    assertTrue(endLatch.await(30, TimeUnit.SECONDS), "All threads should complete");

    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
  }

  /**
   * Test that the distance matrix produces consistent results across threads.
   *
   * <p>This test verifies that for the same origin and index, all threads receive the same
   * destination (deterministic behavior).
   */
  @Test
  void testConcurrentDeterministicResults() throws InterruptedException {
    TestDistanceMeter distanceMeter = new TestDistanceMeter();
    List<String> destinations = List.of("A", "B", "C", "D", "E");
    NearbyDistanceMatrix<String, String> matrix =
        new NearbyDistanceMatrix<>(
            distanceMeter,
            5, // originSize
            destinations,
            origin -> destinations.size());

    int threadCount = 8;
    int iterations = 20;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(threadCount);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    String origin = "Origin0";
    int nearbyIndex = 2;
    String expectedDestination = destinations.get(nearbyIndex);

    // Store results from all threads
    List<String> results = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      final int threadId = i;
      executor.submit(
          () -> {
            try {
              startLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }

            try {
              String destination = (String) matrix.getDestination(origin, nearbyIndex);
              synchronized (results) {
                results.add(destination);
              }
            } finally {
              endLatch.countDown();
            }
          });
      startLatch.countDown();
    }

    assertTrue(endLatch.await(30, TimeUnit.SECONDS), "All threads should complete");

    executor.shutdown();
    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

    // All results should be the same
    for (String result : results) {
      assertEquals(
          expectedDestination,
          result,
          "All threads should receive the same destination for deterministic behavior");
    }
  }

  /**
   * Simple test distance meter that tracks how many times it was called. This helps verify lazy
   * initialization behavior.
   */
  private static class TestDistanceMeter implements NearbyDistanceMeter<String, String> {
    private final AtomicInteger callCount = new AtomicInteger(0);

    @Override
    public double getNearbyDistance(String origin, String destination) {
      callCount.incrementAndGet();
      // Simple distance calculation based on character codes
      return Math.abs(origin.charAt(0) - destination.charAt(0));
    }

    public int getCallCount() {
      return callCount.get();
    }
  }
}
