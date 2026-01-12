package ai.greycos.solver.core.config.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify one of the core requirements of multithreaded solving: reproducibility of
 * results. After a constant number of steps, every iteration must finish with the same score when
 * using REPRODUCIBLE environment mode.
 *
 * <p>This test verifies that multithreaded move evaluation produces deterministic results across
 * multiple solving runs with identical inputs.
 */
@Tag("slow")
class MultiThreadedReproducibilityTest {

  private static final int REPETITION_COUNT = 10;
  private static final int STEP_LIMIT = 5000;
  private static final String MOVE_THREAD_COUNT = "4";

  private TestdataSolution[] testdataSolutions = new TestdataSolution[REPETITION_COUNT];
  private SolverFactory<TestdataSolution> solverFactory;

  @BeforeEach
  void createUninitializedSolutions() {
    // Create identical uninitialized solutions for each repetition
    for (int i = 0; i < REPETITION_COUNT; i++) {
      testdataSolutions[i] = createTestSolution(20, 10);
    }

    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig
        .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
        .withMoveThreadCount(MOVE_THREAD_COUNT);

    // Set step count limit for local search phase
    solverConfig
        .getPhaseConfigList()
        .forEach(
            phaseConfig -> {
              if (phaseConfig instanceof LocalSearchPhaseConfig) {
                phaseConfig.setTerminationConfig(
                    new TerminationConfig().withStepCountLimit(STEP_LIMIT));
              }
            });

    solverFactory = SolverFactory.create(solverConfig);
  }

  @Test
  void multiThreadedSolvingIsReproducible() {
    IntStream.range(0, REPETITION_COUNT).forEach(this::solveAndCompareWithPrevious);
  }

  private void solveAndCompareWithPrevious(final int iteration) {
    Solver<TestdataSolution> solver = solverFactory.buildSolver();
    TestdataSolution bestSolution = solver.solve(testdataSolutions[iteration]);
    testdataSolutions[iteration] = bestSolution;

    if (iteration > 0) {
      TestdataSolution previousBestSolution = testdataSolutions[iteration - 1];
      assertThat(bestSolution.getScore())
          .as(
              "Iteration %d score should match iteration %d score for reproducibility",
              iteration, iteration - 1)
          .isEqualTo(previousBestSolution.getScore());
    }
  }

  @Test
  void multiThreadedSolvingIsReproducibleWithAutoThreadCount() {
    // Test with AUTO thread count
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig
        .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_AUTO);

    solverConfig
        .getPhaseConfigList()
        .forEach(
            phaseConfig -> {
              if (phaseConfig instanceof LocalSearchPhaseConfig) {
                phaseConfig.setTerminationConfig(new TerminationConfig().withStepCountLimit(1000));
              }
            });

    SolverFactory<TestdataSolution> autoSolverFactory = SolverFactory.create(solverConfig);

    TestdataSolution solution1 = autoSolverFactory.buildSolver().solve(createTestSolution(15, 8));
    TestdataSolution solution2 = autoSolverFactory.buildSolver().solve(createTestSolution(15, 8));

    assertThat(solution1.getScore())
        .as("AUTO thread count should produce reproducible results")
        .isEqualTo(solution2.getScore());
  }

  private TestdataSolution createTestSolution(int entityCount, int valueCount) {
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
