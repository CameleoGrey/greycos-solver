package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmLifecycleTest {

  @Test
  void cancellationAtAttemptStartDoesNotEvaluateOrCreditTheInterruptedAttempt() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var steps = recordSteps(solver);
    var starts = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            starts.incrementAndGet();
            assertThat(solver.terminateEarly()).isTrue();
          }
        });

    var result = solver.solve(problem(4, 8));

    assertThat(starts).hasValue(1);
    assertThat(steps).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    assertThat(assignments(result)).containsOnly("0");
    assertThat(solver.isSolving()).isFalse();
    assertReplay(result);
  }

  @Test
  void cancellationAfterCompletedAttemptRetainsPublishedBestAndDiscardsPartialGeneration() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var steps = recordSteps(solver);
    var published = new ArrayList<TestdataSolution>();
    solver.addEventListener(event -> published.add(event.getNewBestSolution()));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            if (step.getStepIndex() == 3) {
              assertThat(solver.terminateEarly()).isTrue();
            }
          }
        });

    var result = solver.solve(problem(5, 8));

    assertThat(steps).hasSize(4);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(4);
    assertThat(published).isNotEmpty().allSatisfy(GeneticAlgorithmIntegrationTest::assertReplay);
    assertThat(result.getScore()).isEqualTo(published.getLast().getScore());
    assertThat(assignments(result)).containsExactlyElementsOf(assignments(published.getLast()));
    assertReplay(result);
  }

  @Test
  void problemChangeRestartsWithFreshEntitiesRangesPopulationAndCachedFitness() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(8))));
    var queued = new AtomicBoolean();
    var starts = new ArrayList<Integer>();
    var traces = new ArrayList<List<Integer>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            starts.add(scope.getWorkingSolution().getEntityList().size());
            traces.add(new ArrayList<>());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            traces.getLast().add(step.getStepIndex());
            if (queued.compareAndSet(false, true)) {
              solver.addProblemChange(
                  (solution, director) -> {
                    // Facts are shared with published clones; copy the collection before changing
                    // it.
                    solution.setValueList(new ArrayList<>(solution.getValueList()));
                    var high = new TestdataValue("9");
                    director.addProblemFact(high, solution.getValueList()::add);
                    for (var entity : solution.getEntityList()) {
                      director.changeVariable(entity, "value", working -> working.setValue(high));
                    }
                    director.addEntity(
                        new TestdataEntity("new-entity", high), solution.getEntityList()::add);
                  });
            }
          }
        });

    var result = solver.solve(problem(3, 5));

    assertThat(starts).containsExactly(5, 6);
    assertThat(traces).hasSize(2);
    assertThat(traces.getFirst()).containsExactly(0);
    assertThat(traces.getLast()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
    assertThat(result.getEntityList()).hasSize(6);
    assertThat(assignments(result)).containsOnly("9");
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(54));
    assertReplay(result);
  }

  @Test
  void environmentChangesBetweenPhasesPrepareDistinctDirectorsBeforePopulationCapture() {
    var first =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(3)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(5));
    var second =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(4)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(7));
    var solver = solver(config(first).withPhases(first, second));
    var directors = new ArrayList<InnerScoreDirector<TestdataSolution, ?>>();
    var counts = new ArrayList<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            directors.add(scope.getScoreDirector());
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            counts.add(scope.getPhaseMoveEvaluationCount());
          }
        });

    assertReplay(solver.solve(problem(4, 8)));

    assertThat(directors).hasSize(2);
    assertThat(directors.getFirst()).isNotSameAs(directors.getLast());
    assertThat(counts).containsExactly(5L, 7L);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(12);
  }

  @Test
  void scoringExceptionKeepsItsIdentityAndReceivesNoCompletedAttemptCredit() {
    var config =
        config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20)))
            .withScoreDirectorFactory(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(ControlledScoreCalculator.class));
    var solver = solver(config);
    var completed = recordSteps(solver);
    var failure = new IllegalStateException("intentional GA scoring failure");
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            ControlledScoreCalculator.FAILURE.set(failure);
          }
        });

    try {
      assertThatThrownBy(() -> solver.solve(problem(5, 8))).isSameAs(failure);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(completed.size());
      assertThat(solver.isSolving()).isFalse();
    } finally {
      ControlledScoreCalculator.FAILURE.remove();
    }
  }

  @Test
  void bestObserverExceptionIsPreservedAndCleanupFailureIsSuppressedWithoutAttemptCredit() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var failure = new IllegalStateException("intentional GA observer failure");
    var cleanup = new IllegalArgumentException("intentional GA cleanup failure");
    var completed = recordSteps(solver);
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.geneticAlgorithm(0))) {
            assertReplay(event.getNewBestSolution());
            throw failure;
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phase) {
            throw cleanup;
          }
        });

    assertThatThrownBy(() -> solver.solve(problem(5, 8)))
        .isSameAs(failure)
        .satisfies(thrown -> assertThat(thrown.getSuppressed()).contains(cleanup));
    assertThat(completed).isEmpty();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
  }

  @Test
  void failedStepEndedCallbackDoesNotCreditTheIncompleteAttempt() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(5)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))));
    var failure = new IllegalStateException("intentional GA step completion failure");
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            throw failure;
          }
        });

    assertThatThrownBy(() -> solver.solve(problem(5, 8))).isSameAs(failure);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
  }

  public static final class ControlledScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    static final ThreadLocal<RuntimeException> FAILURE = new ThreadLocal<>();

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      var failure = FAILURE.get();
      if (failure != null) {
        throw failure;
      }
      return SimpleScore.of(
          solution.getEntityList().stream()
              .filter(entity -> entity.getValue() != null)
              .mapToInt(entity -> Integer.parseInt(entity.getValue().getCode()))
              .sum());
    }
  }
}
