package greycos.solver.core.impl.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PartitionTaskTest {

  @Test
  @SuppressWarnings("unchecked")
  void cancelledBeforeStartClosesDirectorExactlyOnce() {
    var solver = (PartitionSolver<TestdataSolution>) mock(PartitionSolver.class);
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(director);
    when(solver.getSolverScope()).thenReturn(scope);
    var solution = new TestdataSolution();
    var task = new PartitionTask<>(solver, solution);

    task.cancelBeforeStart();
    task.cancelBeforeStart();
    assertThat(task.call()).isZero();

    verify(director).close();
    verify(solver, never()).solve(solution);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cancellationDoesNotCloseARunningTasksDirector() throws Exception {
    var solver = (PartitionSolver<TestdataSolution>) mock(PartitionSolver.class);
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(director);
    when(solver.getSolverScope()).thenReturn(scope);
    var solution = new TestdataSolution();
    var started = new CountDownLatch(1);
    var finish = new CountDownLatch(1);
    when(solver.solve(solution))
        .thenAnswer(
            invocation -> {
              started.countDown();
              assertThat(finish.await(3, TimeUnit.SECONDS)).isTrue();
              return solution;
            });
    when(solver.getScoreCalculationCount()).thenReturn(123L);
    var task = new PartitionTask<>(solver, solution);
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(task);
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      task.cancelBeforeStart();
      verify(director, never()).close();
      finish.countDown();
      assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(123L);
      task.cancelBeforeStart();
      verify(director, never()).close();
    } finally {
      finish.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }
}
