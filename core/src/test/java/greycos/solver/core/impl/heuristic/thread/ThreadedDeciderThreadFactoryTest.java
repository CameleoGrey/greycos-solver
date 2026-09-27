package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(15)
class ThreadedDeciderThreadFactoryTest {

  @ParameterizedTest
  @CsvSource({"true, 0", "true, 1", "false, 0", "false, 1"})
  @SuppressWarnings("unchecked")
  void nullFactoryResultFailsPromptlyAndClosesAlreadyStartedWorkers(
      boolean constructionHeuristic, int workersBeforeNull) throws Exception {
    InnerScoreDirector<Object, SimpleScore> parent = mock(InnerScoreDirector.class);
    InnerScoreDirector<Object, SimpleScore> child = mock(InnerScoreDirector.class);
    var childStarted = new CountDownLatch(1);
    when(child.calculateScore())
        .thenAnswer(
            invocation -> {
              childStarted.countDown();
              return InnerScore.fullyAssigned(SimpleScore.ZERO);
            });
    when(parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)).thenReturn(child);
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(parent);
    var factoryCalls = new AtomicInteger();
    List<Thread> workers = new CopyOnWriteArrayList<>();
    ThreadFactory factory =
        runnable -> {
          if (factoryCalls.getAndIncrement() >= workersBeforeNull) {
            if (workersBeforeNull > 0) {
              try {
                if (!childStarted.await(3, TimeUnit.SECONDS)) {
                  throw new AssertionError(
                      "First worker did not initialize before factory rejection.");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted before factory rejection.", e);
              }
            }
            return null;
          }
          var worker = new Thread(runnable, "test-null-factory-worker");
          worker.setDaemon(true);
          workers.add(worker);
          return worker;
        };
    var result =
        new FutureTask<Throwable>(
            () -> {
              // The pipeline must be constructed on its coordinator thread.
              try {
                if (constructionHeuristic) {
                  var decider =
                      new MultiThreadedConstructionHeuristicDecider<Object>(
                          "",
                          PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
                          mock(ConstructionHeuristicForager.class),
                          factory,
                          2,
                          2);
                  decider.phaseStarted(new ConstructionHeuristicPhaseScope<>(solverScope, 0));
                } else {
                  var decider =
                      new MultiThreadedLocalSearchDecider<Object>(
                          "",
                          PhaseTermination.bridge(new BasicPlumbingTermination<>(false)),
                          mock(MoveSelectorBasedMoveRepository.class),
                          mock(Acceptor.class),
                          mock(LocalSearchForager.class),
                          factory,
                          2,
                          2);
                  decider.phaseStarted(new LocalSearchPhaseScope<>(solverScope, 0));
                }
                return null;
              } catch (Throwable failure) {
                return failure;
              }
            });
    var coordinator = new Thread(result, "test-null-factory-coordinator");
    coordinator.setDaemon(true);
    coordinator.start();
    try {
      Throwable failure;
      try {
        failure = result.get(5, TimeUnit.SECONDS);
      } catch (TimeoutException timeout) {
        throw new AssertionError(
            "Null thread factory result left the coordinator waiting for a missing worker.",
            timeout);
      }
      assertThat(failure)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("returned null")
          .hasMessageContaining(constructionHeuristic ? "Construction Heuristic" : "Local Search");
      assertThat(factoryCalls).hasValue(workersBeforeNull + 1);
    } finally {
      // Also bounds cleanup on the unfixed implementation, which waits for the missing worker.
      coordinator.interrupt();
      coordinator.join(5000);
      assertThat(coordinator.isAlive()).isFalse();
      for (var worker : workers) {
        worker.join(3000);
        assertThat(worker.isAlive()).isFalse();
      }
      if (workersBeforeNull > 0) {
        verify(child).close();
      }
    }
  }
}
