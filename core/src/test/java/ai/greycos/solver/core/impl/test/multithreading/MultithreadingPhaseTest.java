package ai.greycos.solver.core.impl.test.multithreading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

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
 * Integration tests for multithreading functionality with different solver phases. These tests
 * validate that multithreading works correctly with various phase configurations.
 */
public class MultithreadingPhaseTest {

  @Test
  void testMultiThreadedConstructionHeuristic() {
    SolverConfig solverConfig = createSolverConfig();

    // Configure construction heuristic with multithreading
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();
    constructionHeuristicPhaseConfig.setMoveThreadCount("2");

    solverConfig.setPhaseConfigList(List.of(constructionHeuristicPhaseConfig));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMultiThreadedLocalSearch() {
    SolverConfig solverConfig = createSolverConfig();

    // Configure local search with multithreading
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
    localSearchPhaseConfig.setMoveThreadCount("2");

    solverConfig.setPhaseConfigList(List.of(localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMixedPhasesWithDifferentThreadCounts() {
    SolverConfig solverConfig = createSolverConfig();

    // Construction heuristic with 2 threads
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();
    constructionHeuristicPhaseConfig.setMoveThreadCount("2");

    // Local search with 4 threads
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
    localSearchPhaseConfig.setMoveThreadCount("4");

    solverConfig.setPhaseConfigList(
        List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testSolverLevelThreadConfiguration() {
    SolverConfig solverConfig = createSolverConfig();

    // Set move thread count at solver level (applies to all phases)
    solverConfig.setMoveThreadCount("3");

    // Construction heuristic phase
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();

    // Local search phase
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();

    solverConfig.setPhaseConfigList(
        List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(25, 6);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testPhaseSpecificThreadConfiguration() {
    SolverConfig solverConfig = createSolverConfig();

    // Construction heuristic with specific thread count
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();
    constructionHeuristicPhaseConfig.setMoveThreadCount("2");

    // Local search with different thread count
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
    localSearchPhaseConfig.setMoveThreadCount("4");

    solverConfig.setPhaseConfigList(
        List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(30, 8);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testThreadConfigurationPrecedence() {
    SolverConfig solverConfig = createSolverConfig();

    // Set move thread count at solver level
    solverConfig.setMoveThreadCount("2");

    // Override at phase level
    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();
    constructionHeuristicPhaseConfig.setMoveThreadCount("4");

    // Use solver level for this phase
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
    // No moveThreadCount set, should use solver level

    solverConfig.setPhaseConfigList(
        List.of(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    TestdataSolution solution = createTestSolution(20, 5);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testCustomThreadFactoryIntegration() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setThreadFactoryClass(TestThreadFactory.class);

    // Test with construction heuristic
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase));

    TestdataSolution solution = createTestSolution(15, 4);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    assertThat(TestThreadFactory.hasBeenCalled()).isTrue();
    TestThreadFactory.reset();

    // Test with local search
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(lsPhase));

    solution = createTestSolution(15, 4);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    assertThat(TestThreadFactory.hasBeenCalled()).isTrue();
    TestThreadFactory.reset();
  }

  @Test
  void testMoveThreadBufferSizeIntegration() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");
    solverConfig.setMoveThreadBufferSize(20);

    // Test with construction heuristic
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase));

    TestdataSolution solution = createTestSolution(25, 6);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Test with local search
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(lsPhase));

    solution = createTestSolution(25, 6);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testLargeScaleIntegration() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("4");
    solverConfig.setMoveThreadBufferSize(50);

    // Test with mixed phases on a larger problem
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(100, 20);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testShortDurationIntegration() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Short termination to test quick integration
    TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig.setSecondsSpentLimit(2L);
    solverConfig.setTerminationConfig(terminationConfig);

    // Test with construction heuristic
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase));

    TestdataSolution solution = createTestSolution(50, 10);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();

    // Test with local search
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(lsPhase));

    solution = createTestSolution(50, 10);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testMultiplePhaseIntegration() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Multiple phases with different configurations
    ConstructionHeuristicPhaseConfig chPhase1 = new ConstructionHeuristicPhaseConfig();
    chPhase1.setMoveThreadCount("2");

    LocalSearchPhaseConfig lsPhase1 = new LocalSearchPhaseConfig();
    lsPhase1.setMoveThreadCount("3");

    LocalSearchPhaseConfig lsPhase2 = new LocalSearchPhaseConfig();
    lsPhase2.setMoveThreadCount("2");

    solverConfig.setPhaseConfigList(List.of(chPhase1, lsPhase1, lsPhase2));

    TestdataSolution solution = createTestSolution(40, 8);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testAutoThreadCountWithPhases() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("AUTO");

    // Test with mixed phases
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(60, 12);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testPhaseTransitionWithMultithreading() {
    SolverConfig solverConfig = createSolverConfig();

    // Configure phases with different thread counts to test transitions
    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    chPhase.setMoveThreadCount("2");

    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    lsPhase.setMoveThreadCount("4");

    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(75, 15);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  @Test
  void testConfigurationConsistencyAcrossPhases() {
    SolverConfig solverConfig = createSolverConfig();

    // Test that configuration is consistent across different scenarios
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
  void testTerminationWithMultithreadedPhases() {
    SolverConfig solverConfig = createSolverConfig();
    solverConfig.setMoveThreadCount("2");

    // Test termination behavior with multithreaded phases
    TerminationConfig terminationConfig = new TerminationConfig();
    terminationConfig.setSecondsSpentLimit(3L);
    solverConfig.setTerminationConfig(terminationConfig);

    ConstructionHeuristicPhaseConfig chPhase = new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig lsPhase = new LocalSearchPhaseConfig();
    solverConfig.setPhaseConfigList(List.of(chPhase, lsPhase));

    TestdataSolution solution = createTestSolution(80, 16);
    solution = solveWithConfig(solverConfig, solution);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
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
  public static class TestdataConstraintProvider
      implements ai.greycos.solver.core.api.score.stream.ConstraintProvider {
    @Override
    public ai.greycos.solver.core.api.score.stream.Constraint[] defineConstraints(
        ai.greycos.solver.core.api.score.stream.ConstraintFactory constraintFactory) {
      return new ai.greycos.solver.core.api.score.stream.Constraint[] {
        constraintFactory
            .forEach(TestdataEntity.class)
            .reward(ai.greycos.solver.core.api.score.buildin.simple.SimpleScore.ONE)
            .asConstraint("Maximize entities")
      };
    }
  }
}
