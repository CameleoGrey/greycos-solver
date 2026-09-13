package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.api.solver.phase.PhaseCommandContext;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.LocalSearchPhase;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.UniversalTermination;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class IslandLifecycleRegressionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void localSearchAfterAWorseningPhaseStartsFromBestUnderFullAssert(boolean alns) {
    PhaseConfig<?> firstPhase =
        alns
            ? new AlnsPhaseConfig()
                .withAcceptancePolicyClass(AcceptEveryCandidate.class)
                .withDestroyOperators(
                    new AlnsDestroyOperatorConfig()
                        .withId("first")
                        .withCustomClass(FirstEntity.class)
                        .withMinimumDestroyedCount(1)
                        .withMaximumDestroyedCount(1))
                .withRepairOperators(
                    new AlnsRepairOperatorConfig()
                        .withId("worsen")
                        .withCustomClass(WorseningRepair.class))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))
            : new CustomPhaseConfig().withCustomPhaseCommandClassList(List.of(Worsen.class));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withPhaseConfigList(
                        List.of(
                            firstPhase,
                            new LocalSearchPhaseConfig()
                                .withTerminationConfig(
                                    new TerminationConfig().withMoveCountLimit(2L)))));

    var solution =
        SolverFactory.<TestdataSolution>create(config)
            .buildSolver()
            .solve(TestdataSolution.generateSolution(3, 3));

    assertThat(solution.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(new TestdataEasyScoreCalculator().calculateScore(solution))
        .isEqualTo(solution.getScore());
  }

  @Test
  @SuppressWarnings("unchecked")
  void agentRegistersListenersBeforeStartupAndDeliversLifecycleOnce() {
    var scope = newScope();
    var director = spy(scope.getScoreDirector());
    scope.setScoreDirector(director);
    var phase = (Phase<TestdataSolution>) mock(LocalSearchPhase.class);
    var otherPhase = (Phase<TestdataSolution>) mock(Phase.class);
    var recaller = (BestSolutionRecaller<TestdataSolution>) mock(BestSolutionRecaller.class);
    var termination = (UniversalTermination<TestdataSolution>) mock(BasicPlumbingTermination.class);
    var listener = (PhaseLifecycleListener<TestdataSolution>) mock(PhaseLifecycleListener.class);
    var solver = new IslandSolver<>(recaller, termination, List.of(phase, otherPhase));
    solver.addPhaseLifecycleListener(listener);
    scope.setSolver(solver);
    var random = DefaultRandomSource.seeded(0L).splitForChildThread();
    scope.setWorkingRandom(random);
    var latch = new CountDownLatch(1);
    var agent =
        new IslandAgent<>(
            0,
            List.of(phase, otherPhase),
            TestdataSolution.generateSolution(3, 3),
            new SharedGlobalState<>(),
            new BoundedChannel<>(1),
            new BoundedChannel<>(1),
            IslandModelConfig.builder().withIslandCount(1).build(),
            random,
            scope,
            latch);

    agent.run();

    var order = inOrder(phase, otherPhase, recaller, termination, listener);
    order.verify(phase, times(3)).addPhaseLifecycleListener(any());
    order.verify(otherPhase).addPhaseLifecycleListener(any());
    order.verify(recaller).solvingStarted(scope);
    order.verify(termination).solvingStarted(scope);
    order.verify(listener).solvingStarted(scope);
    order.verify(phase).solvingStarted(scope);
    order.verify(otherPhase).solvingStarted(scope);
    order.verify(phase).solve(scope);
    order.verify(otherPhase).solve(scope);
    order.verify(phase).solvingEnded(scope);
    order.verify(otherPhase).solvingEnded(scope);
    order.verify(recaller).solvingEnded(scope);
    order.verify(termination).solvingEnded(scope);
    order.verify(listener).solvingEnded(scope);
    verify(director).close();
    assertThat(scope.getProblemSizeStatistics()).isNotNull();
    assertThat(agent.getStatus()).isEqualTo(AgentStatus.DEAD);
    assertThat(latch.getCount()).isZero();
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedNormalCleanupAndErrorCleanupCloseTheDirectorOnlyOnce() {
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var phase = (Phase<TestdataSolution>) mock(Phase.class);
    var failure = new IllegalStateException("Normal cleanup failed");
    doThrow(failure).when(phase).solvingEnded(scope);
    var closeFailure = new IllegalStateException("Director close failed");
    doThrow(closeFailure).when(director).close();
    var errorCleanupFailure = new IllegalStateException("Error cleanup failed");
    doThrow(errorCleanupFailure).when(phase).solvingError(scope, failure);
    var solver =
        new IslandSolver<TestdataSolution>(
            mock(BestSolutionRecaller.class), mock(BasicPlumbingTermination.class), List.of(phase));

    assertThatThrownBy(() -> solver.solvingEnded(scope)).isSameAs(failure);
    assertThatThrownBy(() -> solver.solvingError(scope, failure)).isSameAs(errorCleanupFailure);

    assertThat(failure.getSuppressed()).containsExactly(closeFailure);
    verify(phase).solvingEnded(scope);
    verify(phase).solvingError(scope, failure);
    verify(director).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void commonPhaseRunnerChecksSolverTerminationBeforeEveryPhase() {
    var scope = newScope();
    scope.setInitialSolution(TestdataSolution.generateSolution(3, 3));
    try (var director = scope.getScoreDirector()) {
      var first = (Phase<TestdataSolution>) mock(Phase.class);
      var second = (Phase<TestdataSolution>) mock(Phase.class);
      var termination =
          (UniversalTermination<TestdataSolution>) mock(BasicPlumbingTermination.class);
      when(termination.isSolverTerminated(scope)).thenReturn(false, true);
      var solver =
          new IslandSolver<TestdataSolution>(
              mock(BestSolutionRecaller.class), termination, List.of(first, second));

      solver.runPhases(scope);

      verify(first).solve(scope);
      verify(second, times(0)).solve(scope);
    }
  }

  private static SolverScope<TestdataSolution> newScope() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    return ((DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver())
        .getSolverScope();
  }

  public static final class Worsen implements PhaseCommand<TestdataSolution> {
    @Override
    public void changeWorkingSolution(PhaseCommandContext<TestdataSolution> context) {
      var solution = context.getWorkingSolution();
      var variable =
          context
              .getSolutionMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      context.executeAndCalculateScore(
          Moves.change(
              variable, solution.getEntityList().getFirst(), solution.getValueList().get(1)));
    }
  }

  public static final class FirstEntity
      implements AlnsDestroyOperator<TestdataSolution, SimpleScore> {
    @Override
    public List<AlnsTarget<TestdataSolution>> select(
        AlnsContext<TestdataSolution, SimpleScore> context, int size) {
      return List.of(context.targets().getFirst());
    }
  }

  public static final class WorseningRepair
      implements AlnsRepairOperator<TestdataSolution, SimpleScore> {
    @Override
    public boolean repair(
        AlnsContext<TestdataSolution, SimpleScore> context,
        List<AlnsTarget<TestdataSolution>> pending) {
      var value = context.workingSolution().getEntityList().get(1).getValue();
      context.assign(
          context.assignments(pending.getFirst()).stream()
              .filter(assignment -> assignment.value() == value)
              .findFirst()
              .orElseThrow());
      return true;
    }
  }

  public static final class AcceptEveryCandidate implements AlnsAcceptancePolicy<SimpleScore> {
    @Override
    public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
      return true;
    }
  }
}
