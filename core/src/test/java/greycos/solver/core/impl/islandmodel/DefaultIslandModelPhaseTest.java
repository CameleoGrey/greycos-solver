package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.api.solver.phase.PhaseCommandContext;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

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

  @Test
  void innerBestSolutionImprovementUsesAgentPhaseId() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    solverConfig.setScoreDirectorFactoryConfig(
        new ScoreDirectorFactoryConfig()
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class));
    var customPhaseConfig =
        new CustomPhaseConfig()
            .withCustomPhaseCommandClassList(List.of(ImprovingPhaseCommand.class));
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withPhaseConfigList(List.of(customPhaseConfig));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    var solution = new TestdataSolution("s1");
    solution.setValueList(List.of(v1, v2, v3));
    solution.setEntityList(
        List.of(
            new TestdataEntity("e1", v1),
            new TestdataEntity("e2", v1),
            new TestdataEntity("e3", v2)));

    var solvedSolution = PlannerTestUtils.solve(solverConfig, solution, true);

    assertThat(solvedSolution.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @Test
  void agentFailureInterruptsBlockedPeer() throws InterruptedException {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var customPhaseConfig =
        new CustomPhaseConfig()
            .withCustomPhaseCommandClassList(List.of(BlockingThenFailingPhaseCommand.class));
    var phaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withPhaseConfigList(List.of(customPhaseConfig));
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    var solverExecutor = Executors.newSingleThreadExecutor();
    var solveFuture =
        solverExecutor.submit(
            () -> PlannerTestUtils.solve(solverConfig, createSolution("s1", 3), true));
    try {
      assertThatThrownBy(() -> solveFuture.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(
              BlockingThenFailingPhaseCommand.BLOCKED_AGENT_INTERRUPTED.await(5, TimeUnit.SECONDS))
          .isTrue();
    } finally {
      BlockingThenFailingPhaseCommand.RELEASE_BLOCKED_AGENT.countDown();
      solveFuture.cancel(true);
      solverExecutor.shutdownNow();
    }
  }

  @Test
  void invalidReceiveGlobalUpdateFrequencyFailsFast() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig = new IslandModelPhaseConfig().withIslandCount(1);
    phaseConfig.setReceiveGlobalUpdateFrequency(0);
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    assertThatThrownBy(() -> SolverFactory.<TestdataSolution>create(solverConfig).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receive global update frequency must be at least 1");
  }

  @Test
  void invalidDeprecatedCompareGlobalFrequencyFailsFast() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var phaseConfig = new IslandModelPhaseConfig().withIslandCount(1);
    phaseConfig.setCompareGlobalFrequency(0);
    solverConfig.setPhaseConfigList(Collections.singletonList(phaseConfig));

    assertThatThrownBy(() -> SolverFactory.<TestdataSolution>create(solverConfig).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Compare global frequency must be at least 1");
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

  public static final class ImprovingPhaseCommand implements PhaseCommand<TestdataSolution> {

    @Override
    public void changeWorkingSolution(PhaseCommandContext<TestdataSolution> context) {
      var solution = context.getWorkingSolution();
      var variableMetaModel =
          context
              .getSolutionMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      context.executeAndCalculateScore(
          Moves.change(
              variableMetaModel, solution.getEntityList().get(1), solution.getValueList().get(2)));
    }
  }

  public static final class BlockingThenFailingPhaseCommand
      implements PhaseCommand<TestdataSolution> {

    private static final AtomicBoolean BLOCK_FIRST_AGENT = new AtomicBoolean(true);
    private static final CountDownLatch BLOCKED_AGENT_STARTED = new CountDownLatch(1);
    private static final CountDownLatch BLOCKED_AGENT_INTERRUPTED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_BLOCKED_AGENT = new CountDownLatch(1);

    @Override
    public void changeWorkingSolution(PhaseCommandContext<TestdataSolution> context) {
      if (BLOCK_FIRST_AGENT.compareAndSet(true, false)) {
        BLOCKED_AGENT_STARTED.countDown();
        try {
          RELEASE_BLOCKED_AGENT.await();
        } catch (InterruptedException e) {
          BLOCKED_AGENT_INTERRUPTED.countDown();
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Blocked island agent was interrupted.", e);
        }
        return;
      }

      try {
        if (!BLOCKED_AGENT_STARTED.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Blocked island agent did not start.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Failing island agent was interrupted.", e);
      }
      throw new IllegalStateException("Intentional island agent failure.");
    }
  }
}
