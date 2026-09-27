package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Configures shared test state for thread factories instantiated by solver configuration.")
@Timeout(20)
class IslandStartupOwnershipTest {

  @Test
  void nullThreadFactoryFailsInsteadOfLeavingTheCoordinatorWaiting() throws InterruptedException {
    var solver = solver(NullThreadFactory.class, 1);
    var children = recordChildren(solver, null);
    var task = new FutureTask<>(() -> solver.solve(TestdataSolution.generateSolution(3, 3)));
    var coordinator = new Thread(task, "null-factory-test-coordinator");
    coordinator.start();
    try {
      assertThatThrownBy(() -> task.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("thread factory")
          .hasMessageContaining("returned null");
      assertThat(children).hasSize(1);
      verify(children.getFirst()).close();
      assertThat(solver.isSolving()).isFalse();
    } finally {
      task.cancel(true);
      coordinator.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(coordinator.isAlive()).isFalse();
    }
  }

  @Test
  void factoryRefusalClosesAcceptedButUnstartedAgentsExactlyOnce() throws InterruptedException {
    var fixture = new DelayedFactoryFixture();
    RefuseSecondThreadFactory.fixture = fixture;
    var solver = solver(RefuseSecondThreadFactory.class, 2);
    var firstDirectorClosed = new CountDownLatch(1);
    var children = recordChildren(solver, firstDirectorClosed);
    var task = new FutureTask<>(() -> solver.solve(TestdataSolution.generateSolution(3, 3)));
    var coordinator = new Thread(task, "refused-factory-test-coordinator");
    coordinator.start();
    try {
      assertThat(fixture.refused.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(firstDirectorClosed.await(5, TimeUnit.SECONDS))
          .as("Cancellation closes the first director before its accepted task starts")
          .isTrue();
      fixture.releaseWorker.countDown();
      assertThatThrownBy(() -> task.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCause(fixture.failure);
      assertThat(children).hasSize(2);
      children.forEach(child -> verify(child).close());
    } finally {
      fixture.releaseWorker.countDown();
      task.cancel(true);
      coordinator.join(TimeUnit.SECONDS.toMillis(5));
      assertThat(coordinator.isAlive()).isFalse();
      if (fixture.worker != null) {
        fixture.worker.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(fixture.worker.isAlive()).isFalse();
      }
      RefuseSecondThreadFactory.fixture = null;
    }
  }

  private static DefaultSolver<TestdataSolution> solver(
      Class<? extends ThreadFactory> factory, int islands) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withThreadFactoryClass(factory)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(islands)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static List<InnerScoreDirector<TestdataSolution, ?>> recordChildren(
      DefaultSolver<TestdataSolution> solver, CountDownLatch firstDirectorClosed) {
    var children = new CopyOnWriteArrayList<InnerScoreDirector<TestdataSolution, ?>>();
    var original = solver.getSolverScope().getScoreDirector();
    var parent = spy(original);
    doAnswer(
            invocation -> {
              var child = spy(original.createChildThreadScoreDirector(ChildThreadType.PART_THREAD));
              if (children.isEmpty() && firstDirectorClosed != null) {
                doAnswer(
                        closeInvocation -> {
                          try {
                            return closeInvocation.callRealMethod();
                          } finally {
                            firstDirectorClosed.countDown();
                          }
                        })
                    .when(child)
                    .close();
              }
              children.add(child);
              return child;
            })
        .when(parent)
        .createChildThreadScoreDirector(ChildThreadType.PART_THREAD);
    solver.getSolverScope().setScoreDirector(parent);
    return children;
  }

  public static final class NullThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      return null;
    }
  }

  public static final class RefuseSecondThreadFactory implements ThreadFactory {
    private static DelayedFactoryFixture fixture;

    @Override
    public Thread newThread(Runnable runnable) {
      var current = fixture;
      if (current.calls.getAndIncrement() == 0) {
        current.worker =
            new Thread(
                () -> {
                  current.workerStarted.countDown();
                  boolean interrupted = false;
                  try {
                    while (true) {
                      try {
                        current.releaseWorker.await();
                        break;
                      } catch (InterruptedException e) {
                        interrupted = true;
                      }
                    }
                    runnable.run();
                  } finally {
                    if (interrupted) {
                      Thread.currentThread().interrupt();
                    }
                  }
                },
                "accepted-unstarted-island");
        return current.worker;
      }
      try {
        if (!current.workerStarted.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("First island worker wrapper did not start.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      current.refused.countDown();
      throw current.failure;
    }
  }

  private static final class DelayedFactoryFixture {
    private final AtomicInteger calls = new AtomicInteger();
    private final CountDownLatch workerStarted = new CountDownLatch(1);
    private final CountDownLatch releaseWorker = new CountDownLatch(1);
    private final CountDownLatch refused = new CountDownLatch(1);
    private final IllegalStateException failure =
        new IllegalStateException("Second island worker refused.");
    private volatile Thread worker;
  }
}
