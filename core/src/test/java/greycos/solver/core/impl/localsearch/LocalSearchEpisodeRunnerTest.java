package greycos.solver.core.impl.localsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.localsearch.decider.LocalSearchPhaseDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.InnerMutableSolutionView;
import greycos.solver.core.impl.move.SolutionAssignmentDiagnostics;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class LocalSearchEpisodeRunnerTest {

  @Test
  @SuppressWarnings("unchecked")
  void strictEpisodeImprovementsCopyOnlyCommittedDirtyBindings() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withEasyScoreCalculatorClass(FirstEntityScoreCalculator.class)
            .withPhases(new CustomPhaseConfig().withCustomPhaseCommands(context -> {}));
    var solver =
        ((DefaultSolver<TestdataSolution>)
                SolverFactory.<TestdataSolution>create(config).buildSolver())
            .getSolverScope();
    var problem = TestdataSolution.generateSolution(5, 100);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    solver.setInitialSolution(problem);
    solver.setWorkingRandom(DefaultRandomSource.seeded(3));
    var decider = (LocalSearchPhaseDecider<TestdataSolution>) mock(LocalSearchPhaseDecider.class);
    when(decider.cancelAndQuiesceEvaluation()).thenReturn(true);
    when(decider.isEvaluationStateSafeToDispose()).thenReturn(true);
    var next = new AtomicInteger();
    int[][] assignments = {{0, 2}, {0, 1}, {0, 3}, {1, 4}, {0, 4}, {0, 0}};
    doAnswer(
            invocation -> {
              LocalSearchStepScope<TestdataSolution> step = invocation.getArgument(0);
              int index = next.getAndIncrement();
              if (index >= assignments.length) return null;
              var director = step.<SimpleScore>getScoreDirector();
              var working = director.getWorkingSolution();
              var entity = working.getEntityList().get(assignments[index][0]);
              var value = working.getValueList().get(assignments[index][1]);
              var variable =
                  director
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(TestdataEntity.class)
                      .getGenuineVariableDescriptor("value");
              Move<TestdataSolution> move =
                  view ->
                      ((InnerMutableSolutionView<TestdataSolution>) view)
                          .getScoreDirector()
                          .changeVariableFacade(variable, entity, value);
              var ledger = step.getPhaseScope().getSelectionAttemptLedger();
              try (var cursor = ledger.openCursor(List.of(move).iterator())) {
                var attempt = cursor.next();
                step.setScore(director.executeTemporaryMove(move, true));
                ledger.consume(attempt);
              }
              step.setStep(move);
              return null;
            })
        .when(decider)
        .decideNextStep(any());
    try (var director = solver.<SimpleScore>getScoreDirector();
        var runner =
            new LocalSearchEpisodeRunner<>(
                decider,
                0,
                new LocalSearchEpisodeTermination<>(
                    new BasicPlumbingTermination<TestdataSolution>(false)),
                10,
                EnvironmentMode.FULL_ASSERT);
        var diagnostics = SolutionAssignmentDiagnostics.open()) {
      var score = director.calculateScore();
      runner.open(solver, score);
      var result =
          runner.runEpisode(
              score,
              new LocalSearchEpisodeRunner.EpisodeCallbacks<>() {
                @Override
                public boolean isEnclosingTerminated() {
                  return false;
                }

                @Override
                public boolean isAdoptionPending() {
                  return false;
                }

                @Override
                public void decisionStarted(LocalSearchStepScope<TestdataSolution> step) {}

                @Override
                public void moveCommitted(LocalSearchStepScope<TestdataSolution> step) {}

                @Override
                public void decisionAborted(LocalSearchStepScope<TestdataSolution> step) {}
              });
      assertThat(result.bestScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(4)));
      assertThat(result.committedSteps()).isEqualTo(6);
      assertThat(result.attempts()).isEqualTo(6);
      assertThat(runner.getConsumedSelectionCount()).isEqualTo(6);
      assertThat(diagnostics.getCaptureCount()).isEqualTo(1);
      assertThat(diagnostics.getCapturedBindingCount()).isEqualTo(100);
      assertThat(diagnostics.getAccumulatorUpdateCount()).isEqualTo(3);
      assertThat(diagnostics.getAccumulatorBindingCount()).isEqualTo(4);
      var records = result.bestAssignments().getBasicChanges().values().iterator().next();
      assertThat(records.get(0).value()).isSameAs(problem.getValueList().get(4));
      assertThat(records.get(1).value()).isSameAs(problem.getValueList().get(4));
      assertThat(director.calculateScore()).isEqualTo(InnerScore.fullyAssigned(SimpleScore.ZERO));
      // The scoped commit observer is gone after the episode.
      try (var observation = director.observeGenuineAssignmentChanges((variable, entity) -> {})) {}
    }
  }

  public static final class FirstEntityScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          solution.getValueList().indexOf(solution.getEntityList().getFirst().getValue()));
    }
  }

  @Test
  void attemptedWorkSettlesEvenWhenEpisodeCleanupThrowsAndResetsOnOpen() {
    var fixture = new Fixture();
    var failure = new IllegalStateException("Episode history cleanup failed.");
    doAnswer(
            ignored -> {
              LocalSearchStepScope<Object> step = ignored.getArgument(0);
              var ledger = step.getPhaseScope().getSelectionAttemptLedger();
              try (var cursor = ledger.openCursor(List.of("consumed", "discarded").iterator())) {
                ledger.consume(cursor.next());
                cursor.next();
              }
              return null;
            })
        .when(fixture.decider)
        .decideNextStep(any());
    doAnswer(
            ignored -> {
              throw failure;
            })
        .when(fixture.decider)
        .phaseEnded(any());
    fixture.runner.open(fixture.solver, fixture.score);
    assertThatThrownBy(() -> fixture.runner.runEpisode(fixture.score, fixture.callbacks))
        .isSameAs(failure);
    assertThat(fixture.runner.getConsumedSelectionCount()).isEqualTo(1);
    assertThat(fixture.runner.getDiscardedSelectionCount()).isEqualTo(1);
    fixture.runner.close();
    fixture.runner.open(fixture.solver, fixture.score);
    assertThat(fixture.runner.getConsumedSelectionCount()).isZero();
    assertThat(fixture.runner.getDiscardedSelectionCount()).isZero();
    fixture.runner.close();
  }

  @Test
  void preparationInterruptedByAnEpisodeLimitEndsNormallyAndAbortsDecision() {
    var fixture = new Fixture();
    doAnswer(
            ignored -> {
              fixture.localLimit.terminateEarly();
              throw new CancellationException("Episode preparation stopped.");
            })
        .when(fixture.decider)
        .decideNextStep(any());
    fixture.runner.open(fixture.solver, fixture.score);
    var result = fixture.runner.runEpisode(fixture.score, fixture.callbacks);
    fixture.runner.close();
    assertThat(result.outcome()).isEqualTo(LocalSearchEpisodeRunner.Outcome.NORMAL);
    assertThat(result.committedSteps()).isZero();
    assertThat(fixture.aborted).hasValue(2); // Setup and the interrupted real decision.
    verify(fixture.decider).phaseEnded(any());
  }

  @Test
  void cancelledPreparationWithAbortFailurePreservesTheFailure() {
    var fixture = new Fixture();
    var cancellation = new CancellationException("Episode preparation stopped.");
    var cleanup = new IllegalStateException("Decision cleanup failed.");
    doAnswer(
            ignored -> {
              fixture.localLimit.terminateEarly();
              throw cancellation;
            })
        .when(fixture.decider)
        .decideNextStep(any());
    doAnswer(
            ignored -> {
              throw cleanup;
            })
        .when(fixture.decider)
        .stepAborted(any());
    fixture.runner.open(fixture.solver, fixture.score);
    assertThatThrownBy(() -> fixture.runner.runEpisode(fixture.score, fixture.callbacks))
        .isSameAs(cancellation)
        .hasSuppressedException(cleanup);
    fixture.runner.close();
    verify(fixture.decider).phaseEnded(any());
    assertThat(fixture.aborted).hasValue(2);
  }

  @Test
  void failurePreservesOriginalAndCompletesHistoryCleanupAfterResourceFailure() {
    var fixture = new Fixture();
    var original = new IllegalStateException("Scorer failed.");
    var cleanup = new IllegalStateException("Worker cleanup failed.");
    doAnswer(
            ignored -> {
              throw original;
            })
        .when(fixture.decider)
        .decideNextStep(any());
    when(fixture.decider.cancelAndQuiesceEvaluation()).thenReturn(true).thenThrow(cleanup);
    fixture.runner.open(fixture.solver, fixture.score);
    assertThatThrownBy(() -> fixture.runner.runEpisode(fixture.score, fixture.callbacks))
        .isSameAs(original)
        .hasSuppressedException(cleanup);
    fixture.runner.close();
    verify(fixture.decider).phaseEnded(any());
    verify(fixture.decider, times(2)).endEvaluationResources(any());
  }

  @Test
  void repeatedSolveRestartsEpisodeIdentity() {
    var fixture = new Fixture();
    for (int solve = 0; solve < 2; solve++) {
      fixture.runner.open(fixture.solver, fixture.score);
      fixture.runner.runEpisode(fixture.score, fixture.callbacks);
      fixture.runner.close();
    }
    verify(fixture.decider, times(2)).beginEpisode(0L);
  }

  @Test
  void failedJoinRetainsHistoryUntilDeferredCleanupCanRunSafely() {
    var fixture = new Fixture();
    var safe = new AtomicBoolean(true);
    when(fixture.decider.isEvaluationStateSafeToDispose()).thenAnswer(ignored -> safe.get());
    var original = new IllegalStateException("Scorer failed.");
    doAnswer(
            ignored -> {
              safe.set(false);
              throw original;
            })
        .when(fixture.decider)
        .decideNextStep(any());
    when(fixture.decider.cancelAndQuiesceEvaluation()).thenReturn(true).thenReturn(false);
    fixture.runner.open(fixture.solver, fixture.score);
    assertThatThrownBy(() -> fixture.runner.runEpisode(fixture.score, fixture.callbacks))
        .isSameAs(original);
    assertThatThrownBy(fixture.runner::close).isInstanceOf(IllegalStateException.class);
    verify(fixture.decider, never()).phaseEnded(any());
    verify(fixture.decider, never()).solvingEnded(any());
    safe.set(true);
    fixture.runner.close();
    verify(fixture.decider).phaseEnded(any());
    verify(fixture.decider).solvingEnded(any());
  }

  @SuppressWarnings("unchecked")
  private static final class Fixture {
    final SolverScope<Object> solver = new SolverScope<>();
    final LocalSearchPhaseDecider<Object> decider = mock(LocalSearchPhaseDecider.class);
    final InnerScore<SimpleScore> score = InnerScore.fullyAssigned(SimpleScore.ZERO);
    final BasicPlumbingTermination<Object> localLimit = new BasicPlumbingTermination<>(false);
    final AtomicInteger aborted = new AtomicInteger();
    final LocalSearchEpisodeRunner<Object> runner;
    final LocalSearchEpisodeRunner.EpisodeCallbacks<Object> callbacks =
        new LocalSearchEpisodeRunner.EpisodeCallbacks<>() {
          @Override
          public boolean isEnclosingTerminated() {
            return false;
          }

          @Override
          public boolean isAdoptionPending() {
            return false;
          }

          @Override
          public void decisionStarted(LocalSearchStepScope<Object> scope) {}

          @Override
          public void moveCommitted(LocalSearchStepScope<Object> scope) {
            throw new AssertionError("Unexpected commit.");
          }

          @Override
          public void decisionAborted(LocalSearchStepScope<Object> scope) {
            aborted.incrementAndGet();
          }
        };

    Fixture() {
      InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
      SolutionDescriptor<Object> descriptor = mock(SolutionDescriptor.class);
      when(director.getSolutionDescriptor()).thenReturn(descriptor);
      when(director.getWorkingSolution()).thenReturn(new Object());
      when(decider.cancelAndQuiesceEvaluation()).thenReturn(true);
      when(decider.isEvaluationStateSafeToDispose()).thenReturn(true);
      solver.setScoreDirector(director);
      solver.setWorkingRandom(DefaultRandomSource.seeded(3));
      runner =
          new LocalSearchEpisodeRunner<>(
              decider,
              0,
              new LocalSearchEpisodeTermination<>(localLimit),
              3,
              EnvironmentMode.NO_ASSERT);
    }
  }
}
