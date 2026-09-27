package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class IslandAgentOwnershipTest {

  @Test
  @SuppressWarnings("unchecked")
  void cancellationBeforeRunClosesAndCountsDownOnlyOnce() {
    var scope = new SolverScope<Object>();
    var director = (InnerScoreDirector<Object, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var latch = new CountDownLatch(2);
    var agent = newAgent(scope, latch);

    agent.cancelBeforeStart();
    agent.cancelBeforeStart();
    agent.run();

    verify(director).close();
    assertThat(latch.getCount()).isEqualTo(1);
    assertThat(agent.getStatus()).isEqualTo(AgentStatus.DEAD);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void failedUnstartedCleanupStillReleasesLatchAndCannotCloseTwice(boolean error) {
    var scope = new SolverScope<Object>();
    var director = (InnerScoreDirector<Object, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    Throwable failure =
        error ? new AssertionError("close failed") : new IllegalStateException("close failed");
    doThrow(failure).when(director).close();
    var latch = new CountDownLatch(2);
    var agent = newAgent(scope, latch);

    assertThatThrownBy(agent::cancelBeforeStart).isSameAs(failure);
    assertThatCode(agent::cancelBeforeStart).doesNotThrowAnyException();
    agent.run();

    verify(director).close();
    assertThat(latch.getCount()).isEqualTo(1);
    assertThat(agent.getStatus()).isEqualTo(AgentStatus.DEAD);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cancellationAfterRunClaimsOwnershipLeavesCleanupToWorker() throws Exception {
    var scope = (SolverScope<Object>) mock(SolverScope.class);
    var director = (InnerScoreDirector<Object, ?>) mock(InnerScoreDirector.class);
    var solver = (IslandSolver<Object>) mock(IslandSolver.class);
    doReturn(director).when(scope).getScoreDirector();
    when(scope.getSolver()).thenReturn(solver);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var failure = new IllegalStateException("startup failed after ownership transfer");
    doAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Test did not release agent");
              throw failure;
            })
        .when(scope)
        .transferWorkingRandomOwnershipToCurrentThread();
    doAnswer(
            invocation -> {
              director.close();
              return null;
            })
        .when(solver)
        .solvingError(scope, failure);
    var latch = new CountDownLatch(2);
    var agent = newAgent(scope, latch);
    var result = new FutureTask<Throwable>(() -> catchThrowable(agent::run));
    var worker = new Thread(result, "agent-ownership-test");
    worker.start();
    try {
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      agent.cancelBeforeStart();
      verify(director, never()).close();
      assertThat(latch.getCount()).isEqualTo(2);
      release.countDown();
      assertThat(result.get(5, TimeUnit.SECONDS)).hasCause(failure);
      agent.cancelBeforeStart();
      verify(director).close();
      assertThat(latch.getCount()).isEqualTo(1);
    } finally {
      release.countDown();
      worker.join(5000);
      assertThat(worker.isAlive()).isFalse();
    }
  }

  private static IslandAgent<Object> newAgent(SolverScope<Object> scope, CountDownLatch latch) {
    return new IslandAgent<>(
        0,
        List.of(),
        new Object(),
        new SharedGlobalState<>(),
        new BoundedChannel<>(1),
        new BoundedChannel<>(1),
        IslandModelConfig.builder().withIslandCount(1).build(),
        DefaultRandomSource.seeded(0L),
        scope,
        latch);
  }
}
