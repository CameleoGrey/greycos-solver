package ai.greycos.solver.core.config.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testutil.MockThreadFactory;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Tests for multithreaded solving functionality with various thread count configurations. */
class SolverConfigMultiThreadedTest {

  @Test
  @Timeout(5)
  void solvingWithTooHighThreadCountFinishes() {
    runSolvingAndVerifySolution(10, 20, "256");
  }

  @Disabled("Similar to PLANNER-1180: Multithreading with very small problems can cause issues")
  @Test
  @Timeout(5)
  void solvingOfVerySmallProblemFinishes() {
    runSolvingAndVerifySolution(1, 1, "2");
  }

  @Test
  @Timeout(5)
  void customThreadFactoryClassIsUsed() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setThreadFactoryClass(MockThreadFactory.class);
    solverConfig.setMoveThreadCount("2");

    TestdataSolution solution = createTestSolution(3, 5);

    MockThreadFactory.reset(); // Reset the static state before solving
    solution = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    assertThat(MockThreadFactory.hasBeenCalled()).isTrue();
  }

  @Test
  @Timeout(5)
  void multiThreadedSolvingWithAutoThreadCount() {
    runSolvingAndVerifySolution(5, 10, SolverConfig.MOVE_THREAD_COUNT_AUTO);
  }

  @Test
  @Timeout(5)
  void multiThreadedSolvingWithExplicitThreadCount() {
    runSolvingAndVerifySolution(8, 15, "4");
  }

  @Test
  @Timeout(5)
  void singleThreadedSolvingWithNoneThreadCount() {
    runSolvingAndVerifySolution(8, 15, SolverConfig.MOVE_THREAD_COUNT_NONE);
  }

  private void runSolvingAndVerifySolution(
      final int entityCount, final int valueCount, final String moveThreadCount) {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setMoveThreadCount(moveThreadCount);

    TestdataSolution solution = createTestSolution(entityCount, valueCount);

    solution = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solution).isNotNull();
    assertThat(solution.getScore().isSolutionInitialized()).isTrue();
  }

  private TestdataSolution createTestSolution(int entityCount, int valueCount) {
    TestdataSolution testdataSolution = new TestdataSolution();

    final List<TestdataValue> values =
        IntStream.range(0, valueCount)
            .mapToObj(number -> new TestdataValue("value" + number))
            .collect(Collectors.toList());
    final List<TestdataEntity> entities =
        IntStream.range(0, entityCount)
            .mapToObj(number -> new TestdataEntity("entity" + number))
            .collect(Collectors.toList());

    testdataSolution.setValueList(values);
    testdataSolution.setEntityList(entities);
    return testdataSolution;
  }
}
