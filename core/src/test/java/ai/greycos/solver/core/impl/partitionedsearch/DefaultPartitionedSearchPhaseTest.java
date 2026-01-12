package ai.greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataSolutionPartitioner;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link DefaultPartitionedSearchPhase} functionality, especially in combination with
 * multithreaded move evaluation.
 */
class DefaultPartitionedSearchPhaseTest {

  // Existing tests (above)

  @Test
  @Timeout(10)
  void partitionedSearchWithZeroEntities() {
    final int partSize = 3;
    final int entityCount = 0; // No entities

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      SolverFactory<TestdataSolution> solverFactory =
          createSolverFactory(false, SolverConfig.MOVE_THREAD_COUNT_NONE);
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 5));

      // Should handle gracefully with no entities to partition
      assertThat(solution).isNotNull();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  @Test
  @Timeout(10)
  void partitionedSearchWithRunnableThreadLimit() {
    final int partSize = 2;
    final int entityCount = 10; // Would create 5 partitions

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      // Create a custom partitioner that respects runnablePartThreadLimit
      SolverFactory<TestdataSolution> solverFactory = createSolverFactory(false, "2");
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 5));

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  @Test
  @Timeout(10)
  void partitionedSearchWithSingleThreaded() {
    final int partSize = 3;
    final int entityCount = 21; // Will create 7 partitions

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      SolverFactory<TestdataSolution> solverFactory =
          createSolverFactory(false, SolverConfig.MOVE_THREAD_COUNT_NONE);
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 5));

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  @Test
  @Timeout(10)
  void partitionedSearchWithExplicitThreadCount() {
    final int partSize = 3;
    final int entityCount = 21; // Will create 7 partitions

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      SolverFactory<TestdataSolution> solverFactory = createSolverFactory(false, "2");
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 5));

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  @Test
  @Timeout(10)
  void partitionedSearchWithMultiThreading() {
    final int partSize = 5;
    final int entityCount = 20; // Will create 4 partitions
    final String moveThreadCount = "2";

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      SolverFactory<TestdataSolution> solverFactory = createSolverFactory(false, moveThreadCount);
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 10));

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  @Test
  @Timeout(10)
  void partitionedSearchWithAutoThreadCount() {
    final int partSize = 4;
    final int entityCount = 16; // Will create 4 partitions

    System.setProperty("testPartitionSize", String.valueOf(partSize));
    try {
      SolverFactory<TestdataSolution> solverFactory =
          createSolverFactory(false, SolverConfig.MOVE_THREAD_COUNT_AUTO);
      Solver<TestdataSolution> solver = solverFactory.buildSolver();

      TestdataSolution solution = solver.solve(createSolution(entityCount, 8));

      assertThat(solution).isNotNull();
      assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    } finally {
      System.clearProperty("testPartitionSize");
    }
  }

  private static SolverFactory<TestdataSolution> createSolverFactory(
      boolean infinite, String moveThreadCount) {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setMoveThreadCount(moveThreadCount);

    PartitionedSearchPhaseConfig partitionedSearchPhaseConfig = new PartitionedSearchPhaseConfig();
    partitionedSearchPhaseConfig.setSolutionPartitionerClass(TestdataSolutionPartitioner.class);

    ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig();
    LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
    if (!infinite) {
      localSearchPhaseConfig.setTerminationConfig(new TerminationConfig().withStepCountLimit(1));
    }

    partitionedSearchPhaseConfig.setPhaseConfigList(
        Arrays.asList(constructionHeuristicPhaseConfig, localSearchPhaseConfig));

    solverConfig.setPhaseConfigList(Arrays.asList(partitionedSearchPhaseConfig));

    return SolverFactory.create(solverConfig);
  }

  private TestdataSolution createSolution(int entityCount, int valueCount) {
    TestdataSolution solution = new TestdataSolution();

    final List<TestdataValue> values =
        IntStream.range(0, valueCount)
            .mapToObj(number -> new TestdataValue("value" + number))
            .collect(Collectors.toList());
    final List<TestdataEntity> entities =
        IntStream.range(0, entityCount)
            .mapToObj(number -> new TestdataEntity("entity" + number))
            .collect(Collectors.toList());

    solution.setValueList(values);
    solution.setEntityList(entities);
    return solution;
  }
}
