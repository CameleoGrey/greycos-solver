package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.testcotwin.list.TestdataListSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmDeferredScoreMismatchTest {

  private static volatile Control control;

  @Test
  void returningToTheMaterializedGenomeCannotDiscardAnIncorrectWorkerScore() {
    var ga =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(2)
            .withEvaluatorThreadCount(1)
            .withCrossoverProbability(0.0)
            .withPBestRate(1.0)
            .withTabuEntityRate(0.0)
            .withMutationOperators(
                new GeneticAlgorithmMutationOperatorConfig()
                    .withType(GeneticAlgorithmMutationType.SWAP)
                    .withProbability(1.0))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    var solver =
        GeneticAlgorithmListIntegrationTest.solver(
            GeneticAlgorithmListIntegrationTest.config(ga)
                .withScoreDirectorFactory(
                    new ScoreDirectorFactoryConfig()
                        .withEasyScoreCalculatorClass(IncorrectWorkerScoreCalculator.class)));
    var phase =
        (DefaultGeneticAlgorithmPhase<TestdataListSolution>) solver.getPhaseList().getFirst();
    var current = new Control();
    control = current;
    phase.setLogicalAttemptObserver(
        attempt -> {
          current.attempts.add(attempt);
          if (attempt.seeding()) installProposals(solver, attempt.assignments(), current);
        });
    try {
      assertThatThrownBy(() -> solver.solve(GeneticAlgorithmListIntegrationTest.problem(3, 1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("evaluator score (-2)")
          .hasMessageContaining("materialized score (-1)")
          .hasMessageContaining("equal assignments");

      var offspring = current.attempts.stream().filter(attempt -> !attempt.seeding()).toList();
      assertThat(offspring)
          .hasSize(4)
          .allSatisfy(
              attempt -> {
                assertThat(attempt.outcome()).isEqualTo(GeneticAlgorithmOutcome.EVALUATED);
                assertThat(attempt.admitted()).isFalse();
              });
      assertThat(offspring.get(0).assignments()).isEqualTo(offspring.get(2).assignments());
      assertThat(offspring.get(1).assignments()).isEqualTo(offspring.get(3).assignments());
      assertThat(offspring.get(1).score().raw()).isEqualTo(SimpleScore.MINUS_ONE);
      assertThat(offspring.get(3).score().raw()).isEqualTo(SimpleScore.of(-2));
      assertThat(phase.getRunDiagnostics().deferredMaterializationUsed()).isTrue();
      assertThat(phase.getRunDiagnostics().coordinatorMaterializations()).isEqualTo(1);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
      assertThat(phase.getEvaluatorDiagnostics().submittedCount()).isEqualTo(4);
      assertThat(phase.getEvaluatorDiagnostics().consumedCount()).isEqualTo(4);
      assertThat(phase.getEvaluatorDiagnostics().closedWorkerCount()).isEqualTo(1);
      assertThat(phase.getEvaluatorDiagnostics().calculationCount())
          .isEqualTo(phase.getEvaluatorDiagnostics().transferredCalculationCount());
      assertThat(current.workerCalls).hasValue(5);
      assertThat(solver.getSolverScope().getBestSolution().getScore()).isEqualTo(SimpleScore.ZERO);
      solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      control = null;
    }
  }

  private static void installProposals(
      DefaultSolver<TestdataListSolution> solver,
      GeneticAlgorithmListSnapshot seeded,
      Control current) {
    var choices = new ArrayList<int[]>();
    for (var pair : new int[][] {{0, 1}, {0, 2}, {1, 2}}) {
      var assignment = new int[] {0, 1, 2};
      assignment[pair[0]] = pair[1];
      assignment[pair[1]] = pair[0];
      if (!Arrays.equals(assignment, seeded.copyList(0))) choices.add(pair);
    }
    // Both children are absent from the seeded population. Always compete against its best
    // member, so the first generation ends on a materialized rejected child outside the cache.
    var proposals =
        mock(RandomGenerator.SplittableGenerator.class, delegatesTo(new SplittableRandom(17L)));
    doAnswer(ignored -> 0.5).when(proposals).nextDouble();
    doAnswer(call -> Math.nextDown((double) call.getArgument(1)))
        .when(proposals)
        .nextDouble(anyDouble(), anyDouble());
    doAnswer(ignored -> 0).when(proposals).nextInt(anyInt());
    var anchorDraw = new AtomicInteger();
    doAnswer(
            call -> {
              int origin = call.getArgument(0);
              int bound = call.getArgument(1);
              if (bound == 2) return origin; // The native member is the best ranked member.
              assertThat(bound).isEqualTo(3);
              int draw = anchorDraw.getAndIncrement();
              assertThat(draw).isLessThan(8);
              return choices.get((draw / 2) % 2)[draw % 2];
            })
        .when(proposals)
        .nextInt(anyInt(), anyInt());
    var second = choices.get(1);
    var repeated = new int[] {0, 1, 2};
    repeated[second[0]] = second[1];
    repeated[second[1]] = second[0];
    current.corruptedAssignment = Arrays.stream(repeated).mapToObj(Integer::toString).toList();

    var random = new SplittableRandom(23L);
    var coordinator = mock(RandomGenerator.SplittableGenerator.class, delegatesTo(random));
    var firstSplit = new AtomicBoolean(true);
    doAnswer(ignored -> firstSplit.getAndSet(false) ? proposals : random.split())
        .when(coordinator)
        .split();
    ((DefaultRandomSource) solver.getSolverScope().getWorkingRandom())
        .moveRandom()
        .setDelegate(coordinator);
  }

  private static final class Control {
    private final Thread coordinator = Thread.currentThread();
    private final AtomicInteger workerCalls = new AtomicInteger();
    private final List<DefaultGeneticAlgorithmPhase.LogicalAttempt> attempts = new ArrayList<>();
    private List<String> corruptedAssignment;
  }

  public static final class IncorrectWorkerScoreCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      var assignments = GeneticAlgorithmListIntegrationTest.assignments(solution).getFirst();
      if (Thread.currentThread() != control.coordinator
          && control.workerCalls.incrementAndGet() == 5) {
        assertThat(assignments).isEqualTo(control.corruptedAssignment);
        return SimpleScore.of(-2);
      }
      return assignments.equals(List.of("0", "1", "2")) ? SimpleScore.ZERO : SimpleScore.MINUS_ONE;
    }
  }
}
