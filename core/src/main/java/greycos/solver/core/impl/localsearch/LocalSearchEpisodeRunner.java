package greycos.solver.core.impl.localsearch;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.localsearch.decider.LocalSearchPhaseDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.EpisodeBestAssignments;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.LocalSearchEpisodeTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

/** Runs bounded local-search histories over one phase-owned workspace and evaluation pool. */
public final class LocalSearchEpisodeRunner<Solution_> implements AutoCloseable {

  /** Compatibility adapter: ordinary local search keeps its phase lifecycle and stopping rules. */
  public static <Solution_> void runOrdinary(
      LocalSearchPhaseScope<Solution_> scope,
      PhaseTermination<Solution_> termination,
      LocalSearchPhaseDecider<Solution_> decider,
      Consumer<LocalSearchStepScope<Solution_>> start,
      Consumer<LocalSearchStepScope<Solution_>> commit,
      Consumer<LocalSearchStepScope<Solution_>> end,
      Consumer<LocalSearchStepScope<Solution_>> noMove) {
    while (!termination.isPhaseTerminated(scope)) {
      var step = new LocalSearchStepScope<>(scope);
      step.setTimeGradient(termination.calculatePhaseTimeGradient(scope));
      start.accept(step);
      decider.decideNextStep(step);
      if (step.getStep() == null) {
        noMove.accept(step);
        break;
      }
      commit.accept(step);
      end.accept(step);
      scope.setLastCompletedStepScope(step);
    }
  }

  public enum Outcome {
    NORMAL,
    ENCLOSING_TERMINATED,
    ADOPTION
  }

  public record EpisodeResult<Solution_>(
      Outcome outcome,
      SolutionAssignments<Solution_> bestAssignments,
      InnerScore<?> bestScore,
      long attempts,
      int committedSteps,
      String completionReason,
      long elapsedMillis) {}

  public interface EpisodeCallbacks<Solution_> {
    boolean isEnclosingTerminated();

    boolean isAdoptionPending();

    void decisionStarted(LocalSearchStepScope<Solution_> step);

    /** Called after selection, immediately before executing the committed move. */
    default void beforeMoveCommitted(LocalSearchStepScope<Solution_> step) {}

    void moveCommitted(LocalSearchStepScope<Solution_> step);

    void decisionAborted(LocalSearchStepScope<Solution_> step);
  }

  private final LocalSearchPhaseDecider<Solution_> decider;
  private final int phaseIndex;
  private final LocalSearchEpisodeTermination<Solution_> termination;
  private final long attemptLimit;
  private final EnvironmentMode environmentMode;
  private SolverScope<Solution_> solverScope;
  private LocalSearchPhaseScope<Solution_> resourceScope;
  private AbstractPhaseScope<Solution_> accountingScope;
  private EpisodeCallbacks<Solution_> callbacks;
  private long episodeSequence;
  private boolean open;
  private long consumedSelectionCount;
  private long discardedSelectionCount;
  private long snapshotNanos;
  private long episodeSetupNanos;
  private boolean diagnosticsEnabled;
  private boolean solvingHistoryStarted;
  private boolean solvingHistoryComplete;
  private LocalSearchPhaseScope<Solution_> pendingHistoryCleanupScope;

  public LocalSearchEpisodeRunner(
      LocalSearchPhaseDecider<Solution_> decider,
      int phaseIndex,
      LocalSearchEpisodeTermination<Solution_> termination,
      long attemptLimit,
      EnvironmentMode environmentMode) {
    if (attemptLimit <= 0)
      throw new IllegalArgumentException(
          "The episode attempt limit (" + attemptLimit + ") must be positive.");
    this.decider = Objects.requireNonNull(decider);
    this.phaseIndex = phaseIndex;
    this.termination = Objects.requireNonNull(termination);
    this.attemptLimit = attemptLimit;
    this.environmentMode = Objects.requireNonNull(environmentMode);
  }

  public void open(AbstractPhaseScope<Solution_> outerScope, InnerScore<?> actualScore) {
    accountingScope = outerScope;
    open(outerScope.getSolverScope(), actualScore);
  }

  public void open(SolverScope<Solution_> solverScope, InnerScore<?> actualScore) {
    if (open) throw new IllegalStateException("The local-search episode runner is already open.");
    episodeSequence =
        consumedSelectionCount = discardedSelectionCount = snapshotNanos = episodeSetupNanos = 0L;
    solvingHistoryStarted = solvingHistoryComplete = false;
    diagnosticsEnabled = Boolean.getBoolean("greycos.solver.iteratedLocalSearchDiagnostics");
    this.solverScope = Objects.requireNonNull(solverScope);
    resourceScope = createScope(actualScore);
    resourceScope.startingNow();
    resourceScope.reset();
    decider.setExternalEvaluationResources(true);
    decider.setEvaluationResourceTermination(
        () ->
            Thread.currentThread().isInterrupted()
                || (callbacks != null && callbacks.isEnclosingTerminated())
                || (accountingScope != null
                    && accountingScope.getTermination().isPhaseTerminated(accountingScope)));
    open = true; // Partial startup is owned by close/error cleanup too.
    try {
      termination.solvingStarted(solverScope);
      decider.startEvaluationResources(resourceScope);
    } catch (RuntimeException | Error failure) {
      try {
        close();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private LocalSearchPhaseScope<Solution_> createScope(InnerScore<?> score) {
    var random = ((DefaultRandomSource) solverScope.getWorkingRandom()).splitForChildThread();
    var scopeReference = new AtomicReference<LocalSearchPhaseScope<Solution_>>();
    var scope =
        new LocalSearchPhaseScope<>(
            solverScope,
            phaseIndex,
            score,
            random,
            new SelectionAttemptLedger(
                attemptLimit,
                () ->
                    scopeReference.get() != null
                        && termination.isNonAttemptTerminated(scopeReference.get())));
    scopeReference.set(scope);
    scope.setChildThreadAccountingScope(accountingScope);
    scope.setTermination(termination);
    return scope;
  }

  public EpisodeResult<Solution_> runEpisode(
      InnerScore<?> actualScore, EpisodeCallbacks<Solution_> callbacks) {
    if (!open) throw new IllegalStateException("The local-search episode runner is not open.");
    this.callbacks = Objects.requireNonNull(callbacks);
    termination.setEnclosingTermination(
        callbacks::isEnclosingTerminated, callbacks::isAdoptionPending);
    var scope = createScope(actualScore);
    scope.startingNow();
    scope.reset();
    EpisodeBestAssignments<Solution_> best = null;
    boolean started = false;
    boolean[] historyStarted = {false};
    int committedSteps = 0;
    String completionReason = null;
    Throwable failure = null;
    try {
      best = initializeBest(scope);
      if (!decider.cancelAndQuiesceEvaluation()) {
        return result(
            Outcome.ENCLOSING_TERMINATED, best, actualScore, scope, 0, "ENCLOSING_TERMINATED");
      }
      termination.phaseStarted(scope);
      started = true;
      boolean initialized;
      long setupStart = diagnosticsEnabled ? System.nanoTime() : 0L;
      try {
        initialized = startEpisodeHistory(scope, callbacks, historyStarted);
        if (initialized) decider.beginEpisode(episodeSequence++);
      } finally {
        if (diagnosticsEnabled) episodeSetupNanos += System.nanoTime() - setupStart;
      }
      if (!initialized) {
        var outcome =
            termination.isEnclosingTerminated()
                ? Outcome.ENCLOSING_TERMINATED
                : termination.isAdoptionPending() ? Outcome.ADOPTION : Outcome.NORMAL;
        return result(outcome, best, actualScore, scope, 0, "SETUP_ATTEMPT_LIMIT");
      }
      while (!termination.isPhaseTerminated(scope)) {
        var step = new LocalSearchStepScope<>(scope);
        step.setTimeGradient(termination.calculatePhaseTimeGradient(scope));
        var random = (DefaultRandomSource) scope.getWorkingRandom();
        var savedRandom = random.moveRandom().getDelegate();
        random.restoreState(random.saveState());
        boolean decisionStarted = false;
        boolean completed = false;
        Throwable decisionFailure = null;
        try {
          decisionStarted = true;
          callbacks.decisionStarted(step);
          termination.stepStarted(step);
          if (!scope.getSelectionAttemptLedger().runSetup(() -> decider.stepStarted(step))) {
            completionReason = "SETUP_ATTEMPT_LIMIT";
            break;
          }
          if (!termination.isPhaseTerminated(scope)) decider.decideNextStep(step);
          if (step.getStep() == null || termination.isNonAttemptTerminated(scope)) {
            completionReason =
                step.getNoStepReason() == null ? "NO_SELECTED_MOVE" : step.getNoStepReason().name();
            break;
          }
          callbacks.beforeMoveCommitted(step);
          try (var observation = scope.getScoreDirector().observeGenuineAssignmentChanges(best)) {
            scope.getScoreDirector().executeMove(step.getStep());
          }
          scope.getSolutionDescriptor().setScore(scope.getWorkingSolution(), step.getScore().raw());
          if (!step.getScore().isFullyAssigned() || step.getScore().isStructurallyFlawed()) {
            throw new IllegalStateException(
                "The episode committed an invalid or incomplete move (" + step.getStep() + ").");
          }
          assertWorkingStep(step);
          if (scope.recordEpisodeBest(step)) {
            long snapshotStart = diagnosticsEnabled ? System.nanoTime() : 0L;
            try {
              best.updateBest();
            } finally {
              if (diagnosticsEnabled) snapshotNanos += System.nanoTime() - snapshotStart;
            }
          }
          callbacks.moveCommitted(step);
          completed = true;
          committedSteps++;
          termination.stepEnded(step);
          decider.stepEnded(step);
          scope.setLastCompletedStepScope(step);
        } catch (RuntimeException | Error exception) {
          decisionFailure = exception;
          throw exception;
        } finally {
          Throwable cleanup = cleanup(decisionFailure, () -> random.restoreState(savedRandom));
          if (decisionStarted && !completed) {
            cleanup = cleanup(cleanup, () -> decider.stepAborted(step));
            cleanup = cleanup(cleanup, () -> callbacks.decisionAborted(step));
          }
          if (decisionFailure == null) throwCleanup(cleanup);
        }
      }
      var outcome =
          termination.isEnclosingTerminated()
              ? Outcome.ENCLOSING_TERMINATED
              : termination.isAdoptionPending() ? Outcome.ADOPTION : Outcome.NORMAL;
      return result(outcome, best, scope.getBestScore(), scope, committedSteps, completionReason);
    } catch (CancellationException cancellation) {
      if (best != null
          && cancellation.getSuppressed().length == 0
          && termination.isNonAttemptTerminated(scope)) {
        var outcome =
            termination.isEnclosingTerminated()
                ? Outcome.ENCLOSING_TERMINATED
                : termination.isAdoptionPending() ? Outcome.ADOPTION : Outcome.NORMAL;
        return result(
            outcome, best, scope.getBestScore(), scope, committedSteps, "EPISODE_TERMINATED");
      }
      failure = cancellation;
      throw cancellation;
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      try {
        finishEpisode(scope, started, historyStarted[0], failure);
      } finally {
        consumedSelectionCount += scope.getSelectionAttemptLedger().getConsumedCount();
        discardedSelectionCount += scope.getSelectionAttemptLedger().getDiscardedCount();
        this.callbacks = null;
      }
    }
  }

  public boolean cancelAndQuiesce() {
    return decider.cancelAndQuiesceEvaluation();
  }

  public boolean isEvaluationStateSafeToDispose() {
    return decider.isEvaluationStateSafeToDispose();
  }

  public void replayWorkingState(Move<Solution_> move, InnerScore<?> score) {
    decider.replayWorkingState(move, score);
  }

  public long getUncreditedCalculationCount() {
    return decider.getUncreditedCalculationCount();
  }

  public long getDiscardedSelectionCount() {
    return discardedSelectionCount;
  }

  public long getConsumedSelectionCount() {
    return consumedSelectionCount;
  }

  public long getSnapshotNanos() {
    return snapshotNanos;
  }

  public long getEpisodeSetupNanos() {
    return episodeSetupNanos;
  }

  private EpisodeBestAssignments<Solution_> initializeBest(LocalSearchPhaseScope<Solution_> scope) {
    long start = diagnosticsEnabled ? System.nanoTime() : 0L;
    try {
      return new EpisodeBestAssignments<>(
          scope.getSolutionDescriptor(), scope.getWorkingSolution());
    } finally {
      if (diagnosticsEnabled) snapshotNanos += System.nanoTime() - start;
    }
  }

  public long getWorkerStartupCount() {
    return decider.getWorkerStartupCount();
  }

  public long getConsumedWorkerCalculationCount() {
    return decider.getConsumedWorkerCalculationCount();
  }

  public long getAdditionalWorkerCalculationCount() {
    return decider.getAdditionalWorkerCalculationCount();
  }

  private EpisodeResult<Solution_> result(
      Outcome outcome,
      EpisodeBestAssignments<Solution_> best,
      InnerScore<?> score,
      LocalSearchPhaseScope<Solution_> scope,
      int committedSteps,
      String reason) {
    if (outcome != Outcome.NORMAL) reason = outcome.name();
    else if (termination.isNonAttemptTerminated(scope)) reason = "EPISODE_TERMINATED";
    else if (reason == null)
      reason =
          scope.getSelectionAttemptLedger().isExhausted()
              ? "EPISODE_ATTEMPT_LIMIT"
              : "NO_SELECTED_MOVE";
    long snapshotStart = diagnosticsEnabled ? System.nanoTime() : 0L;
    SolutionAssignments<Solution_> bestAssignments;
    try {
      bestAssignments = best.freeze();
    } finally {
      if (diagnosticsEnabled) snapshotNanos += System.nanoTime() - snapshotStart;
    }
    return new EpisodeResult<>(
        outcome,
        bestAssignments,
        score,
        scope.getSelectionAttemptLedger().getConsumedCount(),
        committedSteps,
        reason,
        scope.calculatePhaseTimeMillisSpentUpToNow());
  }

  private boolean startEpisodeHistory(
      LocalSearchPhaseScope<Solution_> scope,
      EpisodeCallbacks<Solution_> callbacks,
      boolean[] historyStarted) {
    var setup = new LocalSearchStepScope<>(scope);
    setup.setTimeGradient(termination.calculatePhaseTimeGradient(scope));
    var random = (DefaultRandomSource) scope.getWorkingRandom();
    var savedRandom = random.moveRandom().getDelegate();
    random.restoreState(random.saveState());
    Throwable failure = null;
    try {
      callbacks.decisionStarted(setup);
      termination.stepStarted(setup);
      return scope
          .getSelectionAttemptLedger()
          .runSetup(
              () -> {
                if (!solvingHistoryStarted) {
                  solvingHistoryStarted = true;
                  decider.solvingStarted(solverScope);
                  solvingHistoryComplete = true;
                }
                historyStarted[0] = true;
                decider.phaseStarted(scope);
              });
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      Throwable cleanup = cleanup(failure, () -> random.restoreState(savedRandom));
      cleanup = cleanup(cleanup, () -> callbacks.decisionAborted(setup));
      if (failure == null) throwCleanup(cleanup);
    }
  }

  private <Score_ extends Score<Score_>> void assertWorkingStep(
      LocalSearchStepScope<Solution_> step) {
    InnerScore<Score_> score = step.getScore();
    var scope = step.getPhaseScope();
    if (environmentMode.isFullyAsserted())
      scope.assertPredictedScoreFromScratch(score, step.getStep());
    if (environmentMode.isIntrusivelyAsserted()) {
      scope.assertExpectedWorkingScore(score, step.getStep());
      scope.assertShadowVariablesAreNotStale(score, step.getStep());
    }
  }

  private void finishEpisode(
      LocalSearchPhaseScope<Solution_> scope,
      boolean started,
      boolean historyStarted,
      Throwable failure) {
    Throwable cleanup =
        cleanup(
            failure,
            () -> {
              // Never let episode history disposal race old provider/metadata reads.
              if (!decider.cancelAndQuiesceEvaluation())
                decider.endEvaluationResources(resourceScope);
            });
    if (cleanup != null)
      cleanup = cleanup(cleanup, () -> decider.endEvaluationResources(resourceScope));
    if (started) {
      cleanup = cleanup(cleanup, () -> termination.phaseEnded(scope));
      if (historyStarted) {
        if (decider.isEvaluationStateSafeToDispose())
          cleanup = cleanup(cleanup, () -> decider.phaseEnded(scope));
        else pendingHistoryCleanupScope = scope;
      }
    }
    if (!solvingHistoryComplete && decider.isEvaluationStateSafeToDispose()) {
      cleanup = cleanup(cleanup, this::endSolvingHistory);
    }
    cleanup = cleanup(cleanup, scope::endingNow);
    if (failure == null) throwCleanup(cleanup);
  }

  private static Throwable cleanup(Throwable failure, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | Error exception) {
      if (failure == null) return exception;
      if (failure != exception) failure.addSuppressed(exception);
    }
    return failure;
  }

  private static void throwCleanup(Throwable failure) {
    if (failure instanceof Error error) throw error;
    if (failure != null) throw (RuntimeException) failure;
  }

  @Override
  public void close() {
    if (!open) return;
    open = false;
    Throwable failure = cleanup(null, () -> decider.endEvaluationResources(resourceScope));
    if (!decider.isEvaluationStateSafeToDispose()) {
      // A failed join grants no permission to clear callbacks or provider references.
      open = true;
      throwCleanup(
          failure != null
              ? failure
              : new IllegalStateException("Local-search workers are still using episode state."));
      return;
    }
    if (pendingHistoryCleanupScope != null) {
      var pending = pendingHistoryCleanupScope;
      pendingHistoryCleanupScope = null;
      failure = cleanup(failure, () -> decider.phaseEnded(pending));
    }
    failure = cleanup(failure, this::endSolvingHistory);
    failure = cleanup(failure, () -> termination.solvingEnded(solverScope));
    termination.setEnclosingTermination(() -> false, () -> false);
    decider.setEvaluationResourceTermination(() -> false);
    callbacks = null;
    solverScope = null;
    resourceScope = null;
    accountingScope = null;
    throwCleanup(failure);
  }

  private void endSolvingHistory() {
    if (!solvingHistoryStarted) return;
    solvingHistoryStarted = solvingHistoryComplete = false;
    decider.solvingEnded(solverScope);
  }
}
