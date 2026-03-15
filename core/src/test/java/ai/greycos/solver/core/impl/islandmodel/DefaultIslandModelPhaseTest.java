package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.phase.PhaseCommand;
import ai.greycos.solver.core.api.solver.phase.PhaseCommandContext;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.DefaultSolver;
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

  @Test
  void phaseLifecycleCallbacksAreTriggered() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    @SuppressWarnings("unchecked")
    DefaultSolver<TestdataSolution> solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(solverConfig).buildSolver();
    @SuppressWarnings("unchecked")
    DefaultIslandModelPhase<TestdataSolution> islandPhase =
        (DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().get(0);

    AtomicInteger phaseStartedCount = new AtomicInteger();
    AtomicInteger phaseEndedCount = new AtomicInteger();
    islandPhase.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phaseScope) {
            phaseStartedCount.incrementAndGet();
            assertThat(phaseScope).isInstanceOf(IslandModelPhaseScope.class);
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            phaseEndedCount.incrementAndGet();
            assertThat(phaseScope).isInstanceOf(IslandModelPhaseScope.class);
          }
        });

    solver.solve(createSolution("s1", 3));

    assertThat(phaseStartedCount).hasValue(1);
    assertThat(phaseEndedCount).hasValue(1);
  }

  @Test
  void configuredInnerPhaseListIsUsed() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var customPhaseConfig =
        new CustomPhaseConfig()
            .withCustomPhaseCommandClassList(List.of(ThrowingPhaseCommand.class));
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withPhaseConfigList(List.of(customPhaseConfig));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    var solver = SolverFactory.<TestdataSolution>create(solverConfig).buildSolver();
    assertThatThrownBy(() -> solver.solve(createSolution("s1", 3)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Island agent");
  }

  private static TestdataSolution createSolution(String code, int valueCount) {
    TestdataSolution solution = new TestdataSolution(code);
    var valueList = new java.util.ArrayList<TestdataValue>(valueCount);
    var entityList = new java.util.ArrayList<TestdataEntity>(valueCount);
    for (int i = 0; i < valueCount; i++) {
      valueList.add(new TestdataValue("v" + i));
    }
    for (int i = 0; i < valueCount; i++) {
      entityList.add(new TestdataEntity("e" + i, valueList.get(i % valueList.size())));
    }
    solution.setValueList(valueList);
    solution.setEntityList(entityList);
    return solution;
  }

  public static final class ThrowingPhaseCommand implements PhaseCommand<TestdataSolution> {
    @Override
    public void changeWorkingSolution(PhaseCommandContext<TestdataSolution> context) {
      throw new IllegalStateException("Intentional test failure");
    }
  }
}
