package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class DefaultIslandModelPhaseTest {

  @Test
  void solveWithIslandModelCompletes() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(10));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    TestdataSolution solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(java.util.Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        java.util.Arrays.asList(
            new TestdataEntity("e1", v1),
            new TestdataEntity("e2", v2),
            new TestdataEntity("e3", v1)));

    solution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore()).isNotNull();
  }

  @Test
  void solveWithSingleIslandWorks() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(10));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    TestdataSolution solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(java.util.Arrays.asList(v1, v2));
    solution.setEntityList(
        java.util.Arrays.asList(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    solution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solution).isNotNull();
  }

  @Test
  void solveWithMultipleIslandsCompletes() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(4)
            .withMigrationFrequency(5)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    TestdataSolution solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(java.util.Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        java.util.Arrays.asList(
            new TestdataEntity("e1", v1),
            new TestdataEntity("e2", v2),
            new TestdataEntity("e3", v3),
            new TestdataEntity("e4", v1)));

    solution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore()).isInstanceOf(SimpleScore.class);
  }

  @Test
  void solveWithMigrationEnabledCompletes() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMigrationFrequency(10)
            .withCompareGlobalEnabled(true)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(15));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    TestdataSolution solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(java.util.Arrays.asList(v1, v2));
    solution.setEntityList(
        java.util.Arrays.asList(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    solution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore()).isNotNull();
  }

  @Test
  void solveWithCompareGlobalDisabledCompletes() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMigrationFrequency(10)
            .withCompareGlobalEnabled(false)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(15));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    TestdataSolution solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    solution.setValueList(java.util.Arrays.asList(v1, v2));
    solution.setEntityList(
        java.util.Arrays.asList(new TestdataEntity("e1", v1), new TestdataEntity("e2", v2)));

    solution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solution).isNotNull();
    assertThat(solution.getScore()).isNotNull();
  }
}
