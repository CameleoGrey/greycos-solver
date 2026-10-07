package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmGenerationPolicyTest {

  @Test
  void tiedChildrenReplaceSampledNativesWithReplacementAndPreserveDuplicateGenomes() {
    int populationSize = 5;
    var solver =
        solver(
            config(
                    new GeneticAlgorithmPhaseConfig()
                        .withPopulationSize(populationSize)
                        .withPBestRate(0.05)
                        .withMutationOperators(mutation(GeneticAlgorithmMutationType.SWAP))
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(14)))
                .withConstraintProviderClass(ConstantConstraints.class));
    var trace = trace(solver);
    var phase = recordPhase(solver);

    var result = solver.solve(problem(2, 8));

    var firstGeneration = generation(trace, 1);
    var secondGeneration = generation(trace, 2);
    assertThat(firstGeneration).hasSize(populationSize);
    assertThat(secondGeneration).hasSize(populationSize);
    // With this rank interval, prefix and suffix lengths are always one. All equal scores are
    // ordered by discovery ID. Sampling the same native five times proves sampling with
    // replacement.
    assertThat(firstGeneration)
        .allSatisfy(
            step -> {
              assertThat(step.getFirstParentId()).isZero();
              assertThat(step.getSecondParentId()).isZero();
              assertThat(step.getNativeId()).isEqualTo(4);
              assertThat(step.isAdmitted()).isTrue();
              assertThat(step.getCandidateScore().raw()).isEqualTo(SimpleScore.ZERO);
            });
    assertThat(firstGeneration)
        .extracting(GeneticAlgorithmStepScope::getCandidateId)
        .containsExactly(5L, 6L, 7L, 8L, 9L);
    // New children cannot become parents or natives inside their own generation. The next
    // generation sees all five winners, with no old elite inserted at discovery ID zero.
    assertThat(secondGeneration)
        .allSatisfy(
            step -> {
              assertThat(step.getFirstParentId()).isEqualTo(5);
              assertThat(step.getSecondParentId()).isEqualTo(5);
              assertThat(step.getNativeId()).isEqualTo(9);
              assertThat(step.isAdmitted()).isTrue();
            });
    assertThat(secondGeneration)
        .extracting(GeneticAlgorithmStepScope::getCandidateId)
        .containsExactly(10L, 11L, 12L, 13L, 14L);
    assertThat(phase.get().getPopulationSize()).isEqualTo(5);
    assertThat(phase.get().getDistinctPopulationSize()).isEqualTo(1);
    assertThat(assignments(result)).containsOnly("0");
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(14);
    assertScoreCallsMatchFreshCandidates(trace, phase.get());
  }

  @Test
  void parentsAndNativesOnlyComeFromFrozenPriorGenerationWinners() {
    int populationSize = 7;
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(populationSize)
                    .withPBestRate(1.0)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(34))));
    var trace = trace(solver);
    var phase = recordPhase(solver);

    assertReplay(solver.solve(problem(4, 12)));

    Set<Long> previousPopulationIds =
        LongStream.range(0, populationSize).boxed().collect(Collectors.toSet());
    for (long generation = 1; generation <= 4; generation++) {
      var children = generation(trace, generation);
      assertThat(children).hasSize(populationSize);
      for (var child : children) {
        assertThat(previousPopulationIds)
            .contains(child.getFirstParentId(), child.getSecondParentId(), child.getNativeId());
      }
      // Pairs share both selected parents and the one crossover decision. For an odd population,
      // the final first child has no second attempted/mutated/scored sibling.
      for (int i = 0; i + 1 < populationSize; i += 2) {
        assertThat(children.get(i + 1).getFirstParentId())
            .isEqualTo(children.get(i).getFirstParentId());
        assertThat(children.get(i + 1).getSecondParentId())
            .isEqualTo(children.get(i).getSecondParentId());
        assertThat(children.get(i + 1).isCrossed()).isEqualTo(children.get(i).isCrossed());
      }
      previousPopulationIds =
          children.stream()
              .map(GeneticAlgorithmGenerationPolicyTest::winnerId)
              .collect(Collectors.toSet());
    }
    assertThat(trace).hasSize(34);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(34);
    assertScoreCallsMatchFreshCandidates(trace, phase.get());
  }

  @Test
  void bestArchiveSurvivesWhenEveryPopulationWinnerLosesTheInitialElite() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withPBestRate(0.05)
                    .withMutationRateMultiplier(32.0)
                    .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(6))));
    var trace = trace(solver);
    var input = problem(2, 32);
    input.getEntityList().forEach(entity -> entity.setValue(input.getValueList().getLast()));

    var result = solver.solve(input);

    Map<Long, SimpleScore> discoveredScores = new HashMap<>();
    discoveredScores.put(0L, SimpleScore.of(32));
    trace.forEach(
        step ->
            discoveredScores.put(
                step.getCandidateId(), (SimpleScore) step.getCandidateScore().raw()));
    var winners =
        generation(trace, 1).stream().map(GeneticAlgorithmGenerationPolicyTest::winnerId).toList();
    assertThat(winners).hasSize(3).doesNotContain(0L);
    assertThat(winners)
        .allSatisfy(id -> assertThat(discoveredScores.get(id)).isLessThan(SimpleScore.of(32)));
    var nextChild = generation(trace, 2).getFirst();
    assertThat(winners)
        .contains(
            nextChild.getFirstParentId(), nextChild.getSecondParentId(), nextChild.getNativeId());
    assertThat(nextChild.getBestBeforeScore().raw()).isEqualTo(SimpleScore.of(32));
    assertThat(assignments(result)).containsOnly("1");
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(32));
    assertReplay(result);
  }

  private static GeneticAlgorithmMutationOperatorConfig mutation(
      GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(1.0);
  }

  private static long winnerId(GeneticAlgorithmStepScope<TestdataSolution> step) {
    return step.isAdmitted() ? step.getCandidateId() : step.getNativeId();
  }

  private static List<GeneticAlgorithmStepScope<TestdataSolution>> generation(
      List<GeneticAlgorithmStepScope<TestdataSolution>> trace, long generation) {
    return trace.stream()
        .filter(step -> !step.isSeeding() && step.getGeneration() == generation)
        .toList();
  }

  private static void assertScoreCallsMatchFreshCandidates(
      List<GeneticAlgorithmStepScope<TestdataSolution>> trace,
      GeneticAlgorithmPhaseScope<TestdataSolution> scope) {
    long evaluated =
        trace.stream()
            .filter(step -> step.getOutcome() == GeneticAlgorithmOutcome.EVALUATED)
            .count();
    assertThat(scope.getPhaseScoreCalculationCount()).isEqualTo(1 + evaluated);
  }

  private static List<GeneticAlgorithmStepScope<TestdataSolution>> trace(
      DefaultSolver<TestdataSolution> solver) {
    var trace = new ArrayList<GeneticAlgorithmStepScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            trace.add((GeneticAlgorithmStepScope<TestdataSolution>) scope);
          }
        });
    return trace;
  }

  public static final class ConstantConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE, entity -> 0)
            .asConstraint("Equal fitness")
      };
    }
  }
}
