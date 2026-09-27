package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IslandAgentErrorLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void childFailureCleansAllPhasesAndMarksAgentDead(boolean error) {
    Throwable original =
        error
            ? new AssertionError("Island phase failed.")
            : new IllegalStateException("Island phase failed.");
    var cleanupFailure = new AssertionError("First phase cleanup failed.");
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    var scope =
        ((DefaultSolver<TestdataSolution>)
                SolverFactory.<TestdataSolution>create(config).buildSolver())
            .getSolverScope();
    var firstPhase = (Phase<TestdataSolution>) mock(Phase.class);
    var secondPhase = (Phase<TestdataSolution>) mock(Phase.class);
    doThrow(original).when(firstPhase).solve(scope);
    var events = new ArrayList<String>();
    doAnswer(
            invocation -> {
              events.add("phase-1");
              throw cleanupFailure;
            })
        .when(firstPhase)
        .solvingError(any(), any());
    doAnswer(
            invocation -> {
              events.add("phase-2");
              return null;
            })
        .when(secondPhase)
        .solvingError(any(), any());
    var solver =
        new IslandSolver<TestdataSolution>(
            EnvironmentMode.NO_ASSERT,
            mock(ScoreDirectorFactory.class),
            mock(BestSolutionRecaller.class),
            mock(BasicPlumbingTermination.class),
            List.of(firstPhase, secondPhase));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener");
          }
        });
    scope.setSolver(solver);
    var random = DefaultRandomSource.seeded(0L).splitForChildThread();
    scope.setWorkingRandom(random);
    var latch = new CountDownLatch(1);
    var agent =
        new IslandAgent<>(
            0,
            List.of(firstPhase, secondPhase),
            TestdataSolution.generateSolution(3, 3),
            new SharedGlobalState<>(),
            new BoundedChannel<>(1),
            new BoundedChannel<>(1),
            IslandModelConfig.builder().withIslandCount(1).build(),
            random,
            scope,
            latch);

    try {
      var thrown = catchThrowable(agent::run);
      if (error) {
        assertThat(thrown).isSameAs(original);
      } else {
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasCause(original);
      }
      assertThat(events).containsExactly("listener", "phase-1", "phase-2");
      assertThat(original.getSuppressed()).containsExactly(cleanupFailure);
      assertThat(scope.getScoreDirector().getWorkingSolution()).isNull();
      assertThat(agent.getStatus()).isEqualTo(AgentStatus.DEAD);
      assertThat(latch.getCount()).isZero();
      verify(firstPhase).solve(scope);
    } finally {
      scope.getScoreDirector().close();
    }
  }
}
