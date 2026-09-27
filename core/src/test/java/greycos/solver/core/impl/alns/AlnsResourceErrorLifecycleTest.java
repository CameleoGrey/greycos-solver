package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class AlnsResourceErrorLifecycleTest {

  private static final List<Thread> WORKERS = new CopyOnWriteArrayList<>();
  private static final List<String> CLOSED = new ArrayList<>();
  private static AssertionError firstCloseFailure;
  private static RuntimeException secondCloseFailure;

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void closeErrorStillClosesAllResourcesAfterRepairWork(int failureMode) throws Exception {
    WORKERS.clear();
    CLOSED.clear();
    firstCloseFailure = new AssertionError("Acceptance cleanup failed.");
    secondCloseFailure = new IllegalStateException("Selection cleanup failed.");
    var bodyFailure =
        failureMode == 2
            ? firstCloseFailure
            : new AssertionError("Body failed after actual repair attempts.");
    boolean bodyFails = failureMode == 1 || failureMode == 2;
    var workload = new BasicWorkload();
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                "2",
                0L,
                2,
                AlnsRepairOperatorType.RANDOMIZED_GREEDY,
                new TerminationConfig().withStepCountLimit(1),
                EnvironmentMode.NO_ASSERT)
            .withThreadFactoryClass(RecordingThreadFactory.class);
    var phaseConfig = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    phaseConfig
        .withMoveThreadingMode(AlnsMoveThreadingMode.REPAIR_ATTEMPTS)
        .withRepairAttemptCount(2)
        .withAcceptanceType(null)
        .withAcceptancePolicyClass(ClosingAcceptance.class)
        .withSelectionPolicyClass(ClosingSelection.class)
        .withDestroyOperators(
            new AlnsDestroyOperatorConfig()
                .withId("closing")
                .withCustomClass(ClosingDestroy.class)
                .withMinimumDestroyedCount(2)
                .withMaximumDestroyedCount(2));
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var completedSteps = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> stepScope) {
            completedSteps.add(stepScope.getStepIndex());
            if (failureMode == 3) {
              solver.terminateEarly();
              throw new AlnsTerminationException();
            }
            if (bodyFails) throw bodyFailure;
          }
        });

    try {
      var thrown = catchThrowable(() -> solver.solve(workload.createProblem(12)));
      var alive = WORKERS.stream().filter(Thread::isAlive).toList();
      var phase = (DefaultAlnsPhase<BasicSolution>) solver.getPhaseList().getFirst();
      assertThat(completedSteps).containsExactly(0);
      assertThat(phase.getRepairAttemptDiagnostics().completed()).isEqualTo(2);
      assertThat(thrown).isSameAs(bodyFails ? bodyFailure : firstCloseFailure);
      if (failureMode == 1)
        assertThat(bodyFailure.getSuppressed()).containsExactly(firstCloseFailure);
      assertThat(firstCloseFailure.getSuppressed()).containsExactly(secondCloseFailure);
      assertThat(CLOSED).containsExactly("acceptance", "selection", "destroy");
      assertThat(WORKERS).hasSize(2);
      assertThat(alive).isEmpty();
      assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    } finally {
      // Baseline implementations leak the repair executor after an operator close Error.
      for (var thread : WORKERS) thread.interrupt();
      for (var thread : WORKERS) thread.join(2000);
      WORKERS.clear();
    }
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "alns-resource-error-worker-" + WORKERS.size());
      WORKERS.add(thread);
      return thread;
    }
  }

  public static final class ClosingDestroy
      implements AlnsDestroyOperator<BasicSolution, SimpleScore>, AutoCloseable {
    @Override
    public List<AlnsTarget<BasicSolution>> select(
        AlnsContext<BasicSolution, SimpleScore> context, int size) {
      return context.targets().subList(0, size);
    }

    @Override
    public void close() {
      CLOSED.add("destroy");
      // A shared failure instance must not trigger self-suppression or stop later cleanup.
      throw firstCloseFailure;
    }
  }

  public static final class ClosingSelection
      implements AlnsSelectionPolicy<SimpleScore>, AutoCloseable {
    @Override
    public AlnsOperatorPair select(List<AlnsOperatorPair> eligiblePairs, RandomGenerator random) {
      return eligiblePairs.getFirst();
    }

    @Override
    public void close() {
      CLOSED.add("selection");
      throw secondCloseFailure;
    }
  }

  public static final class ClosingAcceptance
      implements AlnsAcceptancePolicy<SimpleScore>, AutoCloseable {
    @Override
    public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
      return true;
    }

    @Override
    public void close() {
      CLOSED.add("acceptance");
      throw firstCloseFailure;
    }
  }
}
