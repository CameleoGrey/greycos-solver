package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated(
    "Changes the shared worker shutdown timeout and uses a configured recording thread factory.")
@Timeout(20)
class IslandSolveReuseTest {

  @Test
  void rejectsReuseWithoutResettingStateUntilThePreviousWorkersHaveExited()
      throws InterruptedException {
    var oldWorkerStarted = new CountDownLatch(1);
    var releaseOldWorker = new CountDownLatch(1);
    var calls = new AtomicInteger();
    PhaseCommand<TestdataSolution> command =
        context -> {
          var invocation = calls.getAndIncrement();
          if (invocation == 0) {
            oldWorkerStarted.countDown();
            awaitUninterruptibly(releaseOldWorker);
            var solution = context.getWorkingSolution();
            var variable =
                context
                    .getSolutionMetaModel()
                    .genuineEntity(TestdataEntity.class)
                    .basicVariable("value", TestdataValue.class);
            context.executeAndCalculateScore(
                Moves.change(
                    variable, solution.getEntityList().getFirst(), solution.getValueList().get(1)));
          } else if (invocation == 1) {
            awaitUninterruptibly(oldWorkerStarted);
            throw new IllegalStateException("First solve failure");
          }
        };
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withPhaseConfigList(
                        List.of(new CustomPhaseConfig().withCustomPhaseCommands(command))));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var scope = solver.getSolverScope();
    var director = spy(scope.getScoreDirector());
    scope.setScoreDirector(director);
    var events = new CopyOnWriteArrayList<String>();
    solver.addEventListener(event -> events.add(event.getNewBestSolution().getCode()));
    var oldProblem = TestdataSolution.generateSolution(3, 3);
    oldProblem.setCode("OLD");
    oldProblem
        .getEntityList()
        .forEach(entity -> entity.setValue(oldProblem.getValueList().getFirst()));
    var newProblem = TestdataSolution.generateSolution(1, 4);
    newProblem.setCode("NEW");
    var originalTimeout = ThreadUtils.getDefaultShutdownTimeout();
    RecordingThreadFactory.threads.clear();
    ThreadUtils.setDefaultShutdownTimeout(1);
    try {
      assertThatThrownBy(() -> solver.solve(oldProblem)).isInstanceOf(IllegalStateException.class);
      assertThat(calls).hasValue(2);
      assertThat(RecordingThreadFactory.threads).anyMatch(Thread::isAlive);
      solver.terminateEarly();
      var bestSolution = scope.getBestSolution();
      var workingSolution = director.getWorkingSolution();
      var bestScore = scope.getBestScore();
      var startTime = scope.getStartingSystemTimeMillis();
      var globalState =
          ((DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst())
              .getGlobalState();
      var eventCountAfterFailure = events.size();
      clearInvocations(director);

      for (int attempt = 0; attempt < 3; attempt++) {
        assertThatThrownBy(() -> solver.solve(newProblem))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("previous solve")
            .hasMessageContaining("worker groups")
            .hasMessageContaining("Wait");
        verifyNoInteractions(director);
        assertThat(scope.getBestSolution()).isSameAs(bestSolution);
        assertThat(scope.getBestScore()).isSameAs(bestScore);
        assertThat(scope.getStartingSystemTimeMillis()).isEqualTo(startTime);
        assertThat(
                ((DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst())
                    .getGlobalState())
            .isSameAs(globalState);
        assertThat(solver.isTerminateEarly()).isTrue();
        assertThat(solver.isSolving()).isFalse();
        assertThat(calls).hasValue(2);
      }
      assertThat(director.getWorkingSolution()).isSameAs(workingSolution);

      releaseOldWorker.countDown();
      joinWorkers();
      assertThat(events).hasSize(eventCountAfterFailure);
      var result = solver.solve(newProblem);

      assertThat(result.getCode()).isEqualTo("NEW");
      assertThat(result.getEntityList()).hasSize(4);
      assertThat(result.getScore())
          .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
      assertThat(events.subList(eventCountAfterFailure, events.size())).containsOnly("NEW");
      assertThat(
              ((DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst())
                  .getGlobalState())
          .isNotSameAs(globalState);
      assertThat(solver.isTerminateEarly()).isFalse();
    } finally {
      releaseOldWorker.countDown();
      ThreadUtils.setDefaultShutdownTimeout(originalTimeout);
      joinWorkers();
      RecordingThreadFactory.threads.clear();
    }
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "Timed out waiting for the test to release an island worker.");
          }
          return;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void joinWorkers() throws InterruptedException {
    for (var thread : RecordingThreadFactory.threads) {
      thread.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(thread.isAlive()).as("Worker %s has exited", thread.getName()).isFalse();
    }
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    private static final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "reuse-test-island-" + threads.size());
      threads.add(thread);
      return thread;
    }
  }
}
