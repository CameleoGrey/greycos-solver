package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.partitionedsearch.PartitionSolver;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class SolverErrorLifecycleTest {

  private static final List<Thread> WORKERS = new CopyOnWriteArrayList<>();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void coordinatorErrorClosesMoveWorkersAndDirector(boolean constructionHeuristic)
      throws Exception {
    WORKERS.clear();
    var workload = new BasicWorkload();
    var config =
        workload
            .solverConfig(
                "2",
                37,
                4,
                new TerminationConfig().withStepCountLimit(2),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class);
    var problem = workload.createProblem(20);
    if (constructionHeuristic) {
      config.withPhases(new ConstructionHeuristicPhaseConfig());
      problem.getJobs().forEach(job -> job.setMachine(null));
      problem.setScore(null);
    }
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var original = new AssertionError("Coordinator failed after a completed step.");
    var cleanupFailure = new AssertionError("Error listener cleanup failed.");
    var observed = new ArrayList<Throwable>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> stepScope) {
            assertThat(WORKERS).hasSize(2);
            throw original;
          }

          @Override
          public void solvingError(SolverScope<BasicSolution> scope, Throwable failure) {
            observed.add(failure);
            throw cleanupFailure;
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<BasicSolution> scope, Throwable failure) {
            observed.add(failure);
          }
        });

    try {
      var thrown = catchThrowable(() -> solver.solve(problem));
      assertThat(thrown).isSameAs(original);
      assertThat(WORKERS).hasSize(2);
      // Executor termination can precede the final return from a worker's Thread.run().
      // Wait before emergency cleanup so genuine worker leaks still fail the assertion.
      WORKERS.forEach(SolverErrorLifecycleTest::assertThreadStopped);
      assertThat(observed).containsExactly(original, original);
      assertThat(original.getSuppressed()).containsExactly(cleanupFailure);
      assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
      assertThat(solver.isSolving()).isFalse();
    } finally {
      if (WORKERS.stream().anyMatch(Thread::isAlive)) {
        var phase = solver.getPhaseList().getFirst();
        var deciderField = phase.getClass().getDeclaredField("decider");
        deciderField.setAccessible(true);
        var decider = deciderField.get(phase);
        var pipelineField = decider.getClass().getDeclaredField("moveEvaluationPipeline");
        pipelineField.setAccessible(true);
        ((MoveEvaluationPipeline<?>) pipelineField.get(decider)).abort();
      }
      for (var thread : WORKERS) thread.interrupt();
      for (var thread : WORKERS) thread.join(2000);
      WORKERS.clear();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void selfRemovingListenerStillNotifiesTheRegisteredListenersAndPhases() {
    var events = new ArrayList<String>();
    var original = new AssertionError("Solve failed.");
    var cleanupFailure = new AssertionError("Self-removing listener failed.");
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var phase = (Phase<TestdataSolution>) mock(Phase.class);
    doAnswer(
            invocation -> {
              events.add("phase");
              return null;
            })
        .when(phase)
        .solvingError(any(), any());
    doAnswer(
            invocation -> {
              events.add("director");
              return null;
            })
        .when(director)
        .close();
    var solver = failingPartition(scope, List.of(phase), original);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener-1");
            solver.removePhaseLifecycleListener(this);
            throw cleanupFailure;
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener-2");
          }
        });

    assertThat(catchThrowable(() -> solver.solve(new TestdataSolution()))).isSameAs(original);
    assertThat(events).containsExactly("listener-1", "listener-2", "phase", "director");
    assertThat(original.getSuppressed()).containsExactly(cleanupFailure);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cleanupContinuesInOrderAndPreservesPrimaryFailure() {
    var events = new ArrayList<String>();
    var original = new IllegalStateException("Original solve failure.");
    var listenerFailure = new AssertionError("Listener cleanup failed.");
    var phaseFailure = new AssertionError("Phase cleanup failed.");
    var directorFailure = new AssertionError("Director cleanup failed.");
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var firstPhase = (Phase<TestdataSolution>) mock(Phase.class);
    var secondPhase = (Phase<TestdataSolution>) mock(Phase.class);
    doAnswer(
            invocation -> {
              events.add("phase-1");
              throw phaseFailure;
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
    doAnswer(
            invocation -> {
              events.add("director");
              throw directorFailure;
            })
        .when(director)
        .close();
    var solver = failingPartition(scope, List.of(firstPhase, secondPhase), original);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener-1");
            throw listenerFailure;
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener-2");
          }
        });

    var thrown = catchThrowable(() -> solver.solve(new TestdataSolution()));

    assertThat(thrown).isSameAs(original);
    assertThat(events)
        .containsExactly("listener-1", "listener-2", "phase-1", "phase-2", "director");
    assertThat(original.getSuppressed())
        .containsExactly(listenerFailure, phaseFailure, directorFailure);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void partitionFailureDoesNotSuppressItself(boolean error) {
    Throwable original =
        error
            ? new AssertionError("Original error.")
            : new IllegalStateException("Original failure.");
    var events = new ArrayList<String>();
    var scope = new SolverScope<TestdataSolution>();
    var director = (InnerScoreDirector<TestdataSolution, ?>) mock(InnerScoreDirector.class);
    scope.setScoreDirector(director);
    var phase = (Phase<TestdataSolution>) mock(Phase.class);
    doAnswer(
            invocation -> {
              events.add("phase");
              throw original;
            })
        .when(phase)
        .solvingError(any(), any());
    doAnswer(
            invocation -> {
              events.add("director");
              throw original;
            })
        .when(director)
        .close();
    var solver = failingPartition(scope, List.of(phase), original);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingError(SolverScope<TestdataSolution> ignored, Throwable failure) {
            events.add("listener");
            throwUnchecked(original);
          }
        });

    var thrown = catchThrowable(() -> solver.solve(new TestdataSolution()));

    assertThat(thrown).isSameAs(original);
    assertThat(events).containsExactly("listener", "phase", "director");
    assertThat(original.getSuppressed()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static PartitionSolver<TestdataSolution> failingPartition(
      SolverScope<TestdataSolution> scope,
      List<Phase<TestdataSolution>> phases,
      Throwable failure) {
    scope.setWorkingRandom(DefaultRandomSource.seeded(0L).splitForChildThread());
    return new PartitionSolver<TestdataSolution>(
        EnvironmentMode.NO_ASSERT,
        mock(ScoreDirectorFactory.class),
        mock(BestSolutionRecaller.class),
        null,
        phases,
        scope,
        0) {
      @Override
      public void solvingStarted(SolverScope<TestdataSolution> ignored) {}

      @Override
      protected void runPhases(SolverScope<TestdataSolution> ignored) {
        throwUnchecked(failure);
      }
    };
  }

  private static void throwUnchecked(Throwable failure) {
    if (failure instanceof Error error) throw error;
    throw (RuntimeException) failure;
  }

  private static void assertThreadStopped(Thread thread) {
    boolean interruptedBefore = Thread.interrupted();
    try {
      assertThat(thread.join(Duration.ofSeconds(5)))
          .as("Thread %s terminates", thread.getName())
          .isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(
          "Interrupted while waiting for thread " + thread.getName() + " to terminate.",
          interrupted);
    } finally {
      if (interruptedBefore) Thread.currentThread().interrupt();
    }
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "lifecycle-error-worker-" + WORKERS.size());
      WORKERS.add(thread);
      return thread;
    }
  }
}
