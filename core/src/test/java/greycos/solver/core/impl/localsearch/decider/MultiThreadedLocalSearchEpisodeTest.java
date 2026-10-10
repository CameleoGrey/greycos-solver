package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Timeout(20)
@Isolated("Exercises the shared worker shutdown timeout.")
class MultiThreadedLocalSearchEpisodeTest {

  private static final InnerScore<SimpleScore> ZERO = InnerScore.fullyAssigned(SimpleScore.ZERO);

  @Test
  void finalAttemptCompletesForagerBeforeCapAndReplayWaitsForActualCommit() {
    try (var fixture = new Fixture()) {
      var phase = fixture.startEpisode(0, 1);
      fixture.quitAfter = 1;
      var step = fixture.decide(phase);
      assertThat(step.getStep()).isSameAs(fixture.candidates.getFirst());
      assertThat(phase.getSelectionAttemptLedger().getConsumedCount()).isEqualTo(1);
      verify(step.getStep(), never()).execute(any());
      fixture.decider.stepEnded(step);
      assertThat(fixture.decider.cancelAndQuiesceEvaluation()).isTrue();
      verify(step.getStep(), times(2)).execute(any());
      fixture.decider.phaseEnded(phase);
    }
  }

  @Test
  void attemptCapDiscardsAnUnfinishedSelectionWithoutReplayingIt() {
    try (var fixture = new Fixture()) {
      var phase = fixture.startEpisode(0, 1);
      var step = fixture.decide(phase);
      assertThat(step.getStep()).isNull();
      assertThat(step.getNoStepReason())
          .isEqualTo(LocalSearchStepScope.NoStepReason.EPISODE_ATTEMPT_LIMIT);
      assertThat(fixture.seen).hasSize(1);
      verify(fixture.forager, never()).pickMove(any());
      fixture.decider.stepAborted(step);
      fixture.decider.phaseEnded(phase);
      verify(fixture.candidates.getFirst(), never()).execute(any());
    }
  }

  @Test
  void episodesReuseWorkersAndCreditConsumedAndSpeculativeCalculationsOnce() {
    try (var fixture = new Fixture()) {
      fixture.quitAfter = 1;
      for (int episode = 0; episode < 3; episode++) {
        var phase = fixture.startEpisode(episode, 8);
        var step = fixture.decide(phase);
        fixture.decider.stepEnded(step);
        fixture.decider.phaseEnded(phase);
        assertThat(phase.getSelectionAttemptLedger().getConsumedCount()).isEqualTo(1);
        assertThat(phase.getSelectionAttemptLedger().getReservedCount()).isZero();
        assertThat(phase.getSelectionAttemptLedger().getDiscardedCount()).isPositive();
      }
      assertThat(fixture.createdWorkers).hasValue(2);
      assertThat(fixture.decider.getWorkerStartupCount()).isEqualTo(2);
      assertThat(fixture.coordinatorCalculations).hasValue(3);
      assertThat(fixture.decider.getConsumedWorkerCalculationCount()).isEqualTo(3);
      fixture.decider.endEvaluationResources(fixture.resourceScope);
      long allWorkerCalculations =
          fixture.workerCalculations.stream().mapToLong(AtomicLong::get).sum();
      assertThat(fixture.decider.getWorkerStartupCount()).isEqualTo(2);
      assertThat(fixture.decider.getConsumedWorkerCalculationCount()).isEqualTo(3);
      assertThat(fixture.decider.getAdditionalWorkerCalculationCount())
          .isEqualTo(allWorkerCalculations - 3);
      assertThat(fixture.solver.getScoreCalculationCount()).isEqualTo(allWorkerCalculations);
      fixture.decider.endEvaluationResources(fixture.resourceScope);
      assertThat(fixture.solver.getScoreCalculationCount()).isEqualTo(allWorkerCalculations);
    }
  }

  @Test
  void failedEpisodeBarrierJoinsWorkersBeforeProviderHistoryDisposal() {
    try (var fixture = new Fixture()) {
      var phase = fixture.startEpisode(0, 8);
      fixture.failEvaluation.set(true);
      assertThatThrownBy(() -> fixture.decide(phase))
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("episode scorer failure");
      doAnswer(
              invocation -> {
                fixture.solver.getWorkerRegistry().assertNoActiveWorkers();
                return null;
              })
          .when(fixture.repository)
          .phaseEnded(phase);
      assertThatThrownBy(() -> fixture.decider.phaseEnded(phase))
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("episode scorer failure");
      verify(fixture.repository).phaseEnded(phase);
      assertThat(fixture.decider.moveEvaluationPipeline).isNull();
      assertThat(fixture.decider.getWorkerStartupCount()).isEqualTo(2);
    }
  }

  @Test
  void joinTimeoutRetainsOwnershipAndSkipsHistoryDisposalUntilTheWorkerActuallyExits()
      throws InterruptedException {
    int previousTimeout = ThreadUtils.getDefaultShutdownTimeout();
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    ThreadUtils.setDefaultShutdownTimeout(1);
    try (var fixture = new Fixture()) {
      var phase = fixture.startEpisode(0, 8);
      fixture.beforeEvaluation =
          () -> {
            started.countDown();
            boolean interrupted = false;
            while (release.getCount() != 0) {
              try {
                release.await();
              } catch (InterruptedException ignored) {
                interrupted = true;
              }
            }
            if (interrupted) Thread.currentThread().interrupt();
          };
      var pipeline = fixture.decider.moveEvaluationPipeline;
      pipeline.submit(0, fixture.candidates.getFirst());
      pipeline.flush();
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        assertThatThrownBy(() -> fixture.decider.endEvaluationResources(fixture.resourceScope))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not terminate");
        assertThat(pipeline.isTerminated()).isFalse();
        assertThat(fixture.decider.isEvaluationStateSafeToDispose()).isFalse();
        assertThat(fixture.decider.moveEvaluationPipeline).isSameAs(pipeline);
        assertThatThrownBy(() -> fixture.decider.phaseEnded(phase))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("did not terminate");
        fixture.decider.solvingEnded(fixture.solver);
        verify(fixture.repository, never()).phaseEnded(any());
        verify(fixture.repository, never()).solvingEnded(any());
        assertThat(fixture.solver.getScoreCalculationCount()).isZero();
        assertThatThrownBy(fixture.solver.getWorkerRegistry()::assertNoActiveWorkers)
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixture.decider.phaseStarted(phase))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("still own evaluation state");
      } finally {
        release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(pipeline::isTerminated);
      }
      // The timeout was already reported; actual worker exit now permits final reconciliation.
      fixture.decider.endEvaluationResources(fixture.resourceScope);
      assertThat(fixture.decider.moveEvaluationPipeline).isNull();
      assertThat(fixture.decider.isEvaluationStateSafeToDispose()).isTrue();
      fixture.decider.phaseEnded(phase);
      verify(fixture.repository).phaseEnded(phase);
      fixture.solver.getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      release.countDown();
      ThreadUtils.setDefaultShutdownTimeout(previousTimeout);
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final SolverScope<Object> solver = new SolverScope<>();
    private final InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    private final MoveSelectorBasedMoveRepository<Object> repository =
        mock(MoveSelectorBasedMoveRepository.class);
    private final Acceptor<Object> acceptor = mock(Acceptor.class);
    private final LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    private final List<Move<Object>> candidates = new ArrayList<>();
    private final List<LocalSearchMoveScope<Object>> seen = new ArrayList<>();
    private final List<AtomicLong> workerCalculations = new ArrayList<>();
    private final AtomicLong coordinatorCalculations = new AtomicLong();
    private final AtomicInteger createdWorkers = new AtomicInteger();
    private final AtomicBoolean failEvaluation = new AtomicBoolean();
    private volatile Runnable beforeEvaluation;
    private final LocalSearchEpisodeTermination<Object> termination =
        new LocalSearchEpisodeTermination<>(null);
    private final MultiThreadedLocalSearchDecider<Object> decider;
    private final LocalSearchPhaseScope<Object> resourceScope;
    private int quitAfter = Integer.MAX_VALUE;

    @SuppressWarnings("unchecked")
    private Fixture() {
      solver.setScoreDirector(director);
      solver.setSolverMetricSet(EnumSet.noneOf(SolverMetric.class));
      when(director.getCalculationCount()).thenAnswer(invocation -> coordinatorCalculations.get());
      doAnswer(
              invocation -> {
                coordinatorCalculations.addAndGet(invocation.getArgument(0));
                return null;
              })
          .when(director)
          .incrementCalculationCount(org.mockito.ArgumentMatchers.anyLong());
      var children = new ArrayList<InnerScoreDirector<Object, SimpleScore>>();
      for (int i = 0; i < 2; i++) {
        var calculations = new AtomicLong(1);
        workerCalculations.add(calculations);
        InnerScoreDirector<Object, SimpleScore> child = mock(InnerScoreDirector.class);
        when(child.getMoveDirector()).thenReturn(new MoveDirector<>(child));
        when(child.getSolutionDescriptor()).thenReturn(mock(SolutionDescriptor.class));
        when(child.calculateScore()).thenReturn(ZERO);
        when(child.getCalculationCount()).thenAnswer(invocation -> calculations.get());
        when(child.executeTemporaryMove(any(), anyBoolean()))
            .thenAnswer(
                invocation -> {
                  if (beforeEvaluation != null) beforeEvaluation.run();
                  if (failEvaluation.get())
                    throw new IllegalStateException("episode scorer failure");
                  calculations.incrementAndGet();
                  return ZERO;
                });
        children.add(child);
      }
      when(director.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
          .thenAnswer(invocation -> children.get(createdWorkers.getAndIncrement()));
      for (int i = 0; i < 4; i++) {
        Move<Object> move = mock(Move.class);
        when(move.rebase(any())).thenReturn(move);
        candidates.add(move);
      }
      when(repository.iterator()).thenAnswer(invocation -> candidates.iterator());
      when(acceptor.isAccepted(any())).thenReturn(true);
      doAnswer(
              invocation -> {
                seen.clear();
                return null;
              })
          .when(forager)
          .stepStarted(any());
      doAnswer(
              invocation -> {
                seen.add(invocation.getArgument(0));
                return null;
              })
          .when(forager)
          .addMove(any());
      when(forager.isQuitEarly()).thenAnswer(invocation -> seen.size() >= quitAfter);
      when(forager.pickMove(any()))
          .thenAnswer(invocation -> seen.isEmpty() ? null : seen.getFirst());
      decider =
          new MultiThreadedLocalSearchDecider<>(
              "",
              termination,
              repository,
              acceptor,
              forager,
              runnable -> new Thread(runnable, "episode-accounting-worker"),
              2,
              4);
      decider.setExternalEvaluationResources(true);
      decider.setEvaluationResourceTermination(() -> false);
      resourceScope = scope(8);
      decider.solvingStarted(solver);
      decider.startEvaluationResources(resourceScope);
    }

    private LocalSearchPhaseScope<Object> scope(long cap) {
      var scope =
          new LocalSearchPhaseScope<>(
              solver, 0, ZERO, mock(RandomSource.class), new SelectionAttemptLedger(cap));
      scope.setTermination(termination);
      return scope;
    }

    private LocalSearchPhaseScope<Object> startEpisode(long id, long cap) {
      var scope = scope(cap);
      assertThat(decider.cancelAndQuiesceEvaluation()).isTrue();
      decider.phaseStarted(scope);
      decider.beginEpisode(id);
      return scope;
    }

    private LocalSearchStepScope<Object> decide(LocalSearchPhaseScope<Object> scope) {
      var step = new LocalSearchStepScope<>(scope);
      decider.stepStarted(step);
      decider.decideNextStep(step);
      return step;
    }

    @Override
    public void close() {
      decider.endEvaluationResources(resourceScope);
      decider.solvingEnded(solver);
    }
  }
}
