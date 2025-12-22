package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

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
 * Comprehensive test for multithreading functionality in the greycos solver. Tests move thread
 * count configurations and validates that multithreading works correctly.
 */
public class MultithreadingTest {

  @Test
  void testMoveThreadCountAuto() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("AUTO");

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMoveThreadCountExplicit() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMoveThreadCountNone() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("NONE");

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testCustomThreadFactory() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setThreadFactoryClass(TestThreadFactory.class);

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    assertThat(TestThreadFactory.hasBeenCalled()).isTrue();
    TestThreadFactory.reset();
  }

  @Test
  void testMoveThreadBufferSize() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(20);

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMixedPhasesWithDifferentThreadCounts() {
    SolverConfig solverConfig = createSolverConfig();

    // Set move thread count at solver level (applies to all phases)
    solverConfig.setMoveThreadCount("2");

    // Construction heuristic phase
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();

    // Local search phase
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();

    solverConfig.setPhaseConfigList(
        List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testPerformanceComparison() {
    int entityCount = 50;
    int valueCount = 10;

    // Single-threaded configuration
    SolverConfig singleThreadedConfig = createSolverConfig();
    singleThreadedConfig.setMoveThreadCount("NONE");
    singleThreadedConfig.getTerminationConfig().setSecondsSpentLimit(5L);

    // Multi-threaded configuration
    SolverConfig multiThreadedConfig = createSolverConfig();
    multiThreadedConfig.setMoveThreadCount("AUTO");
    multiThreadedConfig.getTerminationConfig().setSecondsSpentLimit(5L);

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
    System.out.println("Speedup: " + speedup + "x");

    // Allow some tolerance for measurement variance
    assertThat(speedup).isGreaterThanOrEqualTo(0.8);
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

  /** Custom thread factory for testing thread creation. */
  public static class TestThreadFactory implements ThreadFactory {
    private static final ThreadLocal<Boolean> called = new ThreadLocal<>();

    @Override
    public Thread newThread(Runnable r) {
      called.set(true);
      Thread thread = new Thread(r);
      thread.setName("TestThread-" + thread.getId());
      return thread;
    }

    public static boolean hasBeenCalled() {
      return Boolean.TRUE.equals(called.get());
    }

    public static void reset() {
      called.remove();
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
}
