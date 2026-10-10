package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Factory-built ILS with assignment, membership and shadow checks independent of Bavet. */
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(60)
class IteratedLocalSearchIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void orderedMoveWorkersMatchSerialAssignmentsAndPrimitiveStepTrace(String shape) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    var serial = run(workload, shape, "NONE");
    assertThat(serial.steps()).isNotEmpty();
    for (var threads : List.of("1", "2", "4")) {
      assertThat(run(workload, shape, threads))
          .as("%s with %s move workers", shape, threads)
          .isEqualTo(serial);
    }
  }

  private static <S> RunResult run(Workload<S> workload, String shape, String threads) {
    var solver =
        (DefaultSolver<S>) SolverFactory.<S>create(config(workload, shape, threads)).buildSolver();
    var steps = new ArrayList<String>();
    var starts = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<S> step) {
            starts.add(step.getStepIndex());
          }

          @Override
          public void stepEnded(AbstractStepScope<S> step) {
            assertThat(step).isInstanceOf(IteratedLocalSearchStepScope.class);
            assertThat(step.getStepIndex()).isEqualTo(steps.size());
            assertThat(workload.recompute(step.getWorkingSolution()))
                .isEqualTo(step.getScore().raw());
            steps.add(step.getScore() + ":" + workload.state(step.getWorkingSolution()));
          }
        });
    var problem = workload.createProblem(16);
    var initialScore = workload.recompute(problem);
    var result = solver.solve(problem);
    assertThat(workload.recompute(result)).isEqualTo(workload.score(result));
    assertThat(workload.score(result)).isGreaterThanOrEqualTo(initialScore);
    // Aborted decisions may have started callbacks; completed steps must each have exactly one
    // start.
    for (int index = 0; index < steps.size(); index++) {
      assertThat(starts).contains(index);
    }
    return new RunResult(workload.score(result), workload.state(result), steps);
  }

  static Stream<Arguments> islandModes() {
    return Stream.of("basic", "list", "mixed")
        .flatMap(
            shape -> Stream.of("NONE", "1", "2", "4").map(threads -> Arguments.of(shape, threads)));
  }

  @ParameterizedTest
  @MethodSource("islandModes")
  void islandsAndMoveWorkersReturnCompleteReplayableSolutions(String shape, String threads) {
    solveIslands(AlnsMoveThreadingWorkload.named(shape), shape, threads);
  }

  private static <S> void solveIslands(Workload<S> workload, String shape, String threads) {
    var config = config(workload, shape, "NONE");
    var phase = (IteratedLocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    phase.setMoveThreadCount(null);
    config.withPhases(
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount(threads)
            .withMigrationFrequency(1)
            .withPhaseConfigList(List.of(phase)));
    var problem = workload.createProblem(16);
    var initialScore = workload.recompute(problem);
    var result = SolverFactory.<S>create(config).buildSolver().solve(problem);
    assertThat(workload.recompute(result)).isEqualTo(workload.score(result));
    assertThat(workload.score(result)).isGreaterThanOrEqualTo(initialScore);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void repeatedSolvesStartFreshEpisodesAndStopTheirWorkers(String threads)
      throws InterruptedException {
    RecordingThreadFactory.threads.clear();
    var workload = new BasicWorkload();
    var solver =
        SolverFactory.<BasicSolution>create(
                config(workload, "basic", threads)
                    .withThreadFactoryClass(RecordingThreadFactory.class))
            .buildSolver();
    var first = solver.solve(workload.createProblem(16));
    var firstState = workload.state(first);
    var second = solver.solve(workload.createProblem(16));
    assertThat(workload.recompute(first)).isEqualTo(first.getScore());
    assertThat(workload.recompute(second)).isEqualTo(second.getScore());
    assertThat(workload.state(first)).isEqualTo(firstState);
    assertThat(workload.state(second)).isEqualTo(firstState);
    assertThat(RecordingThreadFactory.threads).hasSize(threads.equals("NONE") ? 0 : 4);
    for (var thread : RecordingThreadFactory.threads) {
      thread.join(5_000);
      assertThat(thread.isAlive()).as("%s stopped", thread.getName()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void cancellationAfterCommittedPrimitiveStepPreservesPublishedBest(String threads) {
    var workload = new BasicWorkload();
    var solver =
        (DefaultSolver<BasicSolution>)
            SolverFactory.<BasicSolution>create(config(workload, "basic", threads)).buildSolver();
    var scores = new ArrayList<SimpleScore>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            scores.add(workload.recompute(step.getWorkingSolution()));
            assertThat(solver.terminateEarly()).isTrue();
          }
        });
    var problem = workload.createProblem(16);
    var initialScore = workload.recompute(problem);
    var result = solver.solve(problem);
    assertThat(scores).hasSize(1);
    assertThat(workload.recompute(result)).isEqualTo(result.getScore());
    assertThat(result.getScore()).isGreaterThanOrEqualTo(initialScore);
    assertThat(result.getScore()).isGreaterThanOrEqualTo(scores.getFirst());
    assertThat(solver.isSolving()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void problemChangeRestartsWithNewPinsAndAssignments(String threads) {
    var workload = new BasicWorkload();
    var solver =
        (DefaultSolver<BasicSolution>)
            SolverFactory.<BasicSolution>create(config(workload, "basic", threads)).buildSolver();
    var queued = new AtomicBoolean();
    var phases = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<BasicSolution> phase) {
            phases.add(workload.state(phase.getWorkingSolution()));
          }

          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> step) {
            if (queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    for (var job : solution.getJobs()) {
                      if (job.getId() % 2 == 0) {
                        director.changeVariable(
                            job,
                            "machine",
                            workingJob -> workingJob.setMachine(solution.getMachines().getFirst()));
                        director.changeProblemProperty(
                            job, workingJob -> workingJob.setPinned(true));
                      }
                    }
                  });
            }
          }
        });
    var result = solver.solve(workload.createProblem(16));
    assertThat(phases).hasSize(2);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(result.getJobs())
        .filteredOn(job -> job.getId() % 2 == 0)
        .allSatisfy(
            job -> {
              assertThat(job.isPinned()).isTrue();
              assertThat(job.getMachine()).isSameAs(result.getMachines().getFirst());
            });
    assertThat(workload.recompute(result)).isEqualTo(result.getScore());
  }

  @Test
  void mandatoryUnassignedInputFailsBeforeImprovement() {
    var workload = new BasicWorkload();
    var problem = workload.createProblem(16);
    problem.getJobs().get(1).setMachine(null);
    assertThatThrownBy(
            () ->
                SolverFactory.<BasicSolution>create(config(workload, "basic", "NONE"))
                    .buildSolver()
                    .solve(problem))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("initial");
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void pinnedAssignmentsSurvivePerturbationsEpisodesAndRestoration(String threads) {
    var workload = new BasicWorkload();
    var problem = workload.createProblem(16);
    problem.getJobs().getFirst().setPinned(true);
    problem.getJobs().get(3).setPinned(true);
    var result =
        SolverFactory.<BasicSolution>create(config(workload, "basic", threads))
            .buildSolver()
            .solve(problem);
    assertThat(result.getJobs().getFirst().getMachine()).isSameAs(result.getMachines().getFirst());
    assertThat(result.getJobs().get(3).getMachine()).isSameAs(result.getMachines().get(3));
    assertThat(workload.recompute(result)).isEqualTo(result.getScore());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void fullyPinnedInputSkipsSearchWithoutCommittedMoves(String threads) {
    var workload = new BasicWorkload();
    var problem = workload.createProblem(16);
    problem.getJobs().forEach(job -> job.setPinned(true));
    var originalState = workload.state(problem);
    var solver =
        (DefaultSolver<BasicSolution>)
            SolverFactory.<BasicSolution>create(config(workload, "basic", threads)).buildSolver();
    var scopes = new ArrayList<IteratedLocalSearchPhaseScope<BasicSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<BasicSolution> phase) {
            scopes.add((IteratedLocalSearchPhaseScope<BasicSolution>) phase);
          }
        });
    var result = solver.solve(problem);
    assertThat(workload.state(result)).isEqualTo(originalState);
    assertThat(workload.recompute(result)).isEqualTo(result.getScore());
    assertThat(scopes).isEmpty();
  }

  static <S> SolverConfig config(Workload<S> workload, String shape, String threads) {
    return workload
        .solverConfig(threads, 7L, 1, new TerminationConfig(), EnvironmentMode.TRACKED_FULL_ASSERT)
        .withPhases(phase(shape).withMoveThreadCount(threads));
  }

  static IteratedLocalSearchPhaseConfig phase(String shape) {
    return new IteratedLocalSearchPhaseConfig()
        .withLocalSearch(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves(shape))
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(4))
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)))
        .withPerturbationMoveSelectorConfig(moves(shape))
        .withPerturbationStrengths(1, 2)
        .withPerturbationAttemptLimit(12)
        .withEpisodeCandidateAttemptLimit(40)
        .withIterationCountLimit(4);
  }

  private static MoveSelectorConfig<?> moves(String shape) {
    return switch (shape) {
      case "basic" -> new ChangeMoveSelectorConfig();
      case "list" -> new ListChangeMoveSelectorConfig();
      case "mixed" ->
          new UnionMoveSelectorConfig()
              .withMoveSelectors(
                  new ChangeMoveSelectorConfig()
                      .withEntitySelectorConfig(new EntitySelectorConfig(Job.class)),
                  new ListChangeMoveSelectorConfig());
      default -> throw new IllegalArgumentException(shape);
    };
  }

  public static final class RecordingThreadFactory implements ThreadFactory {
    static final List<Thread> threads = new CopyOnWriteArrayList<>();

    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "ils-integration-worker-" + threads.size());
      threads.add(thread);
      return thread;
    }
  }

  private record RunResult(SimpleScore score, String state, List<String> steps) {}
}
