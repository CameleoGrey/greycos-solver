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

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.localsearch.decider.LocalSearchPhaseDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;

import org.junit.jupiter.api.Test;

class LocalSearchEpisodeRunnerTest {

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
