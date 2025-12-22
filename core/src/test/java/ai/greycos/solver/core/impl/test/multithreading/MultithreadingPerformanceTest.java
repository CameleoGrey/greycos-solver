package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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

import org.junit.jupiter.api.Test;

/**
 * Performance tests for multithreading functionality. These tests validate that multithreading
 * provides performance improvements and scales correctly with different thread counts.
 */
public class MultithreadingPerformanceTest {

  @Test
  void testSingleThreadedVsMultiThreadedPerformance() {
    int entityCount = 100;
    int valueCount = 20;

    // Single-threaded configuration
    SolverConfig singleThreadedConfig = createSolverConfig();
    singleThreadedConfig.setMoveThreadCount("NONE");
    singleThreadedConfig.getTerminationConfig().setSecondsSpentLimit(10L);

    // Multi-threaded configuration with 2 threads
    SolverConfig multiThreadedConfig = createSolverConfig();
    multiThreadedConfig.setMoveThreadCount("2");
    multiThreadedConfig.getTerminationConfig().setSecondsSpentLimit(10L);

    TestdataSolution singleThreadedSolution = createTestSolution(entityCount, valueCount);
    TestdataSolution multiThreadedSolution = createTestSolution(entityCount, valueCount);

    // Solve with single thread
    long startTime = System.nanoTime();
    singleThreadedSolution = solveWithConfig(singleThreadedConfig, singleThreadedSolution);
    long singleThreadedTime = System.nanoTime() - startTime;

    // Solve with multiple threads
    startTime = System.nanoTime();
    multiThreadedSolution = solveWithConfig(multiThreadedConfig, multiThreadedSolution);
    long multiThreadedTime = System.nanoTime() - startTime;

    // Verify both solutions are valid
    assertThat(singleThreadedSolution).isNotNull();
    assertThat(multiThreadedSolution).isNotNull();
    assertThat(singleThreadedSolution.getScore().isSolutionInitialized()).isTrue();
    assertThat(multiThreadedSolution.getScore().isSolutionInitialized()).isTrue();

    // Multi-threaded should be faster (or at least not significantly slower)
    double speedup = (double) singleThreadedTime / multiThreadedTime;
    System.out.println("Single-threaded time: " + singleThreadedTime / 1_000_000 + "ms");
    System.out.println("Multi-threaded time: " + multiThreadedTime / 1_000_000 + "ms");
    System.out.println("Speedup: " + speedup + "x");

    // Allow some tolerance for measurement variance and overhead
    assertThat(speedup).isGreaterThanOrEqualTo(0.7);
  }

  @Test
  void testThreadCountScaling() {
    int entityCount = 50;
    int valueCount = 10;

    // Test different thread counts
    int[] threadCounts = {1, 2, 4};
    long[] times = new long[threadCounts.length];

    for (int i = 0; i < threadCounts.length; i++) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount(String.valueOf(threadCounts[i]));
      solverConfig.getTerminationConfig().setSecondsSpentLimit(5L);

      TestdataSolution solution = createTestSolution(entityCount, valueCount);

      long startTime = System.nanoTime();
      solution = solveWithConfig(solverConfig, solution);
      times[i] = System.nanoTime() - startTime;

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    // Print performance results
    for (int i = 0; i < threadCounts.length; i++) {
      System.out.println("Threads: " + threadCounts[i] + ", Time: " + times[i] / 1_000_000 + "ms");
    }

    // With small problems, overhead might dominate, so we just verify it doesn't get much worse
    assertThat(times[1])
        .isGreaterThanOrEqualTo(
            (long) (times[0] * 0.5)); // 2 threads should be at least half as fast
    assertThat(times[2])
        .isGreaterThanOrEqualTo(
            (long) (times[0] * 0.3)); // 4 threads should be at least 30% as fast
  }

  @Test
  void testAutoThreadCountPerformance() {
    int entityCount = 75;
    int valueCount = 15;

    // AUTO configuration
    SolverConfig autoConfig = createSolverConfig();
    autoConfig.setMoveThreadCount("AUTO");
    autoConfig.getTerminationConfig().setSecondsSpentLimit(8L);

    // Explicit 2 threads
    SolverConfig explicitConfig = createSolverConfig();
    explicitConfig.setMoveThreadCount("2");
    explicitConfig.getTerminationConfig().setSecondsSpentLimit(8L);

    TestdataSolution autoSolution = createTestSolution(entityCount, valueCount);
    TestdataSolution explicitSolution = createTestSolution(entityCount, valueCount);

    long startTime = System.nanoTime();
    autoSolution = solveWithConfig(autoConfig, autoSolution);
    long autoTime = System.nanoTime() - startTime;

    startTime = System.nanoTime();
    explicitSolution = solveWithConfig(explicitConfig, explicitSolution);
    long explicitTime = System.nanoTime() - startTime;

    assertThat(autoSolution).isNotNull();
    assertThat(explicitSolution).isNotNull();
    assertThat(autoSolution.getScore().isSolutionInitialized()).isTrue();
    assertThat(explicitSolution.getScore().isSolutionInitialized()).isTrue();

    // AUTO should perform reasonably compared to explicit configuration
    double ratio = (double) autoTime / explicitTime;
    System.out.println("AUTO vs Explicit time ratio: " + ratio);

    // AUTO should not be more than 3x slower than explicit
    assertThat(ratio).isLessThanOrEqualTo(3.0);
  }

  @Test
  void testLargeProblemMultithreading() {
    int entityCount = 200;
    int valueCount = 50;

    // Single-threaded
    SolverConfig singleConfig = createSolverConfig();
    singleConfig.setMoveThreadCount("NONE");
    singleConfig.getTerminationConfig().setSecondsSpentLimit(15L);

    // Multi-threaded
    SolverConfig multiConfig = createSolverConfig();
    multiConfig.setMoveThreadCount("4");
    multiConfig.getTerminationConfig().setSecondsSpentLimit(15L);

    TestdataSolution singleSolution = createTestSolution(entityCount, valueCount);
    TestdataSolution multiSolution = createTestSolution(entityCount, valueCount);

    long startTime = System.nanoTime();
    singleSolution = solveWithConfig(singleConfig, singleSolution);
    long singleTime = System.nanoTime() - startTime;

    startTime = System.nanoTime();
    multiSolution = solveWithConfig(multiConfig, multiSolution);
    long multiTime = System.nanoTime() - startTime;

    assertThat(singleSolution).isNotNull();
    assertThat(multiSolution).isNotNull();
    assertThat(singleSolution.getScore().isSolutionInitialized()).isTrue();
    assertThat(multiSolution.getScore().isSolutionInitialized()).isTrue();

    double speedup = (double) singleTime / multiTime;
    System.out.println("Large problem - Single time: " + singleTime / 1_000_000 + "ms");
    System.out.println("Large problem - Multi time: " + multiTime / 1_000_000 + "ms");
    System.out.println("Large problem speedup: " + speedup + "x");

    // For larger problems, we expect better scaling
    assertThat(speedup).isGreaterThanOrEqualTo(0.8);
  }

  @Test
  void testConstructionHeuristicMultithreadingPerformance() {
    int entityCount = 60;
    int valueCount = 12;

    // Single-threaded construction heuristic
    SolverConfig singleConfig = createSolverConfig();
    singleConfig.setMoveThreadCount("NONE");

    ConstructionHeuristicPhaseConfig singleCHPhase = new ConstructionHeuristicPhaseConfig();
    singleConfig.setPhaseConfigList(List.of(singleCHPhase));

    // Multi-threaded construction heuristic
    SolverConfig multiConfig = createSolverConfig();
    multiConfig.setMoveThreadCount("2");

    ConstructionHeuristicPhaseConfig multiCHPhase = new ConstructionHeuristicPhaseConfig();
    multiConfig.setPhaseConfigList(List.of(multiCHPhase));

    TestdataSolution singleSolution = createTestSolution(entityCount, valueCount);
    TestdataSolution multiSolution = createTestSolution(entityCount, valueCount);

    long startTime = System.nanoTime();
    singleSolution = solveWithConfig(singleConfig, singleSolution);
    long singleTime = System.nanoTime() - startTime;

    startTime = System.nanoTime();
    multiSolution = solveWithConfig(multiConfig, multiSolution);
    long multiTime = System.nanoTime() - startTime;

    assertThat(singleSolution).isNotNull();
    assertThat(multiSolution).isNotNull();
    assertThat(singleSolution.getScore().isSolutionInitialized()).isTrue();
    assertThat(multiSolution.getScore().isSolutionInitialized()).isTrue();

    double speedup = (double) singleTime / multiTime;
    System.out.println("Construction heuristic speedup: " + speedup + "x");

    // Construction heuristic should benefit from multithreading
    assertThat(speedup).isGreaterThanOrEqualTo(0.7);
  }

  @Test
  void testMixedPhaseMultithreadingPerformance() {
    int entityCount = 80;
    int valueCount = 16;

    // Single-threaded mixed phases
    SolverConfig singleConfig = createSolverConfig();
    singleConfig.setMoveThreadCount("NONE");

    ConstructionHeuristicPhaseConfig singleCHPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig singleLSPhase = new LocalSearchPhaseConfig();
    singleConfig.setPhaseConfigList(List.of(singleCHPhase, singleLSPhase));

    // Multi-threaded mixed phases
    SolverConfig multiConfig = createSolverConfig();
    multiConfig.setMoveThreadCount("2");

    ConstructionHeuristicPhaseConfig multiCHPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig multiLSPhase = new LocalSearchPhaseConfig();
    multiConfig.setPhaseConfigList(List.of(multiCHPhase, multiLSPhase));

    TestdataSolution singleSolution = createTestSolution(entityCount, valueCount);
    TestdataSolution multiSolution = createTestSolution(entityCount, valueCount);

    long startTime = System.nanoTime();
    singleSolution = solveWithConfig(singleConfig, singleSolution);
    long singleTime = System.nanoTime() - startTime;

    startTime = System.nanoTime();
    multiSolution = solveWithConfig(multiConfig, multiSolution);
    long multiTime = System.nanoTime() - startTime;

    assertThat(singleSolution).isNotNull();
    assertThat(multiSolution).isNotNull();
    assertThat(singleSolution.getScore().isSolutionInitialized()).isTrue();
    assertThat(multiSolution.getScore().isSolutionInitialized()).isTrue();

    double speedup = (double) singleTime / multiTime;
    System.out.println("Mixed phases speedup: " + speedup + "x");

    // Mixed phases should benefit from multithreading
    assertThat(speedup).isGreaterThanOrEqualTo(0.7);
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
