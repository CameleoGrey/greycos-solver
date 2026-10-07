package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class GeneticAlgorithmReproducibilityTest {

  @ParameterizedTest
  @ValueSource(longs = {0, 37, 997})
  void fixedSeedReproducesFullLogicalTraceUnderDeterministicWorkLimit(long seed) {
    var config =
        config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(0.8)
                    .withMutationRateMultiplier(1.0)
                    .withTabuEntityRate(0.25)
                    .withTerminationConfig(new TerminationConfig().withMoveCountLimit(34L)))
            .withRandomSeed(seed);
    var firstSolver = solver(config);
    var firstTrace = trace(firstSolver);
    var first = firstSolver.solve(problem(5, 12));
    var secondSolver = solver(config.copyConfig());
    var secondTrace = trace(secondSolver);
    var second = secondSolver.solve(problem(5, 12));

    assertThat(firstTrace).hasSize(34);
    assertThat(secondTrace).containsExactlyElementsOf(firstTrace);
    assertThat(assignments(second)).containsExactlyElementsOf(assignments(first));
    assertThat(second.getScore()).isEqualTo(first.getScore());
    assertThat(secondSolver.getSolverScope().getScoreCalculationCount())
        .isEqualTo(firstSolver.getSolverScope().getScoreCalculationCount());
    assertReplay(first);
    assertReplay(second);
  }

  @Test
  void reusingTheSameSolverResetsPopulationDiscoveryIdsAndRandomState() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withPBestRate(0.8)
                    .withTabuEntityRate(0.25)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(34))));
    var trace = trace(solver);
    var first = solver.solve(problem(5, 12));
    var firstTrace = List.copyOf(trace);
    var firstCalculations = solver.getSolverScope().getScoreCalculationCount();
    trace.clear();

    var second = solver.solve(problem(5, 12));

    assertThat(trace).containsExactlyElementsOf(firstTrace);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(34);
    assertThat(solver.getSolverScope().getScoreCalculationCount()).isEqualTo(firstCalculations);
    assertThat(assignments(second)).containsExactlyElementsOf(assignments(first));
    assertReplay(first);
    assertReplay(second);
  }

  @Test
  void bestClonesAndInputAssignmentsAreIsolatedFromLaterWorkspaceChanges() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(7)
                    .withPBestRate(1.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(55))));
    var bestClones = new ArrayList<TestdataSolution>();
    var bestSnapshots = new ArrayList<List<String>>();
    var bestScores = new ArrayList<SimpleScore>();
    solver.addEventListener(
        event -> {
          var best = event.getNewBestSolution();
          assertReplay(best);
          bestClones.add(best);
          bestSnapshots.add(assignments(best));
          bestScores.add(best.getScore());
        });
    var input = problem(5, 12);

    var result = solver.solve(input);

    assertThat(assignments(input)).containsOnly("0");
    assertThat(bestClones).isNotEmpty();
    for (var i = 0; i < bestClones.size(); i++) {
      assertThat(assignments(bestClones.get(i))).containsExactlyElementsOf(bestSnapshots.get(i));
      assertThat(bestClones.get(i).getScore()).isEqualTo(bestScores.get(i));
      assertReplay(bestClones.get(i));
      if (i > 0) {
        assertThat(bestScores.get(i)).isGreaterThan(bestScores.get(i - 1));
        assertThat(bestClones.get(i).getEntityList().getFirst())
            .isNotSameAs(bestClones.get(i - 1).getEntityList().getFirst());
      }
    }
    assertThat(result.getScore()).isEqualTo(bestScores.getLast());
    assertReplay(result);
  }

  private static List<Step> trace(DefaultSolver<TestdataSolution> solver) {
    var trace = new ArrayList<Step>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
            trace.add(
                new Step(
                    step.getStepIndex(),
                    step.isSeeding(),
                    step.getGeneration(),
                    step.getCandidateId(),
                    step.getFirstParentId(),
                    step.getSecondParentId(),
                    step.getNativeId(),
                    step.isCrossed(),
                    step.getMutationType(),
                    step.getMutationGroup(),
                    step.getOutcome(),
                    step.isAdmitted(),
                    step.getChangedAssignmentCount(),
                    step.getBeforeScore(),
                    step.getBestBeforeScore(),
                    step.getCandidateScore(),
                    step.getScore(),
                    step.getBestScoreImproved(),
                    assignments(step.getWorkingSolution())));
          }
        });
    return trace;
  }

  private record Step(
      int index,
      boolean seeding,
      long generation,
      long candidate,
      long firstParent,
      long secondParent,
      long nativeMember,
      boolean crossed,
      GeneticAlgorithmMutationType mutation,
      String group,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      int changedAssignments,
      InnerScore<?> beforeScore,
      InnerScore<?> bestBeforeScore,
      InnerScore<?> candidateScore,
      InnerScore<?> workspaceScore,
      boolean bestImproved,
      List<String> assignments) {}
}
