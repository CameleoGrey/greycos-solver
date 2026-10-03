package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SimulatedAnnealingWorkerLifecycleTest {

  private static final List<Thread> WORKERS = new CopyOnWriteArrayList<>();

  @Test
  @Timeout(20)
  void unsupportedCoolingClosesWorkersStartedBeforeTheFirstStep() throws InterruptedException {
    WORKERS.clear();
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withMoveThreadCount("2")
            .withThreadFactoryClass(RecordingThreadFactory.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withAcceptorConfig(
                        new LocalSearchAcceptorConfig()
                            .withSimulatedAnnealingStartingTemperature("10"))
                    .withTerminationConfig(new TerminationConfig().withDiminishedReturns()));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var decider = (MultiThreadedLocalSearchDecider<TestdataSolution>) phase.getDecider();

    try {
      var failure = catchThrowable(() -> solver.solve(TestdataSolution.generateSolution(3, 6)));

      assertThat(failure)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("simulated annealing timeGradient (-1.0)")
          .hasMessageContaining("phase (0)")
          .hasMessageContaining("Configure a termination that provides a time gradient")
          .hasMessageContaining("diminished returns or asynchronous termination alone");
      assertThat(WORKERS).hasSize(2);
      // Executor termination may precede the final return from a worker's Thread.run().
      // Check shutdown before emergency cleanup, while allowing that final return to complete.
      for (var worker : WORKERS) {
        assertThat(worker.join(Duration.ofSeconds(5)))
            .as("Move worker %s terminates after cooling validation fails", worker.getName())
            .isTrue();
      }
      solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
      assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
      assertThat(solver.isSolving()).isFalse();
    } finally {
      if (WORKERS.stream().anyMatch(Thread::isAlive) && decider.moveEvaluationPipeline != null) {
        decider.moveEvaluationPipeline.abort();
      }
      for (var worker : WORKERS) worker.interrupt();
      for (var worker : WORKERS) worker.join(Duration.ofSeconds(2));
      WORKERS.clear();
    }
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      var worker = new Thread(runnable, "unsupported-cooling-worker-" + WORKERS.size());
      WORKERS.add(worker);
      return worker;
    }
  }
}
