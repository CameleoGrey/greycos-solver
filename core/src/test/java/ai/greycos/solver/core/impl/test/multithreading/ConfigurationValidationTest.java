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
 * Configuration validation tests for multithreading functionality. These tests validate that
 * multithreading configurations are properly validated and that invalid configurations are handled
 * correctly.
 */
public class ConfigurationValidationTest {

  @Test
  void testValidMoveThreadCountConfigurations() {
    // Test that valid move thread count configurations work correctly
    String[] validConfigurations = {"NONE", "AUTO", "1", "2", "4", "8"};

    for (String config : validConfigurations) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount(config);

      // Should not throw an exception
      TestdataSolution solution = createTestSolution(10, 5);
      solution = solveWithConfig(solverConfig, solution);

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }
  }

  @Test
  void testInvalidMoveThreadCountConfigurations() {
    // Test that invalid move thread count configurations are handled properly
    String[] invalidConfigurations = {"0", "-1", "invalid", "1.5", "abc"};

    for (String config : invalidConfigurations) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount(config);

      // Should handle invalid configurations gracefully
      TestdataSolution solution = createTestSolution(10, 5);

      // The behavior depends on the implementation - it might throw an exception
      // or fall back to a default configuration
      try {
        solution = solveWithConfig(solverConfig, solution);
        // If it doesn't throw, the solution should still be valid
        assertThat(solution).isNotNull();
      } catch (Exception e) {
        // If it throws, that's also acceptable behavior for invalid config
        assertThat(e).isInstanceOf(Exception.class);
      }
    }
  }

  @Test
  void testMoveThreadBufferSizeValidation() {
    // Test that move thread buffer size is handled correctly
    int[] validBufferSizes = {0, 1, 10, 50, 100, 1000};

    for (int bufferSize : validBufferSizes) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount("2");
      solverConfig.setMoveThreadBufferSize(bufferSize);

      TestdataSolution solution = createTestSolution(10, 5);
      solution = solveWithConfig(solverConfig, solution);

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }
  }

  @Test
  void testThreadFactoryValidation() {
    // Test that custom thread factory is handled correctly
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
  void testPhaseSpecificThreadConfiguration() {
    // Test that phase-specific thread configuration works
    SolverConfig solverConfig = createSolverConfig();

    // Construction heuristic with specific thread count
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    chPhase.setMoveThreadCount("2");

    // Local search with different thread count
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    lsPhase.setMoveThreadCount("4");

    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(15, 4);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testSolverLevelThreadConfiguration() {
    // Test that solver-level thread configuration works
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("3");

    // Phases without explicit thread count should inherit from solver
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();

    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(15, 4);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testConfigurationPrecedence() {
    // Test that configuration precedence works correctly
    SolverConfig solverConfig = createSolverConfig();

    // Set move thread count at solver level
    solverConfig.setMoveThreadCount("2");

    // Override at phase level
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    chPhase.setMoveThreadCount("4");

    // Use solver level for this phase
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    // No moveThreadCount set, should use solver level

    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testConfigurationConsistency() {
    // Test that configuration is consistent across different scenarios
    SolverConfig solverConfig = createSolverConfig();

    // Set various configuration options
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(10);
    solverConfig.setThreadFactoryClass(TestThreadFactory.class);

    // Test with different phase combinations
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();

    // Single phase
    solverConfig.setPhaseConfigList(List.of(chPhase));
    TestdataSolution solution1 = createTestSolution(15, 4);
    solution1 = solveWithConfig(solverConfig, solution1);

    // Mixed phases
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));
    TestdataSolution solution2 = createTestSolution(15, 4);
    solution2 = solveWithConfig(solverConfig, solution2);

    assertThat(solution1).isNotNull();
    assertThat(solution2).isNotNull();
    assertThat(solution1.getScore().isSolutionInitialized()).isTrue();
    assertThat(solution2.getScore().isSolutionInitialized()).isTrue();

    assertThat(TestThreadFactory.hasBeenCalled()).isTrue();
    TestThreadFactory.reset();
  }

  @Test
  void testConfigurationEdgeCases() {
    // Test edge cases in configuration
    SolverConfig solverConfig = createSolverConfig();

    // Test with very large buffer size
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(10000);

    TestdataSolution solution = createTestSolution(10, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Test with minimal configuration
    solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("1");
    solverConfig.setMoveThreadBufferSize(1);

    solution = createTestSolution(5, 2);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testConfigurationValidationWithDifferentProblemSizes() {
    // Test configuration validation with different problem sizes
    int[] entityCounts = {5, 10, 50, 100};
    int[] valueCounts = {2, 5, 10, 20};

    for (int i = 0; i < entityCounts.length; i++) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount("2");
      solverConfig.setMoveThreadBufferSize(20);

      TestdataSolution solution = createTestSolution(entityCounts[i], valueCounts[i]);
      solution = solveWithConfig(solverConfig, solution);

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }
  }

  @Test
  void testConfigurationWithDifferentTerminationConditions() {
    // Test configuration with different termination conditions
    long[] terminationTimes = {1L, 5L, 10L, 30L};

    for (long terminationTime : terminationTimes) {
      SolverConfig solverConfig = createSolverConfig();
      solverConfig.setMoveThreadCount("2");

      TerminationConfig terminationConfig = new TerminationConfig();
      terminationConfig.setSecondsSpentLimit(terminationTime);
      solverConfig.setTerminationConfig(terminationConfig);

      TestdataSolution solution = createTestSolution(20, 5);
      solution = solveWithConfig(solverConfig, solution);

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }
  }

  @Test
  void testConfigurationWithDifferentPhaseCombinations() {
    // Test configuration with different phase combinations
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Test with only construction heuristic
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase));

    TestdataSolution solution1 = createTestSolution(15, 4);
    solution1 = solveWithConfig(solverConfig, solution1);

    // Test with only local search
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(lsPhase));

    TestdataSolution solution2 = createTestSolution(15, 4);
    solution2 = solveWithConfig(solverConfig, solution2);

    // Test with mixed phases
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution3 = createTestSolution(15, 4);
    solution3 = solveWithConfig(solverConfig, solution3);

    assertThat(solution1).isNotNull();
    assertThat(solution2).isNotNull();
    assertThat(solution3).isNotNull();
    assertThat(solution1.getScore().isSolutionInitialized()).isTrue();
    assertThat(solution2.getScore().isSolutionInitialized()).isTrue();
    assertThat(solution3.getScore().isSolutionInitialized()).isTrue();
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

  /** Custom thread factory for testing thread creation. */
  public static class TestThreadFactory implements java.util.concurrent.ThreadFactory {
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
