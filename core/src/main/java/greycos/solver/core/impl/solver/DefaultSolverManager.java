package greycos.solver.core.impl.solver;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.SolverConfigOverride;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverJob;
import greycos.solver.core.api.solver.SolverJobBuilder;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.api.solver.SolverStatus;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.api.solver.event.FinalBestSolutionEvent;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.api.solver.event.NewBestSolutionEvent;
import greycos.solver.core.api.solver.event.SolverJobStartedEvent;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.solver.monitoring.SolverTags;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class DefaultSolverManager<Solution_> implements SolverManager<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSolverManager.class);

  private final BiConsumer<Object, Throwable> defaultExceptionHandler;
  private final SolverFactory<Solution_> solverFactory;
  private final ExecutorService solverThreadPool;
  private final ConcurrentMap<Object, DefaultSolverJob<Solution_>> problemIdToSolverJobMap;
  // Job IDs may be reused as soon as solving ends; callback cleanup has separate ownership.
  private final Set<DefaultSolverJob<Solution_>> solverJobsAwaitingCleanup =
      ConcurrentHashMap.newKeySet();
  private final Object admissionLock = new Object();
  // Protected by admissionLock, together with registration and executor submission.
  private boolean closed;

  public DefaultSolverManager(
      SolverFactory<Solution_> solverFactory, SolverManagerConfig solverManagerConfig) {
    this.defaultExceptionHandler =
        (problemId, throwable) ->
            LOGGER.error("Solving failed for problemId ({}).", problemId, throwable);
    this.solverFactory = solverFactory;
    validateSolverFactory();
    var parallelSolverCount = solverManagerConfig.resolveParallelSolverCount();
    var threadFactoryClass = solverManagerConfig.getThreadFactoryClass();
    var threadFactory =
        threadFactoryClass == null
            ? Executors.defaultThreadFactory()
            : ConfigUtils.newInstance(
                solverManagerConfig, "threadFactoryClass", threadFactoryClass);
    solverThreadPool = Executors.newFixedThreadPool(parallelSolverCount, threadFactory);
    problemIdToSolverJobMap = new ConcurrentHashMap<>(parallelSolverCount * 10);
  }

  public SolverFactory<Solution_> getSolverFactory() {
    return solverFactory;
  }

  private void validateSolverFactory() {
    solverFactory.buildSolver();
  }

  private static Object getProblemIdOrThrow(Object problemId) {
    return Objects.requireNonNull(problemId, "Invalid problemId (null) given to SolverManager.");
  }

  private @Nullable DefaultSolverJob<Solution_> getSolverJob(Object problemId) {
    return problemIdToSolverJobMap.get(getProblemIdOrThrow(problemId));
  }

  @Override
  public SolverJobBuilder<Solution_> solveBuilder() {
    return new DefaultSolverJobBuilder<>(this);
  }

  SolverJob<Solution_> solveAndListen(
      Object problemId,
      Function<? super Object, ? extends Solution_> problemFinder,
      Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
      @Nullable Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
      @Nullable Consumer<FirstInitializedSolutionEvent<Solution_>> initializedSolutionConsumer,
      @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer,
      @Nullable BiConsumer<? super Object, ? super Throwable> exceptionHandler,
      SolverConfigOverride solverConfigOverride) {
    return solve(
        problemId,
        problemFinder,
        bestSolutionConsumer,
        finalBestSolutionConsumer,
        initializedSolutionConsumer,
        solverJobStartedConsumer,
        exceptionHandler,
        solverConfigOverride);
  }

  SolverJob<Solution_> solve(
      Object problemId,
      Function<? super Object, ? extends Solution_> problemFinder,
      @Nullable Consumer<NewBestSolutionEvent<Solution_>> bestSolutionConsumer,
      @Nullable Consumer<FinalBestSolutionEvent<Solution_>> finalBestSolutionConsumer,
      @Nullable Consumer<FirstInitializedSolutionEvent<Solution_>> initializedSolutionConsumer,
      @Nullable Consumer<SolverJobStartedEvent<Solution_>> solverJobStartedConsumer,
      @Nullable BiConsumer<? super Object, ? super Throwable> exceptionHandler,
      SolverConfigOverride configOverride) {
    var solver = solverFactory.buildSolver(configOverride);
    ((DefaultSolver<Solution_>) solver).setMonitorTags(SolverTags.withProblemId(problemId));
    BiConsumer<? super Object, ? super Throwable> finalExceptionHandler =
        (exceptionHandler != null) ? exceptionHandler : defaultExceptionHandler;
    var solverJob =
        new DefaultSolverJob<>(
            this,
            solver,
            problemId,
            problemFinder,
            bestSolutionConsumer,
            finalBestSolutionConsumer,
            initializedSolutionConsumer,
            solverJobStartedConsumer,
            finalExceptionHandler);
    // Install the future before publishing the job; cancellation may immediately find it.
    var future = new FutureTask<>(solverJob);
    solverJob.setFinalBestSolutionFuture(future);
    try {
      synchronized (admissionLock) {
        if (closed) {
          throw new RejectedExecutionException("The solver manager is already closed.");
        }
        // Install cleanup ownership before publishing the job to concurrent cancellation.
        solverJobsAwaitingCleanup.add(solverJob);
        if (problemIdToSolverJobMap.putIfAbsent(problemId, solverJob) != null) {
          throw new IllegalStateException(
              "The problemId (%s) is already solving.".formatted(problemId));
        }
        try {
          solverThreadPool.execute(future);
        } catch (RuntimeException | Error failure) {
          problemIdToSolverJobMap.remove(problemId, solverJob);
          throw failure;
        }
      }
    } catch (RuntimeException | Error failure) {
      // Future completion may invoke application code. Never do it under admissionLock.
      try {
        solverJob.close();
      } catch (Throwable cleanupFailure) {
        if (cleanupFailure != failure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      throw failure;
    }
    return solverJob;
  }

  @Override
  public SolverStatus getSolverStatus(Object problemId) {
    var solverJob = getSolverJob(problemId);
    if (solverJob == null) {
      return SolverStatus.NOT_SOLVING;
    }
    return solverJob.getSolverStatus();
  }

  @Override
  public CompletableFuture<Void> addProblemChanges(
      Object problemId, List<ProblemChange<Solution_>> problemChangeList) {
    var solverJob = getSolverJob(problemId);
    if (solverJob == null) {
      // We cannot distinguish between "already terminated" and "never solved" without causing a
      // memory leak.
      throw new IllegalStateException(
          "Cannot add the problem changes (%s) because there is no solver solving the problemId (%s)."
              .formatted(problemChangeList, problemId));
    }
    return solverJob.addProblemChanges(problemChangeList);
  }

  @Override
  public void terminateEarly(Object problemId) {
    long deadlineNanos = DefaultSolverJob.earlyTerminationDeadline();
    var solverJob = getSolverJob(problemId);
    if (solverJob == null) {
      // We cannot distinguish between "already terminated" and "never solved" without causing a
      // memory leak.
      LOGGER.debug(
          "Ignoring terminateEarly() call because problemId ({}) is not solving.", problemId);
      return;
    }
    solverJob.terminateEarly(deadlineNanos);
  }

  @Override
  public void close() {
    List<DefaultSolverJob<Solution_>> solverJobs;
    synchronized (admissionLock) {
      closed = true;
      solverJobs = List.copyOf(solverJobsAwaitingCleanup);
      solverThreadPool.shutdownNow();
    }
    var closeActions = solverJobs.stream().map(DefaultSolverJob::prepareClose).toList();
    // A synchronous change-future continuation can retrieve another queued job's final solution.
    // Cancel every queued final future before any change completion or consumer shutdown.
    closeActions.forEach(action -> action.completeTerminalBookkeeping());
    closeActions.forEach(action -> action.requestSolverTermination());
    closeActions.forEach(action -> action.finishClose());
    // An active problem finder must not prevent queued cleanup or hold a startup lock here.
    solverJobs.forEach(DefaultSolverJob::awaitConsumerClose);
  }

  void unregisterSolverJobCleanup(DefaultSolverJob<Solution_> solverJob) {
    solverJobsAwaitingCleanup.remove(solverJob);
  }

  void unregisterSolverJob(Object problemId, DefaultSolverJob<Solution_> solverJob) {
    problemIdToSolverJobMap.remove(getProblemIdOrThrow(problemId), solverJob);
  }
}
