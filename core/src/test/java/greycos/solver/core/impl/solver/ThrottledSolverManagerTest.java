package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.phase.PhaseCommandContext;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class ThrottledSolverManagerTest {

  @Test
  void sequentialJobsFromOneBuilderKeepTheirThrottle() throws Exception {
    var first = new JobGate();
    var second = new JobGate();
    var gates = Map.of("first", first, "second", second);
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (SolverManager<TestdataSolution> manager =
        SolverManager.create(
            gatedSolverConfig(gates), new SolverManagerConfig().withParallelSolverCount("1"))) {
      var builder =
          manager
              .solveBuilder()
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution(id.toString()))
              .withThrottledBestSolutionEventConsumer(
                  event -> gates.get(event.solution().getCode()).consumed.countDown(),
                  Duration.ofDays(1))
              .withExceptionHandler((id, error) -> errors.add(error));
      try {
        var firstJob = builder.withProblemId("first").run();
        assertThat(first.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(first.consumed.await(100, TimeUnit.MILLISECONDS)).isFalse();
        first.release.countDown();
        firstJob.getFinalBestSolution();
        assertThat(first.consumed.getCount()).isZero();

        var secondJob = builder.withProblemId("second").run();
        assertThat(second.started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondJob.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        assertThat(second.consumed.await(100, TimeUnit.MILLISECONDS)).isFalse();
        second.release.countDown();
        secondJob.getFinalBestSolution();
        assertThat(second.consumed.getCount()).isZero();
      } finally {
        first.release.countDown();
        second.release.countDown();
      }
    }
    assertThat(errors).isEmpty();
  }

  @Test
  void overlappingJobsFromOneBuilderHaveIndependentPendingSolutions() throws Exception {
    var first = new JobGate();
    var second = new JobGate();
    var gates = Map.of("first", first, "second", second);
    var consumed = Collections.synchronizedList(new ArrayList<String>());
    var finalized = Collections.synchronizedList(new ArrayList<String>());
    var errors = Collections.synchronizedList(new ArrayList<Throwable>());
    try (SolverManager<TestdataSolution> manager =
        SolverManager.create(
            gatedSolverConfig(gates), new SolverManagerConfig().withParallelSolverCount("2"))) {
      var builder =
          manager
              .solveBuilder()
              .withProblemFinder(id -> PlannerTestUtils.generateTestdataSolution(id.toString()))
              .withThrottledBestSolutionEventConsumer(
                  event -> {
                    var code = event.solution().getCode();
                    consumed.add(code);
                    gates.get(code).consumed.countDown();
                  },
                  Duration.ofDays(1))
              .withFinalBestSolutionEventConsumer(
                  event -> finalized.add(event.solution().getCode()))
              .withExceptionHandler((id, error) -> errors.add(error));
      try {
        var firstJob = builder.withProblemId("first").run();
        assertThat(first.started.await(5, TimeUnit.SECONDS)).isTrue();
        var secondJob = builder.withProblemId("second").run();
        assertThat(second.started.await(5, TimeUnit.SECONDS)).isTrue();

        first.release.countDown();
        firstJob.getFinalBestSolution();
        assertThat(consumed).containsExactly("first");
        assertThat(finalized).containsExactly("first");
        assertThat(secondJob.getSolverStatus()).isEqualTo(SolverStatus.SOLVING_ACTIVE);
        assertThat(second.consumed.await(100, TimeUnit.MILLISECONDS)).isFalse();

        second.release.countDown();
        secondJob.getFinalBestSolution();
        assertThat(consumed).containsExactly("first", "second");
        assertThat(finalized).containsExactly("first", "second");
      } finally {
        first.release.countDown();
        second.release.countDown();
      }
    }
    assertThat(errors).isEmpty();
  }

  @Test
  void bestConsumerCanCloseItsManager() throws Exception {
    var gate = new JobGate();
    var callbackReturned = new CountDownLatch(1);
    var managerRef = new AtomicReference<SolverManager<TestdataSolution>>();
    var config = gatedSolverConfig(Map.of("problem", gate));
    var manager = SolverManager.<TestdataSolution>create(config);
    managerRef.set(manager);
    try {
      manager
          .solveBuilder()
          .withProblemId("problem")
          .withProblem(PlannerTestUtils.generateTestdataSolution("problem"))
          .withThrottledBestSolutionEventConsumer(
              event -> {
                await(gate.started);
                managerRef.get().close();
                callbackReturned.countDown();
              },
              Duration.ofNanos(1))
          // Manager closure interrupts the deliberately blocked solver phase.
          .withExceptionHandler((id, error) -> {})
          .run();
      assertThat(callbackReturned.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      gate.release.countDown();
      manager.close();
    }
  }

  private static SolverConfig gatedSolverConfig(Map<String, JobGate> gates) {
    return PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new CustomPhaseConfig()
                .withCustomPhaseCommands(
                    (PhaseCommandContext<TestdataSolution> context) -> {
                      var gate = gates.get(context.getWorkingSolution().getCode());
                      gate.started.countDown();
                      await(gate.release);
                    }));
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the solver phase.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted waiting for the solver phase.", e);
    }
  }

  private static final class JobGate {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch consumed = new CountDownLatch(1);
  }
}
