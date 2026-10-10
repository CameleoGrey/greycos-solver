package greycos.solver.core.impl.localsearch.decider;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttempt;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.PlanningValueSnapshot;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PreparedMoveFilters;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;

/**
 * Multithreaded implementation of LocalSearchDecider that evaluates moves in parallel using
 * multiple worker threads. This decider coordinates move evaluation across threads while
 * maintaining the correct order of operations and proper synchronization.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

  protected final ThreadFactory threadFactory;
  protected final int moveThreadCount;
  protected final int selectedMoveBufferSize;

  protected boolean assertStepScoreFromScratch = false;
  protected boolean assertExpectedStepScore = false;
  protected boolean assertShadowVariablesAreNotStaleAfterStep = false;

  protected ExecutorService executor;
  protected MoveEvaluationPipeline<Solution_> moveEvaluationPipeline;
  private MoveEvaluationPipeline.Diagnostics moveEvaluationDiagnostics;
  private long transferredCalculationCount;
  private long retiredWorkerCalculationCount;
  private long retiredWorkerStartupCount;
  private PlanningValueSnapshot.Rebaser<Solution_> planningValueRebaser;
  private boolean externalEvaluationResources;
  private BooleanSupplier resourceTermination;
  private LocalSearchPhaseScope<Solution_> resourceScope;

  @Override
  public void setExternalEvaluationResources(boolean external) {
    externalEvaluationResources = external;
  }

  @Override
  public void setEvaluationResourceTermination(BooleanSupplier terminated) {
    resourceTermination = terminated;
    if (moveEvaluationPipeline != null) {
      moveEvaluationPipeline.setBarrierTerminationCheck(terminated);
    }
  }

  public MultiThreadedLocalSearchDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> moveRepository,
      Acceptor<Solution_> acceptor,
      LocalSearchForager<Solution_> forager,
      ThreadFactory threadFactory,
      int moveThreadCount,
      int selectedMoveBufferSize) {
    super(logIndentation, termination, moveRepository, acceptor, forager);
    this.threadFactory = threadFactory;
    this.moveThreadCount = moveThreadCount;
    this.selectedMoveBufferSize = selectedMoveBufferSize;
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    if (!isEvaluationStateSafeToDispose()) {
      throw new IllegalStateException(
          "Local search history cannot restart while move workers still own evaluation state.");
    }
    super.phaseStarted(phaseScope);
    if (!externalEvaluationResources) startEvaluationResources(phaseScope);
    if (moveEvaluationPipeline == null) return;
    moveEvaluationPipeline.setTerminationCheck(() -> termination.isPhaseTerminated(phaseScope));
    if (requiresPlanningValues) {
      planningValueRebaser = new PlanningValueSnapshot.Rebaser<>(phaseScope.getScoreDirector());
    }
  }

  @Override
  public void startEvaluationResources(LocalSearchPhaseScope<Solution_> phaseScope) {
    if (moveEvaluationPipeline != null) {
      throw new IllegalStateException("Local search evaluation resources are already running.");
    }
    resourceScope = phaseScope;
    transferredCalculationCount = 0;
    retiredWorkerCalculationCount = 0;
    retiredWorkerStartupCount = 0;
    executor = createThreadPoolExecutor();
    phaseScope.getSolverScope().getWorkerRegistry().registerExecutor(executor, "Local Search");
    try {
      moveEvaluationPipeline = createMoveEvaluationPipeline(phaseScope.getPhaseIndex());
      moveEvaluationPipeline.setTerminationCheck(() -> termination.isPhaseTerminated(phaseScope));
      if (resourceTermination != null) {
        moveEvaluationPipeline.setBarrierTerminationCheck(resourceTermination);
      }
      if (requiresPlanningValues) {
        planningValueRebaser = new PlanningValueSnapshot.Rebaser<>(phaseScope.getScoreDirector());
        moveEvaluationPipeline.setMetadataCollectorFactory(
            director -> (view, move, context) -> PlanningValueSnapshot.capture(director, move));
      }
      moveEvaluationPipeline.start(phaseScope.getScoreDirector());
    } catch (CancellationException cancellation) {
      retireEvaluationResources(phaseScope, true);
      if (externalEvaluationResources) throw cancellation;
    } catch (RuntimeException | Error failure) {
      try {
        executor.shutdownNow();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  protected MoveEvaluationPipeline<Solution_> createMoveEvaluationPipeline(int phaseIndex) {
    return new MoveEvaluationPipeline<>(
        executor,
        moveThreadCount,
        selectedMoveBufferSize,
        phaseIndex,
        true,
        assertMoveScoreFromScratch,
        assertExpectedUndoMoveScore,
        assertStepScoreFromScratch,
        assertExpectedStepScore,
        assertShadowVariablesAreNotStaleAfterStep);
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    Throwable failure = null;
    try {
      if (!externalEvaluationResources) endEvaluationResources(phaseScope);
      else cancelAndQuiesceEvaluation();
    } catch (RuntimeException | Error error) {
      failure = error;
    }
    if (isEvaluationStateSafeToDispose()) {
      try {
        super.phaseEnded(phaseScope);
      } catch (RuntimeException | Error error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      }
    }
    if (failure instanceof Error error) throw error;
    if (failure != null) throw (RuntimeException) failure;
  }

  @Override
  public void endEvaluationResources(LocalSearchPhaseScope<Solution_> phaseScope) {
    retireEvaluationResources(phaseScope, false);
  }

  private void retireEvaluationResources(
      LocalSearchPhaseScope<Solution_> phaseScope, boolean abort) {
    var pipeline = moveEvaluationPipeline;
    if (pipeline == null) return;
    boolean retired = false;
    try {
      if (abort || pipeline.isTerminated()) pipeline.abort();
      else pipeline.close();
      retired = true;
    } finally {
      if (retired || pipeline.isTerminated()) {
        // Lifetime credit survives all history resets and is reconciled once, after worker join.
        retiredWorkerCalculationCount = pipeline.getCalculationCount();
        retiredWorkerStartupCount = pipeline.getWorkerStartupCount();
        phaseScope.addChildThreadsScoreCalculationCount(
            Math.max(0L, retiredWorkerCalculationCount - transferredCalculationCount));
        moveEvaluationDiagnostics = pipeline.getDiagnostics();
        logger.debug(
            "{}Move evaluation diagnostics: {}", logIndentation, moveEvaluationDiagnostics);
        moveEvaluationPipeline = null;
        planningValueRebaser = null;
        resourceScope = null;
      }
    }
  }

  @Override
  public boolean cancelAndQuiesceEvaluation() {
    if (moveEvaluationPipeline == null) return true;
    try {
      if (moveEvaluationPipeline.cancelAndAwaitQuiescence()) return true;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException | Error failure) {
      abortPreserving(failure);
      throw failure;
    }
    retireEvaluationResources(resourceScope, true);
    return false;
  }

  @Override
  public void replayWorkingState(Move<Solution_> move, InnerScore<?> score) {
    if (moveEvaluationPipeline == null) {
      throw new CancellationException("Local search evaluation resources have stopped.");
    }
    try {
      if (!moveEvaluationPipeline.replayStateAndAwait(move, score)) {
        throw new CancellationException("Local search state replay terminated.");
      }
    } catch (RuntimeException | Error failure) {
      abortPreserving(failure);
      throw failure;
    }
  }

  @Override
  public void beginEpisode(long episodeId) {
    if (!cancelAndQuiesceEvaluation()) {
      throw new CancellationException("Local search episode startup terminated.");
    }
    try {
      moveEvaluationPipeline.beginEpisode(episodeId);
    } catch (RuntimeException | Error failure) {
      abortPreserving(failure);
      throw failure;
    }
  }

  private void abortPreserving(Throwable failure) {
    try {
      retireEvaluationResources(resourceScope, true);
    } catch (RuntimeException | Error cleanup) {
      if (cleanup != failure) failure.addSuppressed(cleanup);
    }
  }

  public MoveEvaluationPipeline.Diagnostics getMoveEvaluationDiagnostics() {
    return moveEvaluationDiagnostics;
  }

  @Override
  public boolean isEvaluationStateSafeToDispose() {
    return moveEvaluationPipeline == null
        || moveEvaluationPipeline.isEvaluationStateSafeToDispose();
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (!isEvaluationStateSafeToDispose()) {
      throw new IllegalStateException(
          "Local search cannot restart while move workers from the previous solve are still active.");
    }
    super.solvingStarted(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    if (isEvaluationStateSafeToDispose()) super.solvingEnded(solverScope);
  }

  @Override
  public long getUncreditedCalculationCount() {
    return moveEvaluationPipeline == null
        ? 0L
        : Math.max(0L, moveEvaluationPipeline.getCalculationCount() - transferredCalculationCount);
  }

  @Override
  public long getWorkerStartupCount() {
    return moveEvaluationPipeline == null
        ? retiredWorkerStartupCount
        : moveEvaluationPipeline.getWorkerStartupCount();
  }

  @Override
  public long getConsumedWorkerCalculationCount() {
    return transferredCalculationCount;
  }

  @Override
  public long getAdditionalWorkerCalculationCount() {
    long physical =
        moveEvaluationPipeline == null
            ? retiredWorkerCalculationCount
            : moveEvaluationPipeline.getCalculationCount();
    return Math.max(0L, physical - transferredCalculationCount);
  }

  @Override
  protected void resetLocalSearchState(LocalSearchStepScope<Solution_> stepScope) {
    try {
      // The adopted step must reach every worker before provider lifecycle callbacks reset state.
      if (moveEvaluationPipeline.awaitEvaluationQuiescence()) {
        super.resetLocalSearchState(stepScope);
        if (requiresPlanningValues) {
          planningValueRebaser = new PlanningValueSnapshot.Rebaser<>(stepScope.getScoreDirector());
        }
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    if (moveEvaluationPipeline != null) {
      try {
        retireEvaluationResources(resourceScope, true);
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != exception) exception.addSuppressed(cleanup);
      }
    }
    if (isEvaluationStateSafeToDispose()) super.solvingError(solverScope, exception);
  }

  protected ExecutorService createThreadPoolExecutor() {
    return Executors.newFixedThreadPool(
        moveThreadCount, ThreadUtils.requireNonNullThreads(threadFactory, "Local Search"));
  }

  @Override
  public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
    if (moveEvaluationPipeline == null) {
      stepScope.setNoStepReason(LocalSearchStepScope.NoStepReason.TERMINATED);
      return;
    }
    int stepIndex = moveEvaluationPipeline.getCurrentEpoch();
    moveEvaluationPipeline.startNextStep(stepIndex);

    var pending =
        externalEvaluationResources
            ? null
            : stepScope.getPhaseScope().getSolverScope().consumePendingMove();
    if (pending != null) {
      moveEvaluationPipeline.cancelStep();
      resetOnPendingMove = pending.requiresReset();
      var move = pending.move();
      var score =
          stepScope.getScoreDirector().executeTemporaryMove(move, assertMoveScoreFromScratch);
      stepScope.getPhaseScope().addMoveEvaluationCount(move, 1L);
      stepScope.setStep(move);
      stepScope.setScore(score);
      stepScope.setSelectedMoveCount(1L);
      stepScope.setAcceptedMoveCount(1L);
    } else if (stepScope.getPhaseScope().isEpisode()) {
      decideEpisodeStep(stepScope, stepIndex);
    } else {
      int selectMoveIndex = 0;
      int movesInPlay = 0;
      Iterator<Move<Solution_>> moveIterator = moveRepository.iterator();
      boolean stoppedForagingEarly = false;

      do {
        boolean hasNextMove = moveIterator.hasNext();
        if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
          var forageResult = forageResult(stepScope, stepIndex);
          movesInPlay--;
          if (forageResult != ForageResult.CONTINUE) {
            stoppedForagingEarly = true;
            break;
          }
        }
        if (hasNextMove) {
          var move = moveIterator.next();
          if (requiresPlanningValues) {
            moveEvaluationPipeline.submit(selectMoveIndex, move, PlanningValuesContext.INSTANCE);
          } else {
            moveEvaluationPipeline.submit(selectMoveIndex, move);
          }
          selectMoveIndex++;
          movesInPlay++;
        }
      } while (movesInPlay > 0);
      if (stoppedForagingEarly) {
        moveEvaluationPipeline.cancelStep();
      }
      // Pick the best move from the results
      pickMove(stepScope);
    }

    // If we have a step, apply it to all threads
    if (stepScope.getStep() != null) {
      InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
      if (scoreDirector.requiresFlushing() && stepIndex % 100 == 99) {
        // Flush delayed score director state periodically to avoid unbounded buildup.
        scoreDirector.calculateScore();
      }
      if (!externalEvaluationResources) {
        try {
          moveEvaluationPipeline.applyStep(
              stepIndex + 1, stepScope.getStep(), stepScope.getScore());
        } catch (CancellationException cancellation) {
          retireEvaluationResources(resourceScope, true);
          stepScope.setStep(null);
          stepScope.setNoStepReason(LocalSearchStepScope.NoStepReason.TERMINATED);
        }
      }
    }
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    if (externalEvaluationResources && stepScope.getStep() != null) {
      // The episode runner may abandon a selected decision before commit. Replay only after its
      // coordinator mutation completed; abandoned decisions leave every worker at the old state.
      moveEvaluationPipeline.applyStep(
          Math.incrementExact(moveEvaluationPipeline.getCurrentEpoch()),
          stepScope.getStep(),
          stepScope.getScore());
    }
    super.stepEnded(stepScope);
  }

  @Override
  public void stepAborted(LocalSearchStepScope<Solution_> stepScope) {
    if (moveEvaluationPipeline != null) moveEvaluationPipeline.cancelStep();
    super.stepAborted(stepScope);
  }

  private void decideEpisodeStep(LocalSearchStepScope<Solution_> stepScope, int pipelineEpoch) {
    var ledger = stepScope.getPhaseScope().getSelectionAttemptLedger();
    var pending = new ArrayDeque<SelectionAttempt<Move<Solution_>>>();
    int publishedMoves = 0;
    boolean complete = false;
    stepScope.setSelectedMoveCount(0L);
    stepScope.setAcceptedMoveCount(0L);
    try (var cursor = ledger.openCursor(moveRepository::iterator)) {
      while (true) {
        if (isEpisodeDecisionInterrupted(stepScope)) break;
        // Markers and candidates share source order; only candidates occupy worker result slots.
        while (pending.size() < selectedMoveBufferSize
            && !isEpisodeDecisionInterrupted(stepScope)) {
          var attempt = cursor.next();
          if (attempt == null) break;
          pending.addLast(attempt);
          if (!attempt.isMarker()) {
            if (requiresPlanningValues) {
              moveEvaluationPipeline.submit(
                  publishedMoves++, attempt.selection(), PlanningValuesContext.INSTANCE);
            } else {
              moveEvaluationPipeline.submit(publishedMoves++, attempt.selection());
            }
          }
        }
        if (isEpisodeDecisionInterrupted(stepScope)) break;
        if (pending.isEmpty()) {
          complete = cursor.isSourceExhausted() && !moveRepository.isNeverEnding();
          break;
        }
        var attempt = pending.removeFirst();
        var outcome =
            attempt.isMarker() ? ForageResult.CONTINUE : forageResult(stepScope, pipelineEpoch);
        if (outcome == ForageResult.STOP) break;
        // The admitted candidate finishes acceptance/foraging before its reservation is settled.
        ledger.consume(attempt);
        if (isEpisodeDecisionInterrupted(stepScope)) break;
        if (outcome == ForageResult.STOP_FORAGING) {
          complete =
              !ledger.isExhausted()
                  || forager.isQuitEarly()
                  || (pending.isEmpty()
                      && cursor.isSourceExhausted()
                      && !moveRepository.isNeverEnding());
          break;
        }
        if (ledger.isExhausted()) {
          complete =
              pending.isEmpty() && cursor.isSourceExhausted() && !moveRepository.isNeverEnding();
          break;
        }
        stepScope.getPhaseScope().getSolverScope().checkYielding();
      }
      if (complete) pickMove(stepScope);
      else
        stepScope.setNoStepReason(
            isEpisodeDecisionInterrupted(stepScope)
                ? LocalSearchStepScope.NoStepReason.TERMINATED
                : ledger.isExhausted() || cursor.isBudgetStopped()
                    ? LocalSearchStepScope.NoStepReason.EPISODE_ATTEMPT_LIMIT
                    : LocalSearchStepScope.NoStepReason.NO_ADMISSIBLE_MOVE);
    } finally {
      // A cancelled epoch remains closed until replay or a new episode explicitly opens it.
      moveEvaluationPipeline.cancelStep();
    }
  }

  private boolean isEpisodeDecisionInterrupted(LocalSearchStepScope<Solution_> stepScope) {
    return Thread.currentThread().isInterrupted()
        || (termination instanceof LocalSearchEpisodeTermination<Solution_> episode
            ? episode.isNonAttemptTerminated(stepScope.getPhaseScope())
            : termination.isPhaseTerminated(stepScope.getPhaseScope()));
  }

  private ForageResult forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
    MoveEvaluationPipeline.Result<Solution_> result;
    try {
      result = moveEvaluationPipeline.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ForageResult.STOP;
    }

    if (result == null) {
      return ForageResult.STOP;
    }

    if (stepIndex != result.stepIndex()) {
      throw new IllegalStateException(
          "Impossible situation: the solverThread's stepIndex ("
              + stepIndex
              + ") differs from the result's stepIndex ("
              + result.stepIndex()
              + ").");
    }
    if (externalEvaluationResources
        && result.episodeId() != moveEvaluationPipeline.getEpisodeId()) {
      throw new IllegalStateException("A stale local search episode result was consumed.");
    }
    stepScope.getScoreDirector().incrementCalculationCount(result.calculationCount());
    transferredCalculationCount += result.calculationCount();
    int foragingMoveIndex = result.moveIndex();
    Move<Solution_> foragingMove = result.move();
    if (foragingMove == null) {
      throw new IllegalStateException(
          "Impossible situation: no original move for move index (" + foragingMoveIndex + ").");
    }

    boolean accepted = false;
    if (!result.isMoveDoable()) {
      logger.trace(
          "{}        Move index ({}) not doable, ignoring move ({}).",
          logIndentation,
          foragingMoveIndex,
          foragingMove);
    } else {
      foragingMove = PreparedMoveFilters.filter(foragingMove, stepScope.getScoreDirector());
      if (foragingMove == null) {
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        return recordCandidate(stepScope, false)
                || termination.isPhaseTerminated(stepScope.getPhaseScope())
            ? ForageResult.STOP_FORAGING
            : ForageResult.CONTINUE;
      }
      LocalSearchMoveScope<Solution_> moveScope =
          new LocalSearchMoveScope<>(stepScope, foragingMoveIndex, foragingMove);
      moveScope.setScore(result.score());
      if (requiresPlanningValues) {
        if (result.metadata() instanceof PlanningValueSnapshot snapshot) {
          moveScope.setPlanningValueSnapshot(planningValueRebaser.rebase(snapshot));
        }
        requirePlanningValueSnapshot(moveScope);
      }
      accepted = acceptor.isAccepted(moveScope);
      moveScope.setAccepted(accepted);
      acceptor.moveEvaluated(moveScope);
      logger.trace(
          "{}        Move index ({}), score ({}), accepted ({}), move ({}).",
          logIndentation,
          foragingMoveIndex,
          moveScope.getScore().raw(),
          moveScope.getAccepted(),
          foragingMove);
      addMoveToForager(moveScope);
      if (forager.isQuitEarly()) {
        recordCandidate(stepScope, accepted);
        return ForageResult.STOP_FORAGING;
      }
    }

    stepScope.getPhaseScope().getSolverScope().checkYielding();
    if (recordCandidate(stepScope, accepted)
        || termination.isPhaseTerminated(stepScope.getPhaseScope())) {
      return ForageResult.STOP_FORAGING;
    }
    return ForageResult.CONTINUE;
  }

  private enum PlanningValuesContext implements MoveEvaluationPipeline.EvaluationContext {
    INSTANCE
  }

  @Override
  public void enableAssertions(EnvironmentMode environmentMode) {
    super.enableAssertions(environmentMode);
    assertStepScoreFromScratch = environmentMode.isFullyAsserted();
    if (environmentMode.isIntrusivelyAsserted()) {
      assertExpectedStepScore = true;
      assertShadowVariablesAreNotStaleAfterStep = true;
    } else {
      assertExpectedStepScore = false;
      assertShadowVariablesAreNotStaleAfterStep = false;
    }
  }

  public int getMoveThreadCount() {
    return moveThreadCount;
  }

  public int getSelectedMoveBufferSize() {
    return selectedMoveBufferSize;
  }

  public boolean isAssertStepScoreFromScratch() {
    return assertStepScoreFromScratch;
  }

  public boolean isAssertExpectedStepScore() {
    return assertExpectedStepScore;
  }

  public boolean isAssertShadowVariablesAreNotStaleAfterStep() {
    return assertShadowVariablesAreNotStaleAfterStep;
  }

  private enum ForageResult {
    CONTINUE,
    STOP_FORAGING,
    STOP
  }
}
