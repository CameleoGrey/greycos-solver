package ai.greycos.solver.core.impl.exhaustivesearch;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;
import ai.greycos.solver.core.testcotwin.pinned.TestdataPinnedSolution;
import ai.greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedEntity;
import ai.greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class DefaultExhaustiveSearchPhaseTest {

  @Test
  void solveWithInitializedEntities() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setPhaseConfigList(Collections.singletonList(new ExhaustiveSearchPhaseConfig()));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        Arrays.asList(
            new TestdataEntity("e1", null),
            new TestdataEntity("e2", v2),
            new TestdataEntity("e3", v1)));

    solution = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solution).isNotNull();
    var solvedE1 = solution.getEntityList().get(0);
    assertCode("e1", solvedE1);
    assertThat(solvedE1.getValue()).isNotNull();
    var solvedE2 = solution.getEntityList().get(1);
    assertCode("e2", solvedE2);
    assertThat(solvedE2.getValue()).isEqualTo(v2);
    var solvedE3 = solution.getEntityList().get(2);
    assertCode("e3", solvedE3);
    assertThat(solvedE3.getValue()).isEqualTo(v1);
  }

  @Test
  void solveWithPinnedEntities() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataPinnedSolution.class, TestdataPinnedEntity.class)
            .withPhases(new ExhaustiveSearchPhaseConfig());

    var solution = new TestdataPinnedSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        Arrays.asList(
            new TestdataPinnedEntity("e1", null, false, false),
            new TestdataPinnedEntity("e2", v2, true, false),
            new TestdataPinnedEntity("e3", v3, false, true)));

    solution = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solution).isNotNull();
    var solvedE1 = solution.getEntityList().get(0);
    assertCode("e1", solvedE1);
    assertThat(solvedE1.getValue()).isNotNull();
    var solvedE2 = solution.getEntityList().get(1);
    assertCode("e2", solvedE2);
    assertThat(solvedE2.getValue()).isEqualTo(v2);
    var solvedE3 = solution.getEntityList().get(2);
    assertCode("e3", solvedE3);
    assertThat(solvedE3.getValue()).isEqualTo(v3);
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void solveWithPinnedEntitiesWhenUnassignedAllowedAndPinnedToNull() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(
                TestdataPinnedAllowsUnassignedSolution.class,
                TestdataPinnedAllowsUnassignedEntity.class)
            .withPhases(new ExhaustiveSearchPhaseConfig());

    var solution = new TestdataPinnedAllowsUnassignedSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        Arrays.asList(
            new TestdataPinnedAllowsUnassignedEntity("e1", null, false, false),
            new TestdataPinnedAllowsUnassignedEntity("e2", v2, true, false),
            new TestdataPinnedAllowsUnassignedEntity("e3", null, false, true)));

    solution =
        PlannerTestUtils.solve(
            solverConfig, solution, true); // No change will be made, but shadows will be updated.
    assertThat(solution).isNotNull();
    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void solveWithPinnedEntitiesWhenUnassignedNotAllowedAndPinnedToNull() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataPinnedSolution.class, TestdataPinnedEntity.class)
            .withPhases(new ExhaustiveSearchPhaseConfig());

    var solution = new TestdataPinnedSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(Arrays.asList(v1, v2, v3));
    solution.setEntityList(
        Arrays.asList(
            new TestdataPinnedEntity("e1", null, false, false),
            new TestdataPinnedEntity("e2", v2, true, false),
            new TestdataPinnedEntity("e3", null, false, true)));

    assertThatThrownBy(() -> PlannerTestUtils.solve(solverConfig, solution))
        .hasMessageContaining("entity (e3)")
        .hasMessageContaining("variable (value");
  }

  @Test
  void solveWithEmptyEntityList() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setPhaseConfigList(Collections.singletonList(new ExhaustiveSearchPhaseConfig()));

    var solution = new TestdataSolution("s1");
    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    solution.setValueList(Arrays.asList(v1, v2, v3));
    solution.setEntityList(Collections.emptyList());

    solution = PlannerTestUtils.solve(solverConfig, solution, true);
    assertThat(solution).isNotNull();
    assertThat(solution.getEntityList()).isEmpty();
  }
}
