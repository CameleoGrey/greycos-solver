package ai.greycos.solver.core.api.cotwin.solution.cloner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.clone.customcloner.TestdataCorrectlyClonedSolution;
import ai.greycos.solver.core.testcotwin.clone.customcloner.TestdataEntitiesNotClonedSolution;
import ai.greycos.solver.core.testcotwin.clone.customcloner.TestdataScoreNotClonedSolution;
import ai.greycos.solver.core.testcotwin.clone.customcloner.TestdataScoreNotEqualSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class CustomSolutionClonerTest {

  @Test
  void clonedUsingCustomCloner() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataCorrectlyClonedSolution.class, TestdataEntity.class);
    solverConfig.setEnvironmentMode(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);

    TestdataCorrectlyClonedSolution solution = new TestdataCorrectlyClonedSolution();
    TestdataCorrectlyClonedSolution solved = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solved.isClonedByCustomCloner()).as("Custom solution cloner was not used").isTrue();
  }

  @Test
  void scoreNotCloned() {
    // RHBRMS-1430 Possible NPE when custom cloner doesn't clone score
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataScoreNotClonedSolution.class, TestdataEntity.class);
    solverConfig.setEnvironmentMode(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);

    TestdataScoreNotClonedSolution solution = new TestdataScoreNotClonedSolution();

    assertThatIllegalStateException()
        .isThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .withMessageContaining("Cloning corruption: the original's score ");
  }

  @Test
  void scoreNotEqual() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataScoreNotEqualSolution.class, TestdataEntity.class);
    solverConfig.setEnvironmentMode(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);

    TestdataScoreNotEqualSolution solution = new TestdataScoreNotEqualSolution();

    assertThatIllegalStateException()
        .isThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .withMessageContaining("Cloning corruption: the original's score ");
  }

  @Test
  void entitiesNotCloned() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataEntitiesNotClonedSolution.class, TestdataEntity.class);
    solverConfig.setEnvironmentMode(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);

    TestdataEntitiesNotClonedSolution solution = new TestdataEntitiesNotClonedSolution();

    assertThatIllegalStateException()
        .isThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .withMessageContaining("Cloning corruption: the same entity ");
  }
}
