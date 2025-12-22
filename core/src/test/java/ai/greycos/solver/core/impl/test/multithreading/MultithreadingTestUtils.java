package ai.greycos.solver.core.impl.test.multithreading;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;

/**
 * Test utilities and helper classes for multithreading tests. Provides common functionality for
 * creating test configurations, solutions, and utilities.
 */
public class MultithreadingTestUtils {

  private MultithreadingTestUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a basic solver configuration for testing.
   *
   * @return a configured SolverConfig
   */
  public static SolverConfig createBasicSolverConfig() {
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

  /**
   * Creates a solver configuration with multithreading enabled.
   *
   * @param moveThreadCount the number of move threads
   * @param moveThreadBufferSize the size of the move thread buffer
   * @return a configured SolverConfig with multithreading
   */
  public static SolverConfig createMultithreadedSolverConfig(
      int moveThreadCount, int moveThreadBufferSize) {
    SolverConfig solverConfig = createBasicSolverConfig();
    solverConfig.setMoveThreadCount(String.valueOf(moveThreadCount));
    solverConfig.setMoveThreadBufferSize(moveThreadBufferSize);
    return solverConfig;
  }

  /**
   * Creates a solver configuration with AUTO multithreading.
   *
   * @return a configured SolverConfig with AUTO multithreading
   */
  public static SolverConfig createAutoMultithreadedSolverConfig() {
    SolverConfig solverConfig = createBasicSolverConfig();
    solverConfig.setMoveThreadCount("AUTO");
    return solverConfig;
  }

  /**
   * Creates a solver configuration with single-threaded solving.
   *
   * @return a configured SolverConfig with single-threaded solving
   */
  public static SolverConfig createSingleThreadedSolverConfig() {
    SolverConfig solverConfig = createBasicSolverConfig();
    solverConfig.setMoveThreadCount("NONE");
    return solverConfig;
  }

  /**
   * Creates a test solution with the specified number of entities and values.
   *
   * @param entityCount the number of entities
   * @param valueCount the number of values
   * @return a TestdataSolution
   */
  public static TestdataSolution createTestSolution(int entityCount, int valueCount) {
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

  /**
   * Creates a large test solution for performance testing.
   *
   * @return a large TestdataSolution
   */
  public static TestdataSolution createLargeTestSolution() {
    return createTestSolution(200, 50);
  }

  /**
   * Creates a medium test solution for integration testing.
   *
   * @return a medium TestdataSolution
   */
  public static TestdataSolution createMediumTestSolution() {
    return createTestSolution(50, 10);
  }

  /**
   * Creates a small test solution for unit testing.
   *
   * @return a small TestdataSolution
   */
  public static TestdataSolution createSmallTestSolution() {
    return createTestSolution(10, 5);
  }

  /**
   * Solves a solution with the given configuration.
   *
   * @param solverConfig the solver configuration
   * @param solution the solution to solve
   * @return the solved solution
   */
  public static TestdataSolution solveWithConfig(
      SolverConfig solverConfig, TestdataSolution solution) {
    SolverFactory<TestdataSolution> solverFactory = SolverFactory.create(solverConfig);
    Solver<TestdataSolution> solver = solverFactory.buildSolver();

    return solver.solve(solution);
  }

  /**
   * Creates a solver configuration with mixed phases and multithreading.
   *
   * @param chThreadCount construction heuristic thread count
   * @param lsThreadCount local search thread count
   * @return a configured SolverConfig with mixed phases
   */
  public static SolverConfig createMixedPhaseSolverConfig(int chThreadCount, int lsThreadCount) {
    SolverConfig solverConfig = createBasicSolverConfig();

    // Construction heuristic phase
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    chPhase.setMoveThreadCount(String.valueOf(chThreadCount));

    // Local search phase
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    lsPhase.setMoveThreadCount(String.valueOf(lsThreadCount));

    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    return solverConfig;
  }

  /**
   * Creates a solver configuration with only construction heuristic phase.
   *
   * @param threadCount the number of threads for construction heuristic
   * @return a configured SolverConfig with only CH phase
   */
  public static SolverConfig createConstructionHeuristicOnlySolverConfig(int threadCount) {
    SolverConfig solverConfig = createBasicSolverConfig();

    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    chPhase.setMoveThreadCount(String.valueOf(threadCount));

    solverConfig.setPhaseConfigList(List.of(chPhase));

    return solverConfig;
  }

  /**
   * Creates a solver configuration with only local search phase.
   *
   * @param threadCount the number of threads for local search
   * @return a configured SolverConfig with only LS phase
   */
  public static SolverConfig createLocalSearchOnlySolverConfig(int threadCount) {
    SolverConfig solverConfig = createBasicSolverConfig();

    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    lsPhase.setMoveThreadCount(String.valueOf(threadCount));

    solverConfig.setPhaseConfigList(List.of(lsPhase));

    return solverConfig;
  }

  /**
   * Creates a custom thread factory for testing that tracks thread creation.
   *
   * @return a TestThreadFactory
   */
  public static TestThreadFactory createTestThreadFactory() {
    return new TestThreadFactory();
  }

  /**
   * Measures the execution time of a solver operation.
   *
   * @param solverConfig the solver configuration
   * @param solution the solution to solve
   * @return the execution time in nanoseconds
   */
  public static long measureSolvingTime(SolverConfig solverConfig, TestdataSolution solution) {
    long startTime = System.nanoTime();
    solveWithConfig(solverConfig, solution);
    return System.nanoTime() - startTime;
  }

  /**
   * Calculates the speedup between single-threaded and multi-threaded solving.
   *
   * @param solution the solution to solve
   * @param threadCount the number of threads to use
   * @return the speedup ratio
   */
  public static double calculateSpeedup(TestdataSolution solution, int threadCount) {
    SolverConfig singleThreadedConfig = createSingleThreadedSolverConfig();
    SolverConfig multiThreadedConfig =
        createMultithreadedSolverConfig(threadCount, threadCount * 10);

    long singleTime = measureSolvingTime(singleThreadedConfig, copySolution(solution));
    long multiTime = measureSolvingTime(multiThreadedConfig, copySolution(solution));

    return (double) singleTime / multiTime;
  }

  /**
   * Copies a test solution.
   *
   * @param original the original solution
   * @return a copy of the solution
   */
  public static TestdataSolution copySolution(TestdataSolution original) {
    TestdataSolution copy = new TestdataSolution();
    copy.setEntityList(new ArrayList<>(original.getEntityList()));
    copy.setValueList(new ArrayList<>(original.getValueList()));
    return copy;
  }

  /** Custom thread factory for testing that tracks thread creation. */
  public static class TestThreadFactory implements ThreadFactory {
    private static final ThreadLocal<Boolean> called = new ThreadLocal<>();
    private static final AtomicInteger threadCount = new AtomicInteger(0);

    @Override
    public Thread newThread(Runnable r) {
      called.set(true);
      int threadNumber = threadCount.incrementAndGet();
      Thread thread = new Thread(r, "TestThread-" + threadNumber);
      return thread;
    }

    public static boolean hasBeenCalled() {
      return Boolean.TRUE.equals(called.get());
    }

    public static void reset() {
      called.remove();
      threadCount.set(0);
    }

    public static int getThreadCount() {
      return threadCount.get();
    }
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

  /** Performance test result holder. */
  public static class PerformanceTestResult {
    private final long singleThreadedTime;
    private final long multiThreadedTime;
    private final double speedup;
    private final TestdataSolution singleThreadedSolution;
    private final TestdataSolution multiThreadedSolution;

    public PerformanceTestResult(
        long singleThreadedTime,
        long multiThreadedTime,
        double speedup,
        TestdataSolution singleThreadedSolution,
        TestdataSolution multiThreadedSolution) {
      this.singleThreadedTime = singleThreadedTime;
      this.multiThreadedTime = multiThreadedTime;
      this.speedup = speedup;
      this.singleThreadedSolution = singleThreadedSolution;
      this.multiThreadedSolution = multiThreadedSolution;
    }

    public long getSingleThreadedTime() {
      return singleThreadedTime;
    }

    public long getMultiThreadedTime() {
      return multiThreadedTime;
    }

    public double getSpeedup() {
      return speedup;
    }

    public TestdataSolution getSingleThreadedSolution() {
      return singleThreadedSolution;
    }

    public TestdataSolution getMultiThreadedSolution() {
      return multiThreadedSolution;
    }

    @Override
    public String toString() {
      return String.format(
          "PerformanceTestResult{singleThreadedTime=%d, multiThreadedTime=%d, speedup=%.2f}",
          singleThreadedTime, multiThreadedTime, speedup);
    }
  }

  /** Memory usage monitor for testing. */
  public static class MemoryMonitor {
    private final Runtime runtime = Runtime.getRuntime();
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private long peakMemoryUsage = 0;

    public void startMonitoring() {
      monitoring.set(true);
      peakMemoryUsage = getCurrentMemoryUsage();
    }

    public void stopMonitoring() {
      monitoring.set(false);
    }

    public long getPeakMemoryUsage() {
      if (monitoring.get()) {
        long currentUsage = getCurrentMemoryUsage();
        if (currentUsage > peakMemoryUsage) {
          peakMemoryUsage = currentUsage;
        }
      }
      return peakMemoryUsage;
    }

    public long getCurrentMemoryUsage() {
      return runtime.totalMemory() - runtime.freeMemory();
    }

    public void reset() {
      peakMemoryUsage = 0;
      monitoring.set(false);
    }
  }
}
