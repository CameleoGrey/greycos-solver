package greycos.solver.core.impl.solver;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.ProblemSizeStatistics;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import greycos.solver.core.api.solver.event.SolverJobStartedEvent;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.PossiblyInitializingPhase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.SolverTermination;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class DefaultSolverJob<Solution_>
    implements SolverJob<Solution_>, Callable<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSolverJob.class);
  private static final Duration EARLY_TERMINATION_TIMEOUT = Duration.ofMinutes(1);

  private final DefaultSolverManager<Solution_> solverManager;
  private final DefaultSolver<Solution_> solver;
  private final Object problemId;
  private final Function<? super Object, ? extends Solution_> problemFinder;
  private final @Nullable Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer;
  private final @Nullable Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer;
  private final @Nullable Consumer<FirstInitializedSolutionEvent<Solution_>>
      firstInitializedSolutionConsumer;
  private final @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer;
  private final BiConsumer<? super Object, ? super Throwable> exceptionHandler;

  private final CountDownLatch terminatedLatch;
  // Never held while finding a problem, running callbacks, completing futures or waiting.
  // Lock order: lifecycleLock -> bestSolutionHolder -> solver plumbing.
  private final Object lifecycleLock = new Object();
  private volatile boolean shutdownRequested;
  // Retained so repeated callers can help finish bookkeeping selected by another thread.
  private @Nullable TerminationActions terminationActions;
  private final AtomicBoolean terminatedEarly = new AtomicBoolean(false);
  private final AtomicBoolean cleanupScheduled = new AtomicBoolean();
  private final BestSolutionHolder<Solution_> bestSolutionHolder = new BestSolutionHolder<>();
  private final AtomicReference<SolverStatus> solverStatus =
      new AtomicReference<>(SolverStatus.SOLVING_SCHEDULED);
  private final AtomicReference<@Nullable Future<Solution_>> finalBestSolutionFuture =
      new AtomicReference<>();
  private final AtomicReference<@Nullable ConsumerSupport<Solution_, Object>> consumerSupport =
      new AtomicReference<>();
  private final AtomicReference<@Nullable ProblemSizeStatistics> temporaryProblemSizeStatistics =
      new AtomicReference<>();
  private volatile @Nullable Thread activeCallThread;

  public DefaultSolverJob(
      DefaultSolverManager<Solution_> solverManager,
      Solver<Solution_> solver,
      Object problemId,
      Function<? super Object, ? extends Solution_> problemFinder,
      @Nullable Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
      @Nullable Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
      @Nullable Consumer<FirstInitializedSolutionEvent<Solution_>> firstInitializedSolutionConsumer,
      @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer,
      BiConsumer<? super Object, ? super Throwable> exceptionHandler) {
    this.solverManager = solverManager;
    this.problemId = problemId;
    if (!(solver instanceof DefaultSolver)) {
      throw new IllegalStateException(
          "Impossible state: solver is not instance of %s."
              .formatted(DefaultSolver.class.getSimpleName()));
    }
    this.solver = (DefaultSolver<Solution_>) solver;
    this.problemFinder = problemFinder;
    this.bestSolutionConsumer = bestSolutionConsumer;
    this.finalBestSolutionConsumer = finalBestSolutionConsumer;
    this.firstInitializedSolutionConsumer = firstInitializedSolutionConsumer;
    this.solverJobStartedConsumer = solverJobStartedConsumer;
    this.exceptionHandler = exceptionHandler;
    this.terminatedLatch = new CountDownLatch(1);
  }

  public void setFinalBestSolutionFuture(Future<Solution_> finalBestSolutionFuture) {
    var oldFuture = this.finalBestSolutionFuture.getAndSet(finalBestSolutionFuture);
    if (oldFuture != null) {
      throw new IllegalStateException(
          "Impossible state: the finalBestSolutionFuture was already set to (%s)."
              .formatted(oldFuture));
    }
  }

  @Override
  public Object getProblemId() {
    return problemId;
  }

  @Override
  public SolverStatus getSolverStatus() {
    return solverStatus.get();
  }

  @Override
  public Solution_ call() {
    boolean started = false;
    boolean cancelledBeforeStart = false;
    boolean failed = false;
    try {
      if (solverStatus.get() != SolverStatus.SOLVING_SCHEDULED) {
        cancelledBeforeStart = true;
        cancelSkippedStart();
        throw new CancellationException("The solver job did not start.");
      }
      // Allocate outside the lifecycle lock and inside failure cleanup. No consumer thread starts
      // yet.
      var currentConsumerSupport =
          new ConsumerSupport<>(
              problemId,
              bestSolutionConsumer,
              finalBestSolutionConsumer,
              firstInitializedSolutionConsumer,
              solverJobStartedConsumer,
              exceptionHandler,
              bestSolutionHolder);
      synchronized (lifecycleLock) {
        if (solverStatus.get() == SolverStatus.SOLVING_SCHEDULED) {
          activeCallThread = Thread.currentThread();
          consumerSupport.set(currentConsumerSupport);
          solverStatus.set(SolverStatus.SOLVING_ACTIVE);
          started = true;
        } else {
          cancelledBeforeStart = true;
        }
      }
      if (cancelledBeforeStart) {
        cancelSkippedStart();
        currentConsumerSupport.close();
        throw new CancellationException("The solver job did not start.");
      }

      var problem = problemFinder.apply(problemId);
      // Restore durable requests after solve() resets its plumbing termination.
      solver.addPhaseLifecycleListener(new TerminationRequestPhaseLifecycleListener());
      // add a phase lifecycle listener that consumes the first initialized solution
      solver.addPhaseLifecycleListener(
          new FirstInitializedSolutionPhaseLifecycleListener(currentConsumerSupport));
      // add a phase lifecycle listener once when the solver starts its execution
      solver.addPhaseLifecycleListener(
          new StartSolverJobPhaseLifecycleListener(currentConsumerSupport));
      solver.addEventListener(this::onBestSolutionChangedEvent);
      final var finalBestSolution = solver.solve(problem);
      currentConsumerSupport.consumeFinalBestSolution(finalBestSolution);
      return finalBestSolution;
    } catch (Throwable e) {
      if (cancelledBeforeStart) {
        throw e;
      }
      failed = true;
      try {
        exceptionHandler.accept(problemId, e);
      } catch (Throwable handlerFailure) {
        LOGGER.error("The exception handler failed for problemId ({}).", problemId, handlerFailure);
      }
      throw new IllegalStateException("Solving failed for problemId (%s).".formatted(problemId), e);
    } finally {
      if (!cancelledBeforeStart) {
        try {
          solvingTerminated(failed);
        } finally {
          if (started) {
            activeCallThread = null;
          }
        }
      }
    }
  }

  private void cancelSkippedStart() {
    if (solverStatus.get() == SolverStatus.NOT_SOLVING) {
      var future = finalBestSolutionFuture.get();
      if (future != null) {
        // The winning cleanup may not have canceled this running FutureTask yet. Do it before
        // throwing, so FutureTask cannot publish an exceptional result instead of cancellation.
        future.cancel(false);
      }
    }
    // Only getFinalBestSolution() retrieves canceled input; never call problemFinder here.
  }

  private void onBestSolutionChangedEvent(
      BestSolutionChangedEvent<Solution_> bestSolutionChangedEvent) {
    var currentConsumerSupport = consumerSupport.get();
    if (currentConsumerSupport == null) {
      throw new IllegalStateException(
          """
              Impossible state: Asked to consume a best solution changed event for problemId (%s), but the consumer is not set.
              This means the solver job did not start properly or has already been terminated. This is likely a bug.
              Please report this issue to GreyCOS with details on how to reproduce it."""
              .formatted(problemId));
    }
    currentConsumerSupport.consumeIntermediateBestSolution(
        bestSolutionChangedEvent.getNewBestSolution(),
        bestSolutionChangedEvent.getProducerId(),
        bestSolutionChangedEvent::isEveryProblemChangeProcessed);
  }

  private void solvingTerminated(boolean cancelPendingChanges) {
    TerminationActions actions;
    synchronized (lifecycleLock) {
      actions = selectTermination(false, false);
    }
    finishTermination(actions);
    if (cancelPendingChanges) {
      bestSolutionHolder.cancelPendingChanges();
    }
    awaitConsumerClose();
  }

  // Called only with lifecycleLock held. Active represented changes remain for normal delivery.
  private TerminationActions selectTermination(boolean cancelFinalFuture, boolean interruptSolver) {
    if (terminationActions != null
        && (!interruptSolver || terminationActions.cancelledFuture != null)) {
      return terminationActions;
    }
    bestSolutionHolder.closeProblemChangeAdmission();
    var pendingChanges =
        cancelFinalFuture
            ? bestSolutionHolder.closeAndDrainPendingChanges()
            : List.<CompletableFuture<Void>>of();
    solverStatus.set(SolverStatus.NOT_SOLVING);
    terminationActions =
        new TerminationActions(
            cancelFinalFuture ? finalBestSolutionFuture.get() : null,
            pendingChanges,
            interruptSolver);
    return terminationActions;
  }

  private void finishTermination(@Nullable TerminationActions actions) {
    completeTerminalBookkeeping(actions);
    cancelPendingChanges(actions);
  }

  private void completeTerminalBookkeeping(@Nullable TerminationActions actions) {
    if (actions == null) {
      return;
    }
    // These actions are idempotent. A concurrent caller need not await the selecting thread.
    // Manager shutdown completes this stage for every job before dispatching change callbacks.
    if (actions.cancelledFuture != null) {
      actions.cancelledFuture.cancel(actions.interruptSolver);
    }
    if (actions.interruptSolver) {
      var currentConsumerSupport = consumerSupport.get();
      if (currentConsumerSupport != null) {
        // Seal event admission before releasing terminal waiters, without draining callbacks here.
        currentConsumerSupport.requestClose();
      }
    }
    solverManager.unregisterSolverJob(problemId, this);
    terminatedLatch.countDown();
  }

  private void cancelPendingChanges(@Nullable TerminationActions actions) {
    if (actions != null) {
      synchronized (actions) {
        if (!actions.pendingChanges.isEmpty()) {
          bestSolutionHolder.cancelPendingChanges(actions.pendingChanges);
          // Tasks retain only the futures whose inline continuations are still running.
          actions.pendingChanges = List.of();
        }
      }
    }
  }

  private static final class TerminationActions {
    private final @Nullable Future<?> cancelledFuture;
    private List<CompletableFuture<Void>> pendingChanges;
    private final boolean interruptSolver;

    private TerminationActions(
        @Nullable Future<?> cancelledFuture,
        List<CompletableFuture<Void>> pendingChanges,
        boolean interruptSolver) {
      this.cancelledFuture = cancelledFuture;
      this.pendingChanges = pendingChanges;
      this.interruptSolver = interruptSolver;
    }
  }

  @Override
  public CompletableFuture<Void> addProblemChanges(
      List<ProblemChange<Solution_>> problemChangeList) {
    Objects.requireNonNull(
        problemChangeList,
        () -> "A problem change list for problem (%s) must not be null.".formatted(problemId));
    if (problemChangeList.isEmpty()) {
      throw new IllegalArgumentException(
          "The problem change list for problem (%s) must not be empty.".formatted(problemId));
    }
    var currentSolverStatus = solverStatus.get();
    if (currentSolverStatus == SolverStatus.NOT_SOLVING) {
      throw new IllegalStateException(
          "Cannot add the problem changes (%s) because the solver job (%s) is not solving."
              .formatted(problemChangeList, currentSolverStatus));
    }

    return bestSolutionHolder.addProblemChange(solver, problemChangeList);
  }

  @Override
  public void terminateEarly() {
    terminateEarly(earlyTerminationDeadline());
  }

  static long earlyTerminationDeadline() {
    return System.nanoTime() + EARLY_TERMINATION_TIMEOUT.toNanos();
  }

  // An absolute monotonic deadline keeps startup, repeated calls and cancellation publication
  // within the same budget. Package-private for short, deterministic lifecycle tests.
  void terminateEarly(long deadlineNanos) {
    TerminationActions actions;
    boolean terminateSolver;
    synchronized (lifecycleLock) {
      terminatedEarly.set(true);
      bestSolutionHolder.closeProblemChangeAdmission();
      terminateSolver = solverStatus.get() == SolverStatus.SOLVING_ACTIVE;
      actions =
          solverStatus.get() == SolverStatus.SOLVING_SCHEDULED
              ? selectTermination(true, false)
              : terminationActions;
    }
    if (terminateSolver) {
      // Application problem finders and startup code never block recording this request.
      // The solvingStarted listener restores it if solve() subsequently resets termination.
      solver.terminateEarly();
    }
    finishTermination(actions);
    if (actions != null) {
      scheduleCleanup();
    }
    if (Thread.currentThread() == activeCallThread || SolverEventThreadContext.isActive()) {
      return;
    }
    try {
      // Every external caller waits, including a repeated call after callback cancellation and
      // NOT_SOLVING published before another thread finishes terminal bookkeeping.
      if (!terminatedLatch.await(
          Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
        LOGGER.warn(
            "The terminateEarly() wait expired for problemId ({}). "
                + "The solver may still be running; subsequent solutions will be ignored.",
            problemId);
        synchronized (lifecycleLock) {
          actions = selectTermination(true, true);
        }
        finishTermination(actions);
        scheduleCleanup();
      } else {
        synchronized (lifecycleLock) {
          actions = terminationActions;
        }
        // Another thread may have completed bookkeeping but not yet dispatched cancellation.
        cancelPendingChanges(actions);
      }
      bestSolutionHolder.awaitCancellationPublication(deadlineNanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.debug("The terminateEarly() wait is interrupted for problemId ({}).", problemId);
    }
  }

  @Override
  public boolean isTerminatedEarly() {
    return terminatedEarly.get();
  }

  @Override
  public Solution_ getFinalBestSolution() throws InterruptedException, ExecutionException {
    try {
      var future = finalBestSolutionFuture.get();
      if (future == null) {
        throw new IllegalStateException(
            "Impossible state: the finalBestSolutionFuture is not set yet for problemId (%s)."
                .formatted(problemId));
      }
      return future.get();
    } catch (CancellationException cancellationException) {
      LOGGER.debug(
          "terminateEarly() has been called before the solver job started solving. "
              + "Retrieving the input problem instead.");
      return problemFinder.apply(problemId);
    }
  }

  @Override
  public Duration getSolvingDuration() {
    return Duration.ofMillis(solver.getTimeMillisSpent());
  }

  @Override
  public long getScoreCalculationCount() {
    return solver.getScoreCalculationCount();
  }

  @Override
  public long getMoveEvaluationCount() {
    return solver.getMoveEvaluationCount();
  }

  @Override
  public long getScoreCalculationSpeed() {
    return solver.getScoreCalculationSpeed();
  }

  @Override
  public long getMoveEvaluationSpeed() {
    return solver.getMoveEvaluationSpeed();
  }

  @Override
  public ProblemSizeStatistics getProblemSizeStatistics() {
    var solverScope = solver.getSolverScope();
    var problemSizeStatistics = solverScope.getProblemSizeStatistics();
    if (problemSizeStatistics != null) {
      temporaryProblemSizeStatistics.set(null);
      return problemSizeStatistics;
    }
    // Solving has not started yet; we do not have a working solution.
    // Therefore we cannot rely on ScoreDirector's ValueRangeManager
    // and we need to use a new cold instance.
    // This will be inefficient on account of recomputing all the value ranges,
    // but it only exists to solve a corner case of accessing the problem size statistics
    // before the solving has started.
    // Once the solving has started, the problem size statistics will be computed
    // using the ScoreDirector's hot ValueRangeManager.
    var result =
        temporaryProblemSizeStatistics.updateAndGet(
            oldStatistics -> {
              if (oldStatistics != null) {
                // If the problem size statistics were already computed, return them.
                // This can happen if the problem size statistics were computed before the solving
                // started.
                return oldStatistics;
              }
              var solutionDescriptor = solverScope.getSolutionDescriptor();
              var valueManager =
                  ValueRangeManager.of(solutionDescriptor, problemFinder.apply(problemId));
              return valueManager.getProblemSizeStatistics();
            });
    // Avoids nullness issues reported by IDE which cannot actually happen.
    // The result can never be null, because none of the methods called in the lambda can return
    // null, and the lambda is the only way to set the value of the
    // temporaryProblemSizeStatistics, which is the only way for it to be null.
    return Objects.requireNonNull(result);
  }

  public SolverTermination<Solution_> getSolverTermination() {
    return solver.globalTermination;
  }

  void close() {
    requestClose();
    awaitConsumerClose();
  }

  void requestClose() {
    var action = prepareClose();
    action.completeTerminalBookkeeping();
    action.requestSolverTermination();
    action.finishClose();
  }

  CloseAction prepareClose() {
    TerminationActions actions = null;
    boolean terminateSolver = false;
    @Nullable ConsumerSupport<Solution_, Object> currentConsumerSupport;
    synchronized (lifecycleLock) {
      shutdownRequested = true;
      switch (solverStatus.get()) {
        case SOLVING_SCHEDULED:
          actions = selectTermination(true, false);
          break;
        case SOLVING_ACTIVE:
          bestSolutionHolder.closeProblemChangeAdmission();
          terminateSolver = true;
          break;
        case NOT_SOLVING:
          // Another caller may have published NOT_SOLVING before canceling the final future.
          // Include its selected work in the manager's all-jobs bookkeeping barrier.
          actions = terminationActions;
          break;
      }
      currentConsumerSupport = consumerSupport.get();
    }
    return new CloseAction(actions, terminateSolver, currentConsumerSupport);
  }

  final class CloseAction {
    private final @Nullable TerminationActions actions;
    private final boolean terminateSolver;
    private final @Nullable ConsumerSupport<Solution_, Object> currentConsumerSupport;

    private CloseAction(
        @Nullable TerminationActions actions,
        boolean terminateSolver,
        @Nullable ConsumerSupport<Solution_, Object> currentConsumerSupport) {
      this.actions = actions;
      this.terminateSolver = terminateSolver;
      this.currentConsumerSupport = currentConsumerSupport;
    }

    void completeTerminalBookkeeping() {
      DefaultSolverJob.this.completeTerminalBookkeeping(actions);
    }

    void requestSolverTermination() {
      if (terminateSolver) {
        // Do not acquire the startup lock: a problem finder may be blocked in application code.
        solver.terminateEarly();
      }
    }

    void finishClose() {
      cancelPendingChanges(actions);
      if (currentConsumerSupport != null) {
        currentConsumerSupport.requestClose();
      }
      scheduleCleanup();
    }
  }

  private void scheduleCleanup() {
    if (cleanupScheduled.compareAndSet(false, true)) {
      // A canceled queued job has no solver thread to drain its continuations. Keep public
      // manager.close() ownership until actual callback cleanup, independently of solver exit.
      Thread.ofVirtual().name("solver-job-cleanup").start(this::awaitConsumerClose);
    }
  }

  void awaitConsumerClose() {
    var currentConsumerSupport = consumerSupport.get();
    if (currentConsumerSupport != null) {
      currentConsumerSupport.close();
    }
    TerminationActions actions;
    synchronized (lifecycleLock) {
      actions = terminationActions;
    }
    cancelPendingChanges(actions);
    bestSolutionHolder.awaitCancellationCompletion();
    if (!SolverEventThreadContext.isActive()) {
      solverManager.unregisterSolverJobCleanup(this);
    }
  }

  /** Restores termination requests made before solve() resets plumbing termination. */
  private final class TerminationRequestPhaseLifecycleListener
      extends PhaseLifecycleListenerAdapter<Solution_> {
    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
      if (terminatedEarly.get() || shutdownRequested) {
        solver.terminateEarly();
      }
    }
  }

  /**
   * A listener that consumes the solution from a phase only if the phase first initializes the
   * solution.
   */
  private final class FirstInitializedSolutionPhaseLifecycleListener
      extends PhaseLifecycleListenerAdapter<Solution_> {

    private final ConsumerSupport<Solution_, Object> consumerSupport;

    public FirstInitializedSolutionPhaseLifecycleListener(
        ConsumerSupport<Solution_, Object> consumerSupport) {
      this.consumerSupport = consumerSupport;
    }

    @Override
    public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
      var eventPhase =
          solver.getPhaseList().stream()
              .filter(
                  phase ->
                      ((AbstractPhase<Solution_>) phase).getPhaseIndex()
                          == phaseScope.getPhaseIndex())
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Impossible state: Solving failed for problemId (%s) because the phase id %d was not found."
                              .formatted(problemId, phaseScope.getPhaseIndex())));
      if (eventPhase instanceof PossiblyInitializingPhase<Solution_> possiblyInitializingPhase
          && possiblyInitializingPhase.isLastInitializingPhase()) {
        // The Solver thread calls the method,
        // but the consumption is done asynchronously by the Consumer thread.
        // Only happens if the phase initializes the solution.
        consumerSupport.consumeFirstInitializedSolution(
            phaseScope.getWorkingSolution(),
            phaseScope.getPhaseId(),
            possiblyInitializingPhase.getTerminationStatus().early());
      }
    }
  }

  /** A listener that is triggered once when the solver starts the solving process. */
  private final class StartSolverJobPhaseLifecycleListener
      extends PhaseLifecycleListenerAdapter<Solution_> {

    private final ConsumerSupport<Solution_, Object> consumerSupport;

    public StartSolverJobPhaseLifecycleListener(
        ConsumerSupport<Solution_, Object> consumerSupport) {
      this.consumerSupport = consumerSupport;
    }

    @Override
    public void solvingStarted(SolverScope<Solution_> solverScope) {
      if (solverJobStartedConsumer != null) {
        consumerSupport.consumeStartSolverJob(
            solverScope.getScoreDirector().cloneWorkingSolution());
      }
    }
  }
}
