package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Memory and resource management tests for multithreading functionality. These tests validate that
 * multithreading properly manages memory usage, thread resources, and cleanup.
 */
public class MemoryAndResourceManagementTest {

  private MemoryMXBean memoryBean;
  private ThreadMXBean threadBean;
  private long initialMemoryUsage;
  private long initialThreadCount;

  @BeforeEach
  void setUp() {
    memoryBean = ManagementFactory.getMemoryMXBean();
    threadBean = ManagementFactory.getThreadMXBean();

    // Record initial state
    initialMemoryUsage = getCurrentMemoryUsage();
    initialThreadCount = threadBean.getThreadCount();
  }

  @Test
  void testMemoryUsageWithMultithreading() {
    // Test that multithreading doesn't cause excessive memory usage
    long memoryBefore = getCurrentMemoryUsage();

    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("4");
    solverConfig.setMoveThreadBufferSize(50);

    TestdataSolution solution = createTestSolution(100, 20);
    solution = solveWithConfig(solverConfig, solution);

    long memoryAfter = getCurrentMemoryUsage();
    long memoryIncrease = memoryAfter - memoryBefore;

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Memory increase should be reasonable (less than 100MB for this test)
    assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024);
  }

  @Test
  void testThreadCountManagement() {
    // Test that thread count is properly managed
    long threadsBefore = threadBean.getThreadCount();

    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("3");

    TestdataSolution solution = createTestSolution(50, 10);
    solution = solveWithConfig(solverConfig, solution);

    long threadsAfter = threadBean.getThreadCount();

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Thread count should not increase excessively
    // Allow some tolerance for thread pool creation and cleanup
    assertThat(threadsAfter - threadsBefore).isLessThan(10);
  }

  @Test
  void testResourceCleanupAfterSolving() {
    // Test that resources are properly cleaned up after solving
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    TestdataSolution solution = createTestSolution(30, 6);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Force garbage collection to clean up resources
    System.gc();
    System.runFinalization();

    // Give some time for cleanup
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Memory usage should not be excessively high after cleanup
    long finalMemoryUsage = getCurrentMemoryUsage();
    assertThat(finalMemoryUsage - initialMemoryUsage).isLessThan(50 * 1024 * 1024);
  }

  @Test
  void testLargeProblemMemoryManagement() {
    // Test memory management with large problems
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("4");
    solverConfig.setMoveThreadBufferSize(100);

    // Create a large problem
    TestdataSolution solution = createTestSolution(500, 100);

    long memoryBefore = getCurrentMemoryUsage();
    solution = solveWithConfig(solverConfig, solution);
    long memoryAfter = getCurrentMemoryUsage();

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Memory increase should be proportional to problem size
    long memoryIncrease = memoryAfter - memoryBefore;
    // Allow up to 10MB per 100 entities
    long expectedMemoryIncrease = (500 / 100) * 10 * 1024 * 1024;
    assertThat(memoryIncrease).isLessThan(expectedMemoryIncrease);
  }

  @Test
  void testMultipleSolversMemoryManagement() {
    // Test memory management when creating multiple solvers
    long memoryBefore = getCurrentMemoryUsage();

    List<Solver<TestdataSolution>> solvers = new ArrayList<>();
    List<TestdataSolution> solutions = new ArrayList<>();

    // Create multiple solvers
    for (int i = 0; i < 5; i++) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount("2");

      SolverFactory<TestdataSolution> solverFactory = SolverFactory.create(solverConfig);
      Solver<TestdataSolution> solver = solverFactory.buildSolver();
      solvers.add(solver);

      TestdataSolution solution = createTestSolution(20, 4);
      solutions.add(solution);
    }

    // Solve all problems
    for (int i = 0; i < solvers.size(); i++) {
      solutions.set(i, solvers.get(i).solve(solutions.get(i)));
    }

    long memoryAfter = getCurrentMemoryUsage();

    // Verify all solutions are valid
    for (TestdataSolution solution : solutions) {
      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    // Memory increase should be reasonable
    long memoryIncrease = memoryAfter - memoryBefore;
    assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024);

    // Clean up solvers
    for (Solver<TestdataSolution> solver : solvers) {
      // Note: Solver doesn't have a close() method in this implementation
      // but we can still test memory management
    }
  }

  @Test
  void testThreadPoolResourceManagement() {
    // Test that thread pools are properly managed
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("4");

    TestdataSolution solution = createTestSolution(40, 8);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Give time for thread pool cleanup
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Thread count should return to reasonable levels
    long finalThreadCount = threadBean.getThreadCount();
    assertThat(finalThreadCount - initialThreadCount).isLessThan(5);
  }

  @Test
  void testMemoryPressureHandling() {
    // Test that the system handles memory pressure gracefully
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(200); // Large buffer

    TestdataSolution solution = createTestSolution(100, 25);

    // Force garbage collection before test
    System.gc();
    System.runFinalization();

    long memoryBefore = getCurrentMemoryUsage();
    solution = solveWithConfig(solverConfig, solution);
    long memoryAfter = getCurrentMemoryUsage();

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Memory usage should be reasonable even with large buffer
    long memoryIncrease = memoryAfter - memoryBefore;
    assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024);
  }

  @Test
  void testConcurrentSolvingResourceManagement() throws InterruptedException {
    // Test resource management when solving multiple problems concurrently
    int numConcurrentSolvers = 3;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch finishLatch = new CountDownLatch(numConcurrentSolvers);
    AtomicLong totalMemoryIncrease = new AtomicLong(0);

    ExecutorService executor = Executors.newFixedThreadPool(numConcurrentSolvers);

    long memoryBefore = getCurrentMemoryUsage();

    // Start multiple concurrent solvers
    for (int i = 0; i < numConcurrentSolvers; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await(); // Wait for all threads to be ready

              SolverConfig solverConfig = createSolverConfig();
              solverConfig.setMoveThreadCount("2");

              TestdataSolution solution = createTestSolution(30, 6);
              solution = solveWithConfig(solverConfig, solution);

              assertThat(solution).isNotNull();
              assertThat(solution.getScore().isSolutionInitialized()).isTrue();

            } catch (Exception e) {
              throw new RuntimeException(e);
            } finally {
              finishLatch.countDown();
            }
          });
    }

    // Start all solvers simultaneously
    startLatch.countDown();

    // Wait for all solvers to complete
    boolean allCompleted = finishLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(allCompleted).isTrue();

    long memoryAfter = getCurrentMemoryUsage();
    long memoryIncrease = memoryAfter - memoryBefore;

    // Memory increase should be reasonable for concurrent solving
    assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024);
  }

  @Test
  void testResourceManagementWithShortTermination() {
    // Test resource management with short termination times
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig.setSecondsSpentLimit(1L); // Very short termination
    solverConfig.setTerminationConfig(terminationConfig);

    TestdataSolution solution = createTestSolution(50, 10);

    long memoryBefore = getCurrentMemoryUsage();
    solution = solveWithConfig(solverConfig, solution);
    long memoryAfter = getCurrentMemoryUsage();

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Memory usage should be reasonable even with short termination
    long memoryIncrease = memoryAfter - memoryBefore;
    assertThat(memoryIncrease).isLessThan(50 * 1024 * 1024);
  }

  private long getCurrentMemoryUsage() {
    return memoryBean.getHeapMemoryUsage().getUsed();
  }

  private SolverConfig createSolverConfig() {
    SolverConfig solverConfig = new SolverConfig();

    // Configure basic solver settings
    solverConfig.setSolutionClass(TestdataSolution.class);
    solverConfig.setEntityClassList(List.of(TestdataEntity.class));

    // Configure constraint provider
    solverConfig.withConstraintProviderClass(TestdataConstraintProvider.class);

    // Configure termination
    TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig.setSecondsSpentLimit(10L);
    solverConfig.setTerminationConfig(terminationConfig);

    return solverConfig;
  }

  private TestdataSolution solveWithConfig(SolverConfig solverConfig, TestdataSolution solution) {
    SolverFactory<TestdataSolution> solverFactory = SolverFactory.create(solverConfig);
    Solver<TestdataSolution> solver = solverFactory.buildSolver();

    return solver.solve(solution);
  }

  private TestdataSolution createTestSolution(int entityCount, int valueCount) {
    TestdataSolution solution = new TestdataSolution();
    solution.setEntityList(new ArrayList<>());
    solution.setValueList(new ArrayList<>());

    for (int i = 0; i < valueCount; i++) {
      TestdataValue value = new TestdataValue("value-" + i);
      solution.getValueList().add(value);
    }

    for (int i = 0; i < entityCount; i++) {
      TestdataEntity entity = new TestdataEntity("entity-" + i);
      entity.setValue(solution.getValueList().get(i % valueCount));
      solution.getEntityList().add(entity);
    }

    return solution;
  }

  /** Simple constraint provider for testing. */
  public static class TestdataConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
      return new Constraint[] {
        constraintFactory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE)
            .asConstraint("Maximize entities")
      };
    }
  }
}
