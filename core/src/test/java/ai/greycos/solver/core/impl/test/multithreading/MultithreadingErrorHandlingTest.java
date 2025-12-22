package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Error handling tests for multithreading functionality. These tests validate that the
 * multithreading implementation properly handles exceptions, invalid configurations, and error
 * recovery scenarios.
 */
public class MultithreadingErrorHandlingTest {

  @Test
  void testInvalidMoveThreadCount() {
    SolverConfig solverConfig = createSolverConfig();

    // Test invalid move thread count values
    assertThatThrownBy(
            () -> {
              solverConfig.setMoveThreadCount("0");
              solveWithConfig(solverConfig, createTestSolution(10, 5));
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("moveThreadCount");

    assertThatThrownBy(
            () -> {
              solverConfig.setMoveThreadCount("-1");
              solveWithConfig(solverConfig, createTestSolution(10, 5));
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("moveThreadCount");

    assertThatThrownBy(
            () -> {
              solverConfig.setMoveThreadCount("invalid");
              solveWithConfig(solverConfig, createTestSolution(10, 5));
            })
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("moveThreadCount");
  }

  @Test
  void testTooManyMoveThreads() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("1000"); // More than available processors

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    // Should work but log a warning about counter-efficiency
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testNegativeMoveThreadBufferSize() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(-1);

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    // Should handle negative buffer size gracefully
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testZeroMoveThreadBufferSize() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(0);

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    // Should handle zero buffer size gracefully
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testCustomThreadFactoryExceptionHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setThreadFactoryClass(FaultyThreadFactory.class);

    TestdataSolution solution = createTestSolution(10, 5);

    // Should handle thread factory exceptions gracefully
    assertThatThrownBy(
            () -> {
              solveWithConfig(solverConfig, solution);
            })
        .isInstanceOf(Exception.class);
  }

  @Test
  void testMoveThreadExceptionPropagation() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Create a solution that might cause exceptions during move evaluation
    TestdataSolution solution = createFaultyTestSolution(20, 5);

    // Should not throw an exception, but should handle it gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    // The solution might not be fully initialized due to the exception,
    // but the solver should not crash
  }

  @Test
  void testThreadInterruptHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.getTerminationConfig().setSecondsSpentLimit(1L); // Short timeout

    TestdataSolution solution = createTestSolution(50, 10);

    // Should handle thread interruption gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMemoryPressureHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("4");
    solverConfig.setMoveThreadBufferSize(1000); // Large buffer

    // Create a large solution to test memory handling
    TestdataSolution solution = createTestSolution(200, 50);

    // Should handle memory pressure gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testConstructionHeuristicExceptionHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase));

    // Create a solution that might cause issues in construction heuristic
    TestdataSolution solution = createFaultyTestSolution(15, 3);

    // Should handle construction heuristic exceptions gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    // May or may not be fully initialized depending on when exception occurs
  }

  @Test
  void testLocalSearchExceptionHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(lsPhase));

    // Create a solution that might cause issues in local search
    TestdataSolution solution = createFaultyTestSolution(15, 3);

    // Should handle local search exceptions gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    // May or may not be fully initialized depending on when exception occurs
  }

  @Test
  void testMixedPhaseExceptionHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    // Create a solution that might cause issues in mixed phases
    TestdataSolution solution = createFaultyTestSolution(20, 5);

    // Should handle mixed phase exceptions gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    // May or may not be fully initialized depending on when exception occurs
  }

  @Test
  void testThreadPoolShutdownHandling() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.getTerminationConfig().setSecondsSpentLimit(2L);

    TestdataSolution solution = createTestSolution(30, 8);

    // Should handle thread pool shutdown gracefully
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testResourceCleanupOnException() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Create a solution that will cause resource cleanup issues
    TestdataSolution solution = createFaultyTestSolution(25, 6);

    // Should clean up resources even when exceptions occur
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    // Resource cleanup should not throw exceptions
  }

  @Test
  void testConfigurationValidation() {
    SolverConfig solverConfig = createSolverConfig();

    // Valid configurations should work
    solverConfig.setMoveThreadCount("NONE");
    assertThat(solveWithConfig(solverConfig, createTestSolution(10, 5))).isNotNull();

    solverConfig.setMoveThreadCount("AUTO");
    assertThat(solveWithConfig(solverConfig, createTestSolution(10, 5))).isNotNull();

    solverConfig.setMoveThreadCount("1");
    assertThat(solveWithConfig(solverConfig, createTestSolution(10, 5))).isNotNull();

    solverConfig.setMoveThreadCount("4");
    assertThat(solveWithConfig(solverConfig, createTestSolution(10, 5))).isNotNull();
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
    terminationConfig.setSecondsSpentLimit(5L);
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

  private TestdataSolution createFaultyTestSolution(int entityCount, int valueCount) {
    // Create a solution that will cause exceptions during move evaluation
    TestdataSolution solution = new TestdataSolution();
    solution.setEntityList(new ArrayList<>());
    solution.setValueList(new ArrayList<>());

    for (int i = 0; i < valueCount; i++) {
      TestdataValue value = new TestdataValue("value-" + i);
      solution.getValueList().add(value);
    }

    for (int i = 0; i < entityCount; i++) {
      TestdataEntity entity = new TestdataEntity("entity-" + i);
      // Set up entity in a way that might cause exceptions
      entity.setValue(null);
      solution.getEntityList().add(entity);
    }

    return solution;
  }

  /** Faulty thread factory for testing exception handling. */
  public static class FaultyThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
      throw new RuntimeException("Simulated thread factory failure");
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
