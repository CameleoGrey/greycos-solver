package greycos.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.iteratedlocalsearch.DefaultIteratedLocalSearchPhase;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises real factories, acceptor histories, GLS collectors and persistent worker graphs. */
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(60)
class LocalSearchEpisodeAlgorithmIntegrationTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "LATE_ACCEPTANCE",
        "DIVERSIFIED_LATE_ACCEPTANCE",
        "SIMULATED_ANNEALING",
        "TABU_SEARCH",
        "GREAT_DELUGE",
        "VARIABLE_NEIGHBORHOOD_DESCENT",
        "GUIDED_LOCAL_SEARCH",
        "STEP_COUNTING"
      })
  void algorithmsKeepNativeScoresAndRepeatableEpisodeTraces(String algorithm) {
    var serial = run(algorithm, "NONE");
    var threaded = run(algorithm, "2");
    assertThat(threaded).isEqualTo(serial);
  }

  private static Trace run(String algorithm, String threads) {
    var workload = new BasicWorkload();
    var inner =
        inner(algorithm).withTerminationConfig(new TerminationConfig().withStepCountLimit(4));
    var phaseConfig =
        new IteratedLocalSearchPhaseConfig()
            .withMoveThreadCount(threads)
            .withLocalSearch(inner)
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(24)
            .withEpisodeCandidateAttemptLimit(160)
            .withIterationCountLimit(5);
    var config =
        workload
            .solverConfig(
                threads, 19L, 1, new TerminationConfig(), EnvironmentMode.TRACKED_FULL_ASSERT)
            .withPreviewFeature(PreviewFeature.DIVERSIFIED_LATE_ACCEPTANCE)
            .withPhases(phaseConfig);
    var solver =
        (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
    var steps = new ArrayList<String>();
    var workingBelowBest = new ArrayList<Boolean>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<BasicSolution> scope) {
            var step = (IteratedLocalSearchStepScope<BasicSolution>) scope;
            if (step.getOrigin() == IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH) {
              workingBelowBest.add(
                  workload
                          .recompute(step.getWorkingSolution())
                          .compareTo(step.getPhaseScope().<SimpleScore>getBestScore().raw())
                      < 0);
            }
          }

          @Override
          public void stepEnded(AbstractStepScope<BasicSolution> scope) {
            var step = (IteratedLocalSearchStepScope<BasicSolution>) scope;
            assertThat(workload.recompute(step.getWorkingSolution()))
                .isEqualTo(step.getScore().raw());
            steps.add(
                step.getOrigin()
                    + ":"
                    + step.getScore()
                    + ":"
                    + workload.state(step.getWorkingSolution()));
          }
        });
    Trace first = null;
    for (int solve = 0; solve < 2; solve++) {
      steps.clear();
      workingBelowBest.clear();
      var problem = workload.createProblem(16);
      var initial = workload.recompute(problem);
      var result = solver.solve(problem);
      assertThat(workload.recompute(result)).isEqualTo(result.getScore());
      assertThat(result.getScore()).isGreaterThanOrEqualTo(initial);
      assertThat(steps).anyMatch(step -> step.startsWith("LOCAL_SEARCH:"));
      assertThat(workingBelowBest)
          .as("an episode must actually visit a state below historical solver best")
          .contains(true);
      var diagnostics =
          ((DefaultIteratedLocalSearchPhase<BasicSolution>) solver.getPhaseList().getFirst())
              .getDiagnostics();
      assertThat(diagnostics.episodes()).isGreaterThan(1);
      assertThat(diagnostics.workerStartups()).isEqualTo(threads.equals("NONE") ? 0 : 2);
      var trace =
          new Trace(
              result.getScore(),
              workload.state(result),
              List.copyOf(steps),
              List.copyOf(workingBelowBest));
      if (first == null) first = trace;
      else assertThat(trace).isEqualTo(first);
    }
    return first;
  }

  private static LocalSearchPhaseConfig inner(String algorithm) {
    var config = new LocalSearchPhaseConfig();
    if (algorithm.equals("GUIDED_LOCAL_SEARCH")) {
      return config
          .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
          .withGuidedLocalSearchConfig(
              new GuidedLocalSearchConfig()
                  .withSampleSize(12)
                  .withMaxUnproductiveRounds(2)
                  .withMaxPenaltyUpdatesPerStep(4));
    }
    if (algorithm.equals("SIMULATED_ANNEALING")) {
      return config
          .withAcceptorConfig(
              new LocalSearchAcceptorConfig().withSimulatedAnnealingStartingTemperature("100"))
          .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
    }
    if (algorithm.equals("STEP_COUNTING")) {
      return config
          .withAcceptorConfig(new LocalSearchAcceptorConfig().withStepCountingHillClimbingSize(3))
          .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
    }
    if (algorithm.equals("TABU_SEARCH")) {
      return config
          .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuSize(2))
          .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
    }
    return config.withLocalSearchType(LocalSearchType.valueOf(algorithm));
  }

  private record Trace(
      SimpleScore score, String assignments, List<String> steps, List<Boolean> workingBelowBest) {}
}
