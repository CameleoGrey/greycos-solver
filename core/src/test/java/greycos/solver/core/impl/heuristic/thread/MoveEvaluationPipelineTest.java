package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MoveEvaluationPipelineTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final InnerScore<SimpleScore> ZERO = InnerScore.fullyAssigned(SimpleScore.ZERO);

  @Test
  void consumesOutOfOrderCompletionsInSelectionOrderAndPreservesInitialization() throws Exception {
    var firstStarted = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var secondEvaluated = new CountDownLatch(1);
    var first = move();
    var second = move();
    var secondScore = InnerScore.withUnassignedCount(SimpleScore.of(-7), 3);
    try (var fixture = new Fixture(2, 2)) {
      fixture.evaluate(
          first,
          () -> {
            firstStarted.countDown();
            awaitLatch(releaseFirst);
            return ZERO;
          });
      fixture.evaluate(
          second,
          () -> {
            secondEvaluated.countDown();
            return secondScore;
          });
      fixture.start();
      fixture.pipeline.submit(0, first);
      fixture.pipeline.submit(1, second);
      fixture.pipeline.flush();
      awaitLatch(firstStarted);
      awaitLatch(secondEvaluated);

      var coordinator = Thread.currentThread();
      try (var releasingFirst = releaseWhenParked(coordinator, releaseFirst)) {
        var firstResult = fixture.pipeline.take();
        releasingFirst.get();
        assertThat(firstResult.moveIndex()).isZero();
        assertThat(firstResult.move()).isSameAs(first);
        assertThat(firstResult.score()).isEqualTo(ZERO);
      }
      var secondResult = fixture.pipeline.take();
      assertThat(secondResult.moveIndex()).isEqualTo(1);
      assertThat(secondResult.move()).isSameAs(second);
      assertThat(secondResult.score()).isEqualTo(secondScore);
      assertThat(secondResult.isMoveDoable()).isTrue();
    } finally {
      releaseFirst.countDown();
    }
  }

  @Test
  void reusesConsumedSlotsWithoutConfusingMoveIndices() throws Exception {
    try (var fixture = new Fixture(2, 2)) {
      fixture.start();
      for (int index = 0; index < 12; index++) {
        var candidate = move();
        fixture.pipeline.submit(index, candidate);
        var result = fixture.pipeline.take();
        assertThat(result.stepIndex()).isZero();
        assertThat(result.moveIndex()).isEqualTo(index);
        assertThat(result.move()).isSameAs(candidate);
      }
    }
  }

  @Test
  void takeFlushesAPartialBatchWithoutGeneratingAdditionalCandidates() throws Exception {
    var allStarted = new CountDownLatch(3);
    try (var fixture = new Fixture(3, 3)) {
      fixture.start();
      fixture.workerThreads.forEach(MoveEvaluationPipelineTest::awaitParked);
      var candidates = new ArrayList<Move<Object>>();
      for (int index = 0; index < 3; index++) {
        var candidate = move();
        candidates.add(candidate);
        fixture.evaluate(
            candidate,
            () -> {
              allStarted.countDown();
              awaitLatch(allStarted);
              return ZERO;
            });
        fixture.pipeline.submit(index, candidate);
      }
      for (int index = 0; index < candidates.size(); index++) {
        var result = fixture.pipeline.take();
        assertThat(result.moveIndex()).isEqualTo(index);
        assertThat(result.move()).isSameAs(candidates.get(index));
      }
      fixture.pipeline.close();
      assertThat(fixture.pipeline.getDiagnostics().generated()).isEqualTo(3);
      assertThat(fixture.pipeline.getDiagnostics().evaluated()).isEqualTo(3);
      assertThat(fixture.pipeline.getDiagnostics().consumed()).isEqualTo(3);
    }
  }

  @Test
  void failureBypassesAnUnfinishedEarlierMoveAndClosesEveryDirector() throws Exception {
    var firstStarted = new CountDownLatch(1);
    var firstInterrupted = new CountDownLatch(1);
    var releaseFailure = new CountDownLatch(1);
    var failingMove = move();
    var firstMove = move();
    var failure = new IllegalArgumentException("evaluation failure");
    try (var fixture = new Fixture(2, 2)) {
      fixture.evaluate(
          firstMove,
          () -> {
            firstStarted.countDown();
            try {
              new CountDownLatch(1).await();
              throw new AssertionError("The unfinished move must be interrupted during abort.");
            } catch (InterruptedException interrupted) {
              firstInterrupted.countDown();
              throw interrupted;
            }
          });
      fixture.evaluate(
          failingMove,
          () -> {
            awaitLatch(firstStarted);
            awaitLatch(releaseFailure);
            throw failure;
          });
      fixture.start();
      fixture.pipeline.submit(0, firstMove);
      fixture.pipeline.submit(1, failingMove);
      try (var failingAfterPark = releaseWhenParked(Thread.currentThread(), releaseFailure)) {
        assertThatThrownBy(fixture.pipeline::take)
            .isInstanceOf(IllegalStateException.class)
            .hasCause(failure);
        failingAfterPark.get();
      }
      fixture.pipeline.abort();
      awaitLatch(firstInterrupted);
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void completionBeforeCoordinatorWaitIsNotLost() throws Exception {
    var candidate = move();
    var evaluated = new CountDownLatch(1);
    try (var fixture = new Fixture(1, 1)) {
      fixture.evaluate(
          candidate,
          () -> {
            evaluated.countDown();
            return ZERO;
          });
      fixture.start();
      fixture.pipeline.submit(0, candidate);
      fixture.pipeline.flush();
      awaitLatch(evaluated);
      awaitParked(fixture.workerThreads.get(0));
      fixture.pipeline.setTerminationCheck(
          () -> {
            throw new AssertionError("Ready results must retain the normal foraging prefix.");
          });
      assertThat(fixture.pipeline.take().move()).isSameAs(candidate);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void nonDoableMovePublishesAnOrderedResultWithoutScoring() throws Exception {
    AbstractSelectorBasedMove<Object> candidate = mock(AbstractSelectorBasedMove.class);
    when(candidate.rebase(any())).thenReturn(candidate);
    try (var fixture = new Fixture(1, 1, true)) {
      fixture.start();
      fixture.pipeline.submit(0, candidate);
      var result = fixture.pipeline.take();
      assertThat(result.move()).isSameAs(candidate);
      assertThat(result.isMoveDoable()).isFalse();
      assertThat(result.score()).isNull();
      verify(fixture.children.get(0), never()).executeTemporaryMove(any(), anyBoolean());
    }
  }

  @Test
  void nextEpochRunsWhileAnOldEvaluationFinishesAndThirdEpochWaitsForAcknowledgement()
      throws Exception {
    var obsoleteStarted = new CountDownLatch(1);
    var releaseObsolete = new CountDownLatch(1);
    var useful = move();
    var obsolete = move();
    var firstStep = move();
    var secondStep = move();
    var nextEpochMove = move();
    try (var fixture = new Fixture(2, 2)) {
      fixture.evaluate(
          obsolete,
          () -> {
            obsoleteStarted.countDown();
            awaitLatch(releaseObsolete);
            return InnerScore.fullyAssigned(SimpleScore.of(-99));
          });
      fixture.start();
      fixture.pipeline.submit(0, useful);
      fixture.pipeline.submit(1, obsolete);
      fixture.pipeline.flush();
      awaitLatch(obsoleteStarted);
      assertThat(fixture.pipeline.take().move()).isSameAs(useful);
      fixture.pipeline.cancelStep();
      fixture.pipeline.applyStep(1, firstStep, ZERO);
      fixture.pipeline.startNextStep(1);
      fixture.pipeline.submit(0, nextEpochMove);

      var nextResult = fixture.pipeline.take();
      assertThat(nextResult.stepIndex()).isEqualTo(1);
      assertThat(nextResult.move()).isSameAs(nextEpochMove);
      assertThat(nextResult.score()).isEqualTo(ZERO);
      assertThat(releaseObsolete.getCount()).isEqualTo(1);
      fixture.pipeline.cancelStep();
      try (var releasingObsolete = releaseWhenParked(Thread.currentThread(), releaseObsolete)) {
        fixture.pipeline.applyStep(2, secondStep, ZERO);
        releasingObsolete.get();
      }

      fixture.pipeline.startNextStep(2);
      var thirdEpochMove = move();
      fixture.pipeline.submit(0, thirdEpochMove);
      var result = fixture.pipeline.take();
      assertThat(result.stepIndex()).isEqualTo(2);
      assertThat(result.move()).isSameAs(thirdEpochMove);
      assertThat(result.score()).isEqualTo(ZERO);
      fixture.pipeline.close();
      verify(firstStep, times(2)).execute(any());
      verify(secondStep, times(2)).execute(any());
      fixture.assertDirectorsClosed();
    } finally {
      releaseObsolete.countDown();
    }
  }

  @Test
  void consecutiveEmptyEpochsDeliverEveryStepExactlyOnce() throws Exception {
    try (var fixture = new Fixture(3, 1)) {
      fixture.start();
      var steps = new ArrayList<Move<Object>>();
      for (int epoch = 1; epoch <= 4; epoch++) {
        var step = move();
        steps.add(step);
        fixture.pipeline.cancelStep();
        fixture.pipeline.applyStep(epoch, step, ZERO);
        fixture.pipeline.startNextStep(epoch);
      }
      fixture.pipeline.close();
      for (var step : steps) {
        verify(step, times(3)).execute(any());
      }
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void unknownReplayScoreIsCalculatedOnEveryWorkerWithoutScoringTheCoordinator() throws Exception {
    var partial = InnerScore.withUnassignedCount(SimpleScore.of(-4), 2);
    var delta = move();
    try (var fixture = new Fixture(3, 2)) {
      for (var child : fixture.children) {
        when(child.calculateScore()).thenReturn(ZERO, partial);
      }
      fixture.start();
      fixture.pipeline.applyState(1, delta, null);
      fixture.pipeline.submit(0, move());
      assertThat(fixture.pipeline.take().stepIndex()).isEqualTo(1);
      fixture.pipeline.close();

      verify(delta, times(3)).execute(any());
      verify(fixture.parent, never()).calculateScore();
      for (var child : fixture.children) {
        verify(child, times(2)).calculateScore();
        verify(child.getSolutionDescriptor()).setScore(child.getWorkingSolution(), partial.raw());
      }
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void unknownPartialReplayScoreBecomesTheUndoAssertionBaseline() throws Exception {
    var partial = InnerScore.withUnassignedCount(SimpleScore.of(-4), 2);
    var destroy = move();
    var candidate = move();
    var restore = move();
    try (var fixture = new Fixture(1, 2, false, true)) {
      var child = fixture.children.getFirst();
      when(child.calculateScore()).thenReturn(ZERO, partial);
      fixture.start();
      fixture.pipeline.applyState(1, destroy, null);
      fixture.pipeline.submit(0, candidate);
      assertThat(fixture.pipeline.take().score()).isEqualTo(ZERO);
      fixture.pipeline.applyStep(2, restore, ZERO);
      fixture.pipeline.close();

      verify(child).assertExpectedUndoMoveScore(eq(candidate), eq(partial), any());
      verify(child).assertPredictedScoreFromScratch(partial, destroy);
      verify(child).assertExpectedWorkingScore(partial, destroy);
      verify(child).assertShadowVariablesAreNotStale(partial, destroy);
      verify(child).assertExpectedWorkingScore(ZERO, restore);
      verify(child, times(2)).calculateScore();
      verify(fixture.parent, never()).calculateScore();
    }
  }

  @Test
  void unknownReplayScoreFailureStopsWorkersAndPreservesTheCause() {
    var failure = new IllegalArgumentException("partial state score failure");
    try (var fixture = new Fixture(1, 2)) {
      when(fixture.children.getFirst().calculateScore()).thenReturn(ZERO).thenThrow(failure);
      fixture.start();
      fixture.pipeline.applyState(1, move(), null);
      assertThatThrownBy(fixture.pipeline::close)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(failure);
      fixture.assertDirectorsClosed();
      verify(fixture.parent, never()).calculateScore();
    }
  }

  @Test
  void closeWaitsForTheFinalSelectedStepToBeApplied() throws Exception {
    var stepStarted = new CountDownLatch(1);
    var releaseStep = new CountDownLatch(1);
    var step = move();
    doAnswer(
            invocation -> {
              stepStarted.countDown();
              awaitLatch(releaseStep);
              return null;
            })
        .when(step)
        .execute(any());
    try (var fixture = new Fixture(1, 1)) {
      fixture.start();
      fixture.pipeline.cancelStep();
      fixture.pipeline.applyStep(1, step, ZERO);
      awaitLatch(stepStarted);
      try (var releasingStep = releaseWhenParked(Thread.currentThread(), releaseStep)) {
        fixture.pipeline.close();
        releasingStep.get();
      }
      verify(step).execute(any());
      fixture.assertDirectorsClosed();
    } finally {
      releaseStep.countDown();
    }
  }

  @Test
  void setupFailureAbortsPeersAndClosesCreatedDirectors() {
    var failure = new IllegalArgumentException("setup failure");
    try (var fixture = new Fixture(2, 2)) {
      when(fixture.children.get(0).calculateScore()).thenThrow(failure);
      assertThatThrownBy(fixture::start)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(failure);
      fixture.pipeline.abort();
      verify(fixture.children.get(0)).close();
    }
  }

  @Test
  void calculationCountFailureStillClosesDirectorAndKeepsTheFirstFailure() {
    var countFailure = new IllegalArgumentException("count failure");
    var closeFailure = new IllegalArgumentException("close failure");
    try (var fixture = new Fixture(1, 1)) {
      var child = fixture.children.get(0);
      when(child.getCalculationCount()).thenThrow(countFailure);
      doThrow(closeFailure).when(child).close();
      fixture.start();
      assertThatThrownBy(fixture.pipeline::close)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(countFailure);
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void directorCloseFailureIsReportedToTheCoordinator() {
    var closeFailure = new IllegalArgumentException("close failure");
    try (var fixture = new Fixture(1, 1)) {
      doThrow(closeFailure).when(fixture.children.get(0)).close();
      fixture.start();
      assertThatThrownBy(fixture.pipeline::close)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(closeFailure);
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void terminationWhileWaitingReturnsWithoutConsumingTheUnfinishedMove() throws Exception {
    var candidate = move();
    var evaluationStarted = new CountDownLatch(1);
    var requestTermination = new CountDownLatch(1);
    try (var fixture = new Fixture(1, 1)) {
      fixture.evaluate(
          candidate,
          () -> {
            evaluationStarted.countDown();
            new CountDownLatch(1).await();
            throw new AssertionError("Aborting the pipeline must interrupt this evaluation.");
          });
      fixture.start();
      fixture.pipeline.setTerminationCheck(() -> requestTermination.getCount() == 0);
      fixture.pipeline.submit(0, candidate);
      fixture.pipeline.flush();
      awaitLatch(evaluationStarted);
      try (var terminating = releaseWhenParked(Thread.currentThread(), requestTermination)) {
        assertThat(fixture.pipeline.take()).isNull();
        terminating.get();
      }
      fixture.pipeline.abort();
      assertThat(fixture.pipeline.getDiagnostics().consumed()).isZero();
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void interruptedCoordinatorClosesWorkersWithoutReportingCancellationAsFailure() throws Exception {
    var candidate = move();
    var evaluationStarted = new CountDownLatch(1);
    try (var fixture = new Fixture(2, 1)) {
      fixture.evaluate(
          candidate,
          () -> {
            evaluationStarted.countDown();
            new CountDownLatch(1).await();
            throw new AssertionError("Closing the interrupted pipeline must interrupt evaluation.");
          });
      fixture.start();
      fixture.pipeline.submit(0, candidate);
      fixture.pipeline.flush();
      awaitLatch(evaluationStarted);
      try {
        Thread.currentThread().interrupt();
        fixture.pipeline.close();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        fixture.assertDirectorsClosed();
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  void unexpectedWorkerInterruptWakesAWaitingCoordinator() throws Exception {
    var candidate = move();
    var evaluationStarted = new CountDownLatch(1);
    var evaluatingThread = new AtomicReference<Thread>();
    try (var fixture = new Fixture(2, 1)) {
      fixture.evaluate(
          candidate,
          () -> {
            evaluatingThread.set(Thread.currentThread());
            evaluationStarted.countDown();
            new CountDownLatch(1).await();
            throw new AssertionError("Aborting the pipeline must interrupt this evaluation.");
          });
      fixture.start();
      fixture.pipeline.submit(0, candidate);
      fixture.pipeline.flush();
      awaitLatch(evaluationStarted);
      var coordinator = Thread.currentThread();
      try (var interrupting =
          async(
              () -> {
                awaitParked(coordinator);
                fixture.workerThreads.stream()
                    .filter(worker -> worker != evaluatingThread.get())
                    .findFirst()
                    .orElseThrow()
                    .interrupt();
                return null;
              })) {
        assertThatThrownBy(fixture.pipeline::take)
            .isInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Move worker interrupted outside cancellation.");
        interrupting.get();
      }
      fixture.pipeline.abort();
      fixture.assertDirectorsClosed();
    }
  }

  @Test
  void aPublishedResultWakesAnAlreadyWaitingCoordinator() throws Exception {
    var candidate = move();
    var evaluationStarted = new CountDownLatch(1);
    var releaseEvaluation = new CountDownLatch(1);
    try (var fixture = new Fixture(1, 1)) {
      fixture.evaluate(
          candidate,
          () -> {
            evaluationStarted.countDown();
            awaitLatch(releaseEvaluation);
            return ZERO;
          });
      fixture.start();
      fixture.pipeline.submit(0, candidate);
      fixture.pipeline.flush();
      awaitLatch(evaluationStarted);
      try (var releasingEvaluation = releaseWhenParked(Thread.currentThread(), releaseEvaluation)) {
        assertThat(fixture.pipeline.take().move()).isSameAs(candidate);
        releasingEvaluation.get();
      }
    } finally {
      releaseEvaluation.countDown();
    }
  }

  @SuppressWarnings("unchecked")
  private static Move<Object> move() {
    Move<Object> move = mock(Move.class);
    when(move.rebase(any())).thenReturn(move);
    return move;
  }

  private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
    assertThat(latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
  }

  private static void awaitParked(Thread thread) {
    await()
        .atMost(TIMEOUT)
        .until(
            () ->
                thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING);
  }

  private static <T> Async<T> async(Callable<T> callable) {
    var future = new FutureTask<>(callable);
    var thread = new Thread(future, "test-pipeline-controller");
    thread.start();
    return new Async<>(thread, future);
  }

  private static ParkedRelease releaseWhenParked(Thread coordinator, CountDownLatch latch) {
    var operationCompleted = new AtomicBoolean();
    var controller =
        async(
            () -> {
              await()
                  .atMost(TIMEOUT)
                  .until(
                      () ->
                          operationCompleted.get()
                              || coordinator.getState() == Thread.State.WAITING
                              || coordinator.getState() == Thread.State.TIMED_WAITING);
              assertThat(operationCompleted)
                  .as("The pipeline operation must wait until the worker is released.")
                  .isFalse();
              latch.countDown();
              return (Void) null;
            });
    return new ParkedRelease(controller, operationCompleted);
  }

  private record ParkedRelease(Async<Void> controller, AtomicBoolean operationCompleted)
      implements AutoCloseable {

    private void get() throws Exception {
      operationCompleted.set(true);
      controller.get();
    }

    @Override
    public void close() throws InterruptedException {
      controller.close();
    }
  }

  private record Async<T>(Thread thread, FutureTask<T> future) implements AutoCloseable {

    private T get() throws Exception {
      return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws InterruptedException {
      if (thread.isAlive()) {
        thread.interrupt();
      }
      thread.join(TIMEOUT.toMillis());
      assertThat(thread.isAlive()).isFalse();
    }
  }

  private static final class Fixture implements AutoCloseable {

    private final List<InnerScoreDirector<Object, SimpleScore>> children = new ArrayList<>();
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Move<Object>, Callable<InnerScore<SimpleScore>>> evaluations =
        new ConcurrentHashMap<>();
    private final InnerScoreDirector<Object, SimpleScore> parent;
    private final ExecutorService executor;
    private final MoveEvaluationPipeline<Object> pipeline;

    private Fixture(int workers, int capacity) {
      this(workers, capacity, false);
    }

    private Fixture(int workers, int capacity, boolean evaluateDoable) {
      this(workers, capacity, evaluateDoable, false);
    }

    @SuppressWarnings("unchecked")
    private Fixture(int workers, int capacity, boolean evaluateDoable, boolean assertions) {
      parent = mock(InnerScoreDirector.class);
      for (int i = 0; i < workers; i++) {
        InnerScoreDirector<Object, SimpleScore> child = mock(InnerScoreDirector.class);
        when(child.getMoveDirector()).thenReturn(new MoveDirector<>(child));
        when(child.getSolutionDescriptor()).thenReturn(mock(SolutionDescriptor.class));
        when(child.calculateScore()).thenReturn(ZERO);
        when(child.executeTemporaryMove(any(), anyBoolean()))
            .thenAnswer(
                invocation -> {
                  var evaluation = evaluations.get(invocation.getArgument(0));
                  return evaluation == null ? ZERO : evaluation.call();
                });
        children.add(child);
      }
      var nextChild = new AtomicInteger();
      when(parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD))
          .thenAnswer(invocation -> children.get(nextChild.getAndIncrement()));
      executor =
          Executors.newFixedThreadPool(
              workers,
              runnable -> {
                var thread = new Thread(runnable, "test-pipeline-worker-" + workerThreads.size());
                workerThreads.add(thread);
                return thread;
              });
      pipeline =
          new MoveEvaluationPipeline<>(
              executor,
              workers,
              capacity,
              0,
              evaluateDoable,
              assertions,
              assertions,
              assertions,
              assertions,
              assertions);
    }

    private void evaluate(Move<Object> move, Callable<InnerScore<SimpleScore>> evaluation) {
      evaluations.put(move, evaluation);
    }

    private void start() {
      pipeline.start(parent);
      pipeline.startNextStep(0);
    }

    private void assertDirectorsClosed() {
      for (var child : children) {
        verify(child).close();
      }
    }

    @Override
    public void close() {
      pipeline.abort();
      executor.shutdownNow();
      try {
        assertThat(executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
    }
  }
}
