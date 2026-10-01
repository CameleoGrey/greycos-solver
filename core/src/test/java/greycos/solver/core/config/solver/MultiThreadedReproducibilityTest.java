package greycos.solver.core.config.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Compares construction and local-search trajectories using assignment and machine-load scores. */
@Tag("slow")
class MultiThreadedReproducibilityTest {

  private static final int PROBLEM_SIZE = 24;
  private static final int STEP_LIMIT = 60;

  @Test
  @Timeout(60)
  void multiThreadedSolvingIsReproducible() {
    assertReproducible("4");
  }

  @Test
  @Timeout(60)
  void multiThreadedSolvingIsReproducibleWithAutoThreadCount() {
    // AUTO is compared on the same machine, where its resolved worker count remains fixed.
    assertReproducible(SolverConfig.MOVE_THREAD_COUNT_AUTO);
  }

  private void assertReproducible(String threads) {
    var workload = new BasicWorkload();
    var config =
        workload.solverConfig(
            threads,
            37L,
            16,
            new TerminationConfig().withStepCountLimit(STEP_LIMIT),
            EnvironmentMode.REPRODUCIBLE);
    var phases = new ArrayList<>(config.getPhaseConfigList());
    phases.add(0, new ConstructionHeuristicPhaseConfig());
    config.setPhaseConfigList(phases);
    var factory = SolverFactory.<BasicSolution>create(config);

    var first = solve(factory, workload);
    var second = solve(factory, workload);

    assertThat(first).isEqualTo(second);
    assertThat(first.constructionSteps()).hasSize(PROBLEM_SIZE);
    assertThat(first.localSearchSteps()).hasSize(STEP_LIMIT);
    assertThat(first.constructionSteps().stream().map(StepTrace::score).distinct().count())
        .isGreaterThan(1L);
    assertThat(first.finalScore()).isLessThan(SimpleScore.ZERO);
  }

  private Trace solve(SolverFactory<BasicSolution> factory, BasicWorkload workload) {
    var problem = workload.createProblem(PROBLEM_SIZE);
    problem.getJobs().forEach(job -> job.setMachine(null));
    problem.setScore(null);
    var solver = (DefaultSolver<BasicSolution>) factory.buildSolver();
    var construction = new ArrayList<StepTrace>();
    var localSearch = new ArrayList<StepTrace>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> stepScope) {
            var workingSolution = stepScope.getWorkingSolution();
            long unassigned =
                workingSolution.getJobs().stream().filter(job -> job.getMachine() == null).count();
            var expectedScore = recomputePartialScore(workingSolution);
            assertThat(stepScope.getScore().unassignedCount()).isEqualTo(unassigned);
            assertThat(stepScope.getScore().raw()).isEqualTo(expectedScore);
            if (stepScope instanceof ConstructionHeuristicStepScope<BasicSolution> step) {
              construction.add(
                  new StepTrace(
                      step.getStepIndex(),
                      moveSignature(step.getStep()),
                      step.getSelectedMoveCount(),
                      0L,
                      unassigned,
                      expectedScore,
                      workload.state(workingSolution)));
            } else if (stepScope instanceof LocalSearchStepScope<BasicSolution> step) {
              assertThat(unassigned).isZero();
              assertThat(expectedScore).isEqualTo(workload.recompute(workingSolution));
              localSearch.add(
                  new StepTrace(
                      step.getStepIndex(),
                      moveSignature(step.getStep()),
                      step.getSelectedMoveCount(),
                      step.getAcceptedMoveCount(),
                      unassigned,
                      expectedScore,
                      workload.state(workingSolution)));
            }
          }
        });
    var solution = solver.solve(problem);
    assertThat(solution.getScore()).isEqualTo(workload.recompute(solution));
    return new Trace(construction, localSearch, workload.state(solution), solution.getScore());
  }

  private static SimpleScore recomputePartialScore(BasicSolution solution) {
    long[] loads = new long[solution.getMachines().size()];
    long penalty = 0L;
    for (var job : solution.getJobs()) {
      var machine = job.getMachine();
      if (machine == null) {
        continue;
      }
      assertThat(machine).isSameAs(solution.getMachines().get(machine.id()));
      loads[machine.id()] += job.getUnits();
      penalty += Math.abs((long) machine.id() - job.getPreferredMachine()) * job.getUnits();
    }
    for (long load : loads) {
      penalty += load * load;
    }
    return SimpleScore.of(-penalty);
  }

  private static String moveSignature(Move<BasicSolution> move) {
    // The fixture's entities and values describe themselves using stable business IDs.
    return move.describe() + ":" + move.getPlanningEntities() + ":" + move.getPlanningValues();
  }

  private record StepTrace(
      int stepIndex,
      String move,
      long selectedMoves,
      long acceptedMoves,
      long unassigned,
      SimpleScore score,
      String assignments) {}

  private record Trace(
      List<StepTrace> constructionSteps,
      List<StepTrace> localSearchSteps,
      String finalAssignments,
      SimpleScore finalScore) {}
}
