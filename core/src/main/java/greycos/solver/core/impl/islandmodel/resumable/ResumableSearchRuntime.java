package greycos.solver.core.impl.islandmodel.resumable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.config.util.ResumableSearchConfigResolver;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.islandmodel.IslandModelPhaseScope;
import greycos.solver.core.impl.islandmodel.SolutionSyncMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionOwnership;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.PhaseFactory;
import greycos.solver.core.impl.phase.ResumablePhaseExecution;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.impl.solver.ClassInstanceCache;
import greycos.solver.core.impl.solver.change.DefaultProblemChangeDirector;
import greycos.solver.core.impl.solver.monitoring.ResumableSearchWorkCategory;
import greycos.solver.core.impl.solver.monitoring.ResumableSearchWorkCounter;
import greycos.solver.core.impl.solver.monitoring.ResumableSearchWorkSnapshot;
import greycos.solver.core.impl.solver.monitoring.SolverMetricRun;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.ChildThreadPlumbingTermination;
import greycos.solver.core.impl.solver.termination.ChildThreadSupportingTermination;
import greycos.solver.core.impl.solver.termination.PartitionTerminationBudget;
import greycos.solver.core.impl.solver.termination.ResumableSearchBudget;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.termination.TerminationFactory;
import greycos.solver.core.impl.solver.termination.UniversalTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.ThreadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advances a complete island/partition hierarchy on fixed owner workers. Only the calling solver
 * thread admits work and commits round results; an owner never blocks waiting for a descendant.
 */
public final class ResumableSearchRuntime<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResumableSearchRuntime.class);

  private static final ThreadLocal<Consumer<RoundSnapshot<?>>> ROUND_OBSERVER = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> TRACE_STEPS = new ThreadLocal<>();
  private static final ThreadLocal<Consumer<CompletionSnapshot<?>>> COMPLETION_OBSERVER =
      new ThreadLocal<>();

  public record StepTrace<Solution_>(
      int phaseIndex,
      int stepIndex,
      String moveDescription,
      InnerScore<?> score,
      Solution_ solution) {}

  public record SessionSummary<Solution_>(
      String path,
      long episode,
      long completedSteps,
      long moveEvaluations,
      long scoreCalculations,
      InnerScore<?> bestScore,
      Solution_ bestSolution,
      List<StepTrace<Solution_>> steps,
      boolean finished,
      long physicalScoreCalculations,
      Map<ResumableSearchWorkCategory, Long> scoreCalculationCategories,
      long completedSearchSteps,
      Map<String, Long> moveCountsByType) {
    public SessionSummary {
      steps = List.copyOf(steps);
      scoreCalculationCategories = Map.copyOf(scoreCalculationCategories);
      moveCountsByType = Map.copyOf(moveCountsByType);
    }
  }

  public record RoundSnapshot<Solution_>(
      long round,
      List<SessionSummary<Solution_>> sessions,
      ResumableSearchBudget.Work budgetConsumption,
      ResumableSearchWorkSnapshot physicalWork,
      boolean finished,
      long modelRevision,
      int activeSessionCount,
      int activeContainerCount,
      int workerCount) {
    public RoundSnapshot {
      sessions = List.copyOf(sessions);
    }
  }

  public record CompletionSnapshot<Solution_>(
      long completedRounds,
      long modelRevision,
      ResumableSearchBudget.Work budgetConsumption,
      ResumableSearchWorkSnapshot physicalWork,
      InnerScore<?> bestScore,
      Solution_ bestSolution) {}

  public static AutoCloseable observeCompletion(Consumer<CompletionSnapshot<?>> observer) {
    var previous = COMPLETION_OBSERVER.get();
    COMPLETION_OBSERVER.set(Objects.requireNonNull(observer));
    return () -> {
      if (previous == null) COMPLETION_OBSERVER.remove();
      else COMPLETION_OBSERVER.set(previous);
    };
  }

  /** Installs an optional diagnostic observer only for solves invoked by the calling thread. */
  public static AutoCloseable observeRounds(Consumer<RoundSnapshot<?>> observer) {
    return observeRounds(observer, true);
  }

  /** Summary-only observation avoids cloning the working solution at every primitive step. */
  public static AutoCloseable observeRounds(
      Consumer<RoundSnapshot<?>> observer, boolean traceSteps) {
    var previous = ROUND_OBSERVER.get();
    var previousTrace = TRACE_STEPS.get();
    ROUND_OBSERVER.set(Objects.requireNonNull(observer));
    TRACE_STEPS.set(traceSteps);
    return () -> {
      if (previous == null) ROUND_OBSERVER.remove();
      else ROUND_OBSERVER.set(previous);
      if (previousTrace == null) TRACE_STEPS.remove();
      else TRACE_STEPS.set(previousTrace);
    };
  }

  public static boolean containsResumableSearch(List<? extends PhaseConfig> phases) {
    if (phases == null) return false;
    for (var phase : phases) {
      if (phase instanceof IslandModelPhaseConfig island) {
        if (island.getResumableSearchConfig() != null
            || containsResumableSearch(island.getPhaseConfigList())) return true;
      } else if (phase instanceof PartitionedSearchPhaseConfig partition
          && containsResumableSearch(partition.getPhaseConfigList())) return true;
    }
    return false;
  }

  public static <Solution_> void solve(
      int phaseIndex,
      PhaseConfig<?> rootConfig,
      HeuristicConfigPolicy<Solution_> policy,
      BestSolutionRecaller<Solution_> outerRecaller,
      SolverTermination<Solution_> inheritedTermination,
      SolverScope<Solution_> outerScope) {
    new ResumableSearchRuntime<>(phaseIndex, rootConfig, policy, inheritedTermination, outerScope)
        .run();
  }

  private final int phaseIndex;
  private final PhaseConfig<?> rootConfig;
  private final HeuristicConfigPolicy<Solution_> policy;
  private final SolverScope<Solution_> outerScope;
  private final ScoreDirectorFactory<Solution_, ?> scoreDirectorFactory;
  private final ExecutorService[] owners;
  private final int quantum;
  private final long rootSeed;
  private final long revision;
  private final Consumer<RoundSnapshot<?>> observer;
  private final boolean traceSteps;
  private final Consumer<CompletionSnapshot<?>> completionObserver;
  private final ChildThreadPlumbingTermination<Solution_> cancellation =
      new ChildThreadPlumbingTermination<>();
  private final AtomicReference<Throwable> ownerFailure = new AtomicReference<>();
  private final AtomicBoolean ownersInterrupted = new AtomicBoolean();
  private final List<Session> sessions = new ArrayList<>();
  private final List<Container> containers = new ArrayList<>();
  private final ResumableSearchBudget<Solution_> solverBudget;
  private Container root;
  private long round;
  private long completedRounds;
  private long nextSessionOrdinal;
  private boolean closing;
  private boolean inRound;
  private boolean discardUncommittedBest;
  private boolean startupAborted;
  private final long initialRootCalculations;
  private final long initialRootMoves;
  private final ResumableSearchWorkCounter rootWork = new ResumableSearchWorkCounter();
  private final Map<String, ResumableSearchWorkSnapshot> physicalSources =
      new java.util.HashMap<>();
  private ResumableSearchWorkSnapshot retiredPhysicalWork = ResumableSearchWorkSnapshot.ZERO;
  private long cleanupDeadlineNanos;
  private boolean restoreInterrupt;
  private final List<Thread> ownerThreads =
      java.util.Collections.synchronizedList(new ArrayList<>());

  private ResumableSearchRuntime(
      int phaseIndex,
      PhaseConfig<?> rootConfig,
      HeuristicConfigPolicy<Solution_> policy,
      SolverTermination<Solution_> inheritedTermination,
      SolverScope<Solution_> outerScope) {
    this.phaseIndex = phaseIndex;
    this.rootConfig = rootConfig;
    this.policy = policy;
    this.outerScope = outerScope;
    this.scoreDirectorFactory = outerScope.getSolver().getScoreDirectorFactory();
    var resolved = Objects.requireNonNull(ResumableSearchConfigResolver.resolve(rootConfig));
    quantum = resolved.stepQuantum();
    rootSeed = ((DefaultRandomSource) outerScope.getWorkingRandom()).moveRandom().getSeed();
    revision = outerScope.getResumableSearchModelRevision();
    observer = ROUND_OBSERVER.get();
    traceSteps = observer != null && !Boolean.FALSE.equals(TRACE_STEPS.get());
    completionObserver = COMPLETION_OBSERVER.get();
    initialRootCalculations = outerScope.getScoreCalculationCount();
    initialRootMoves = outerScope.getMoveEvaluationCount();
    solverBudget =
        ResumableSearchBudget.forSolver(
            "solver",
            "phase-" + index(phaseIndex),
            inheritedTermination,
            outerScope,
            outerScope.getResumableSearchState(),
            outerScope.getClock(),
            outerScope.getStartingSystemTimeMillis());
    owners = new ExecutorService[resolved.workerCount()];
    var factory =
        ThreadUtils.requireNonNullThreads(
            policy.buildThreadFactory(ChildThreadType.PART_THREAD), "Resumable search");
    for (int i = 0; i < owners.length; i++) {
      final int ownerIndex = i;
      owners[i] =
          Executors.newSingleThreadExecutor(
              task -> {
                var thread = factory.newThread(task);
                ownerThreads.add(thread);
                outerScope
                    .getWorkerRegistry()
                    .registerThread(thread, "resumable search owner " + ownerIndex);
                return thread;
              });
      outerScope.getWorkerRegistry().registerExecutor(owners[i], "resumable search owner " + i);
    }
  }

  private void run() {
    Throwable failure = null;
    var previousRootWorkCounter = outerScope.getScoreDirector().getResumableSearchWorkCounter();
    outerScope.getScoreDirector().setResumableSearchWorkCounter(rootWork);
    try {
      LOGGER.info(
          "Resumable search phase ({}) starting: workers ({}), completed-step quantum ({}), revision ({}), hierarchy ({}).",
          phaseIndex,
          owners.length,
          quantum,
          revision,
          rootConfig.getClass().getSimpleName());
      root =
          new Container(
              null,
              outerScope,
              -1,
              rootConfig,
              outerScope.getSolver().getPhaseList().get(phaseIndex),
              phaseIndex,
              "phase-" + index(phaseIndex));
      openContainer(root);
      while (!root.complete && !stopping()) {
        settle();
        if (root.complete || stopping()) break;
        var runnable =
            sessions.stream()
                .filter(s -> !s.closed && s.execution != null)
                .sorted(Comparator.comparing(s -> s.path))
                .toList();
        if (runnable.isEmpty()) {
          throw new IllegalStateException(
              "The resumable hierarchy has no runnable leaf or completed container.");
        }
        long remaining = solverBudget.remainingStepCount();
        var grants = new ArrayList<Grant>();
        inRound = true;
        for (var session : runnable) {
          long grant = Math.min(quantum, remaining);
          for (var ancestor = session.parent;
              ancestor != null;
              ancestor = ancestor.parentContainer()) {
            grant =
                Math.min(grant, ancestor.budget.remainingStepCount() - reserved(grants, ancestor));
          }
          for (var member = session; member != null; member = member.parent.session) {
            if (member.memberBudget != null) {
              long reserved = 0L;
              for (var earlier : grants)
                if (earlier.session.isDescendantOf(member)) reserved += earlier.steps;
              grant = Math.min(grant, member.memberBudget.remainingStepCount() - reserved);
            }
          }
          if (grant <= 0L) continue;
          long admitted = grant;
          grants.add(
              new Grant(
                  session, grant, owners[session.owner].submit(() -> session.advance(admitted))));
          if (remaining != Long.MAX_VALUE) remaining -= grant;
        }
        if (grants.isEmpty()) {
          inRound = false;
          break;
        }
        var summaries = new ArrayList<SessionSummary<Solution_>>(grants.size());
        // No logical predicate observes a partly committed round while another owner is running.
        for (var grant : grants) summaries.add(await(grant.future));
        // A short round may finish without a timed wait; asynchronous stop still precedes
        // publication.
        if (stopping()) {
          discardUncommittedBest = true;
          cancellation.terminateChildren();
        }
        for (int i = 0; i < grants.size(); i++) commit(grants.get(i).session, summaries.get(i));
        inRound = false;
        if (!discardUncommittedBest) {
          completedRounds++;
          reduceContainers(true);
          if (outerScope.hasMetricSampleListeners()) {
            outerScope.publishMetricSample(SolverMetricSamples.captureResumableRound(outerScope));
          }
          observe(summaries, false);
          round++;
        }
      }
      if (ownerStopping()) discardUncommittedBest = true;
      finishAll(false, null);
      reduceContainers(false);
      closeContainer(root);
      if (completionObserver != null)
        completionObserver.accept(
            new CompletionSnapshot<>(
                completedRounds,
                revision,
                root.budget.consumption(),
                physicalWork(),
                root.bestScore,
                root.bestSolution));
      LOGGER.info(
          "Resumable search phase ({}) ended: rounds ({}), completed steps ({}), move evaluations ({}), logical score calculations ({}), physical score calculations ({}).",
          phaseIndex,
          round,
          root.budget.consumption().completedSteps(),
          root.budget.consumption().moveEvaluations(),
          root.budget.consumption().scoreCalculations(),
          physicalWork().totalScoreCalculationCount());
    } catch (RuntimeException | Error e) {
      failure = e;
      restoreInterrupt |= Thread.interrupted();
      cancellation.terminateChildren();
      interruptOwners();
      for (var session : sessions) session.metricRun.seal();
      try {
        finishAll(true, e);
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != e) e.addSuppressed(cleanup);
      }
      throw e;
    } finally {
      closing = true;
      cancellation.terminateChildren();
      for (var owner : owners) owner.shutdown();
      boolean interrupted = Thread.interrupted() | restoreInterrupt;
      RuntimeException shutdownFailure = null;
      long shutdownDeadline =
          cleanupDeadlineNanos == 0L
              ? System.nanoTime()
                  + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout())
              : cleanupDeadlineNanos;
      for (var owner : owners) {
        try {
          if (!owner.awaitTermination(
              Math.max(0L, shutdownDeadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            owner.shutdownNow();
            var timeout =
                new IllegalStateException(
                    "Resumable search owner did not stop; the solver cannot be reused yet.");
            if (failure != null) failure.addSuppressed(timeout);
            else if (shutdownFailure == null) shutdownFailure = timeout;
            else shutdownFailure.addSuppressed(timeout);
          }
        } catch (InterruptedException e) {
          interrupted = true;
          owner.shutdownNow();
          if (failure != null) failure.addSuppressed(e);
          else if (shutdownFailure == null)
            shutdownFailure =
                new IllegalStateException("Interrupted while stopping resumable owners.", e);
          else shutdownFailure.addSuppressed(e);
        }
      }
      for (var thread : List.copyOf(ownerThreads)) {
        if (thread.getState() == Thread.State.NEW) {
          outerScope.getWorkerRegistry().unregisterThread(thread);
          continue;
        }
        while (thread.isAlive()) {
          long remaining = shutdownDeadline - System.nanoTime();
          if (remaining <= 0L) {
            var timeout =
                new IllegalStateException(
                    "Resumable search owner thread did not exit before cleanup deadline.");
            if (failure != null) failure.addSuppressed(timeout);
            else if (shutdownFailure == null) shutdownFailure = timeout;
            else shutdownFailure.addSuppressed(timeout);
            break;
          }
          try {
            TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      }
      if (interrupted) Thread.currentThread().interrupt();
      outerScope.getScoreDirector().setResumableSearchWorkCounter(previousRootWorkCounter);
      if (shutdownFailure != null) throw shutdownFailure;
    }
  }

  private void observe(List<SessionSummary<Solution_>> summaries, boolean finished) {
    if (observer != null)
      observer.accept(
          new RoundSnapshot<>(
              round,
              summaries,
              root.budget.consumption(),
              physicalWork(),
              finished,
              revision,
              sessions.size(),
              containers.size(),
              owners.length));
  }

  private ResumableSearchWorkSnapshot physicalWork() {
    var total = retiredPhysicalWork.plus(rootWork.snapshot());
    for (var work : physicalSources.values()) total = total.plus(work);
    return total;
  }

  private long reserved(List<Grant> grants, Container ancestor) {
    long total = 0;
    for (var grant : grants) if (grant.session.isDescendantOf(ancestor)) total += grant.steps;
    return total;
  }

  private boolean stopping() {
    if (ownerStopping()) {
      cancellation.terminateChildren();
      return true;
    }
    return solverBudget.isTerminated() || root != null && root.budget.isTerminated();
  }

  /** Only thread-safe cancellation signals; owner callbacks never read coordinator work budgets. */
  private boolean ownerStopping() {
    return cancellation.isSolverTerminated(outerScope)
        || outerScope.getSolver().isTerminateEarly()
        || !outerScope.getSolver().isEveryProblemChangeProcessed();
  }

  private void abortStartup() {
    startupAborted = true;
    discardUncommittedBest = true;
    cancellation.terminateChildren();
  }

  /** Wake interruptible user work while owner queues remain available for orderly cleanup. */
  private void interruptOwners() {
    if (!ownersInterrupted.compareAndSet(false, true)) return;
    for (var thread : List.copyOf(ownerThreads)) {
      if (thread != Thread.currentThread()) thread.interrupt();
    }
  }

  private <T> T await(Future<T> future) {
    while (true) {
      try {
        return future.get(25L, TimeUnit.MILLISECONDS);
      } catch (TimeoutException ignored) {
        if (closing && cleanupDeadlineNanos != 0L && System.nanoTime() >= cleanupDeadlineNanos) {
          for (var owner : owners) owner.shutdownNow();
          throw new IllegalStateException(
              "Resumable owner cleanup did not reach quiescence before its deadline.");
        }
        if (!closing && ownerFailure.get() != null) throw propagate(ownerFailure.get());
        if (!closing) {
          // Only clocks can advance these immutable committed expressions during a running round.
          for (var member : sessions)
            if (member.memberBudget != null && member.memberBudget.isTerminated()) {
              for (var descendant : sessions)
                if (descendant.isDescendantOf(member)) descendant.stop.terminateChildren();
            }
        }
        if (!closing && stopping()) {
          cancellation.terminateChildren();
          if (inRound) discardUncommittedBest = true;
        }
      } catch (InterruptedException e) {
        restoreInterrupt = true;
        cancellation.terminateChildren();
        if (closing) continue;
        interruptOwners();
        throw new IllegalStateException("Interrupted waiting for a resumable owner.", e);
      } catch (ExecutionException e) {
        // A peer interrupted to stop the run may finish before the original failing owner.
        var cause = !closing && ownerFailure.get() != null ? ownerFailure.get() : e.getCause();
        if (cause instanceof Error error) throw error;
        if (cause instanceof RuntimeException exception) throw exception;
        throw new IllegalStateException("Resumable owner failed.", cause);
      }
    }
  }

  private <T> T onOwner(int owner, Callable<T> action) {
    if (owner >= 0) return await(owners[owner].submit(action));
    try {
      return action.call();
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Settle phase handoffs and container starts in stable logical order, never worker arrival order.
   */
  private void settle() {
    boolean changed;
    do {
      changed = false;
      for (var session : List.copyOf(sessions)) {
        if (session.closed || session.container != null || session.execution != null) continue;
        if (stopping()) {
          abortStartup();
          return;
        }
        if (session.phaseCursor == session.configs.size()
            || onOwner(
                session.owner, () -> session.termination.isSolverTerminated(session.scope))) {
          var summary = onOwner(session.owner, () -> session.finish(false, null));
          commit(session, summary);
          changed = true;
        } else {
          if (!onOwner(session.owner, session::prepareNextPhase)) {
            abortStartup();
            return;
          }
          // Restoring the best may itself score. Recheck child solver-wide limits before the
          // next phase is admitted, without resetting its independent phase-local termination.
          if (onOwner(session.owner, () -> session.termination.isSolverTerminated(session.scope))) {
            commit(session, onOwner(session.owner, () -> session.finish(false, null)));
            changed = true;
            continue;
          }
          var config = session.configs.get(session.phaseCursor);
          if (config instanceof IslandModelPhaseConfig
              || config instanceof PartitionedSearchPhaseConfig) {
            session.container =
                new Container(
                    session,
                    session.scope,
                    session.owner,
                    config,
                    session.phases.get(session.phaseCursor),
                    session.phaseCursor,
                    session.path
                        + "/episode-"
                        + session.episode
                        + "/phase-"
                        + index(session.phaseCursor));
            openContainer(session.container);
          } else {
            if (config instanceof LocalSearchPhaseConfig) {
              for (var ancestor = session.parent;
                  ancestor != null;
                  ancestor = ancestor.parentContainer()) {
                if (ancestor.searchStartMillis == null)
                  ancestor.searchStartMillis = outerScope.getClock().millis();
              }
            }
            var initialized =
                onOwner(
                    session.owner,
                    () -> {
                      if (ownerStopping()) return session.snapshot();
                      var phase = session.phases.get(session.phaseCursor);
                      if (phase instanceof DefaultLocalSearchPhase<Solution_> local) {
                        session.execution = local.startExecution(session.scope);
                      } else if (phase
                          instanceof DefaultConstructionHeuristicPhase<Solution_> construction) {
                        session.execution = construction.startExecution(session.scope);
                      } else
                        throw new IllegalArgumentException(
                            "Unsupported resident phase: " + phase.getClass().getName());
                      return session.snapshot();
                    });
            commit(session, initialized);
          }
          if (stopping()) {
            abortStartup();
            return;
          }
          changed = true;
        }
      }
      reduceContainers(false);
      // A completed container can make its owning session's next phase ready during reduction.
      changed |=
          sessions.stream().anyMatch(s -> !s.closed && s.container == null && s.execution == null);
    } while (changed && !stopping());
  }

  private void openContainer(Container container) {
    containers.add(container);
    if (!onOwner(container.owner, container::open)) {
      container.inputs = List.of();
      abortStartup();
      return;
    }
    var inputs = container.inputs;
    // Inputs are only needed for startup; retaining them would keep an extra solution per session.
    container.inputs = List.of();
    LOGGER.info(
        "Resumable hierarchy node ({}) has logical width ({}), workers ({}), quantum ({}), cooperation ({}).",
        container.path,
        inputs.size(),
        owners.length,
        quantum,
        container.config instanceof IslandModelPhaseConfig island
                && island.getResumableSearchConfig() != null
            ? Objects.requireNonNullElse(
                island.getResumableSearchConfig().getCooperationPolicy(),
                ResumableSearchCooperationPolicy.NONE)
            : "LEGACY");
    container.budget.rebaseArchive(container.bestScore);
    if (container.budget.isTerminated() || stopping()) return;
    var childConfigs = childConfigs(container.config);
    for (int i = 0; i < inputs.size(); i++) {
      if (stopping()
          || container.budget.isTerminated()
          || container.partitionBudget != null
              && container.partitionBudget.isDefinitelyTerminated()) {
        if (stopping()) abortStartup();
        break;
      }
      var path = container.path + (container.partition ? "/part-" : "/island-") + index(i);
      int owner = (int) (nextSessionOrdinal % owners.length);
      nextSessionOrdinal = Math.incrementExact(nextSessionOrdinal);
      createSession(container, path, i, 0L, owner, inputs.get(i), childConfigs);
      if (stopping()) {
        abortStartup();
        break;
      }
    }
  }

  private Session createSession(
      Container parent,
      String path,
      int childIndex,
      long episode,
      int owner,
      Solution_ input,
      List<PhaseConfig> configs) {
    var scope =
        onOwner(
            parent.owner,
            () -> parent.scope.createChildThreadSolverScope(ChildThreadType.PART_THREAD, false));
    var session = new Session(parent, path, childIndex, episode, owner, scope, configs);
    sessions.add(session);
    parent.children.add(session);
    outerScope.getIslandWorkAccounting().register(session.source());
    try {
      var summary = onOwner(owner, () -> session.initialize(input));
      commit(session, summary);
    } catch (RuntimeException | Error failure) {
      try {
        onOwner(
            owner,
            () -> {
              session.finish(true, failure);
              return null;
            });
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    return session;
  }

  private void commit(Session session, SessionSummary<Solution_> summary) {
    if (discardUncommittedBest && session.summary != null) {
      var retained = session.summary;
      summary =
          new SessionSummary<>(
              summary.path(),
              summary.episode(),
              summary.completedSteps(),
              summary.moveEvaluations(),
              summary.scoreCalculations(),
              retained.bestScore(),
              retained.bestSolution(),
              summary.steps(),
              summary.finished(),
              summary.physicalScoreCalculations(),
              summary.scoreCalculationCategories(),
              summary.completedSearchSteps(),
              summary.moveCountsByType());
    }
    session.summary = summary;
    var source = session.source();
    solverBudget.recordSource(
        source, summary.completedSteps(), summary.moveEvaluations(), summary.scoreCalculations());
    for (var member = session; member != null; member = member.parent.session) {
      if (member.memberBudget != null) {
        member.memberBudget.recordSource(
            source,
            summary.completedSteps(),
            summary.moveEvaluations(),
            summary.scoreCalculations());
        member.memberBudget.recordSearchStepSource(source, summary.completedSearchSteps());
      }
    }
    for (var parent = session.parent; parent != null; parent = parent.parentContainer()) {
      parent.budget.recordSource(
          source, summary.completedSteps(), summary.moveEvaluations(), summary.scoreCalculations());
    }
    outerScope
        .getIslandWorkAccounting()
        .publish(
            source,
            new SolverWorkSnapshot(
                summary.scoreCalculations(),
                summary.moveEvaluations(),
                summary.moveCountsByType()));
    physicalSources.put(
        source,
        new ResumableSearchWorkSnapshot(
            summary.physicalScoreCalculations(), summary.scoreCalculationCategories()));
    if (summary.finished()) {
      solverBudget.retireSource(source);
      for (var member = session; member != null; member = member.parent.session) {
        if (member.memberBudget != null) member.memberBudget.retireSource(source);
      }
      for (var parent = session.parent; parent != null; parent = parent.parentContainer())
        parent.budget.retireSource(source);
      outerScope.getIslandWorkAccounting().retire(source);
      retiredPhysicalWork = retiredPhysicalWork.plus(physicalSources.remove(source));
      sessions.remove(session);
    }
  }

  private void reduceContainers(boolean allowCooperation) {
    if (startupAborted || discardUncommittedBest) {
      // Newly initialized sessions have no previous summary to restore. Keep the last archive,
      // while final summaries still account for initialization and cleanup work.
      recordRootWork();
      return;
    }
    var ordered =
        containers.stream()
            .filter(c -> !c.complete)
            .sorted(
                Comparator.comparingInt((Container c) -> c.path.length())
                    .reversed()
                    .thenComparing(c -> c.path))
            .toList();
    for (var container : ordered) {
      if (startupAborted) break;
      onOwner(
          container.owner,
          () -> {
            try (var ignored =
                (container.session == null ? rootWork : container.session.work)
                    .scope(ResumableSearchWorkCategory.RESTORATION)) {
              container.reduce();
              if (container.partitionBudget != null) container.partitionBudget.refresh();
              return null;
            }
          });
      recordContainerWork(container);
      container.budget.bestScoreImproved(container.bestScore, outerScope.getClock().millis());
      if (container.session != null && !container.session.closed) {
        commit(container.session, onOwner(container.owner, () -> container.session.snapshot()));
      }
      if (container == root) publishRoot();
      for (var child : List.copyOf(container.children)) {
        if (child.memberBudget == null || child.closed) continue;
        boolean searchActive =
            sessions.stream()
                .anyMatch(
                    s ->
                        !s.closed
                            && s.isDescendantOf(child)
                            && s.phaseCursor < s.configs.size()
                            && s.configs.get(s.phaseCursor) instanceof LocalSearchPhaseConfig);
        child.memberBudget.setIdleApplicable(searchActive);
        child.memberBudget.bestScoreImproved(
            child.summary.bestScore(), outerScope.getClock().millis());
        child.memberBudget.setPredicateArchive(container.bestScore);
        if (container.legacy != null && container.searchStartMillis != null) {
          child.memberBudget.setUnimprovedTimeOrigin(
              Math.max(container.lastImprovementMillis, container.searchStartMillis));
        }
        if (child.memberBudget.isTerminated()) finishTree(child, false, null);
      }
      if (container.budget.isTerminated()
          || container.partitionBudget != null
              && container.partitionBudget.isDefinitelyTerminated()) {
        for (var child : List.copyOf(container.children)) finishTree(child, false, null);
      }
      if (container.children.stream().allMatch(s -> s.closed)) closeContainer(container);
      else if (allowCooperation || container.legacy != null) cooperate(container);
    }
    recordRootWork();
  }

  private void recordRootWork() {
    var source = root.path + "/coordinator/revision-" + revision;
    long moves = outerScope.getMoveEvaluationCount() - initialRootMoves;
    long scores = outerScope.getScoreCalculationCount() - initialRootCalculations;
    solverBudget.refreshOrdinaryWork(outerScope);
    root.budget.recordSource(source, 0L, moves, scores);
  }

  private void recordContainerWork(Container container) {
    if (container == root) {
      recordRootWork();
      return;
    }
    var work =
        onOwner(
            container.owner,
            () ->
                new long[] {
                  container.scope.getMoveEvaluationCount() - container.initialMoves,
                  container.scope.getScoreCalculationCount() - container.initialCalculations
                });
    container.budget.recordSource(
        container.path + "/coordinator/revision-" + revision, 0L, work[0], work[1]);
  }

  private void publishRoot() {
    if (root.bestScore == null || root.bestScore.isStructurallyFlawed()) return;
    solverBudget.bestScoreImproved(root.bestScore, outerScope.getClock().millis());
    if (compare(root.bestScore, outerScope.getBestScore()) <= 0) return;
    boolean wasInitialized = outerScope.isBestSolutionInitialized();
    outerScope.setBestSolution(outerScope.getScoreDirector().cloneSolution(root.bestSolution));
    setBestScore(outerScope, root.bestScore);
    outerScope.setBestSolutionTimeMillis(outerScope.getClock().millis());
    if (root.bestScore.isFullyAssigned()) {
      if (!wasInitialized) outerScope.setStartingInitializedScore(root.bestScore.raw());
      outerScope
          .getSolver()
          .getSolverEventSupport()
          .fireBestSolutionChanged(
              outerScope,
              new ResumableSearchEventProducerId(
                  phaseIndex,
                  revision,
                  round,
                  root.bestPath == null ? root.path : root.bestPath,
                  root.bestEpisode),
              outerScope.getBestSolution());
    }
  }

  private void cooperate(Container container) {
    if (!(container.config instanceof IslandModelPhaseConfig island)) return;
    var config = island.getResumableSearchConfig();
    if (config == null) {
      // Legacy migration keeps the declared frequency and uses notified adoption with state reset.
      container.relayLegacyMigrations();
      return;
    }
    if (config.getCooperationPolicy() != ResumableSearchCooperationPolicy.RESTART_WEAKEST
        || stopping()
        || container.lastCooperationRound == completedRounds) return;
    container.lastCooperationRound = completedRounds;
    Session weakest = null;
    for (var child : container.children) {
      if (child.childIndex == 0
          || child.closed
          || child.summary == null
          || completedRounds - child.lastImprovementRound < config.getStagnationRounds()
          || compare(container.bestScore, child.summary.bestScore()) <= 0) continue;
      if (weakest == null
          || compare(child.summary.bestScore(), weakest.summary.bestScore()) < 0
          || compare(child.summary.bestScore(), weakest.summary.bestScore()) == 0
              && child.childIndex < weakest.childIndex) weakest = child;
    }
    if (weakest == null) return;
    var selected = weakest;
    finishTree(selected, false, null);
    container.children.remove(selected);
    if (stopping() || container.budget.isTerminated()) return;
    var input =
        onOwner(
            container.owner,
            () -> container.scope.getScoreDirector().cloneSolution(container.bestSolution));
    if (stopping()) {
      abortStartup();
      return;
    }
    createSession(
        container,
        selected.path,
        selected.childIndex,
        selected.episode + 1L,
        selected.owner,
        input,
        selected.configs);
    container.children.sort(Comparator.comparingInt(s -> s.childIndex));
    if (stopping()) abortStartup();
  }

  private void closeContainer(Container container) {
    if (container.complete) return;
    onOwner(
        container.owner,
        () -> {
          container.finish();
          return null;
        });
    recordContainerWork(container);
    container.complete = true;
    containers.remove(container);
    if (container.session != null) {
      container.session.container = null;
      onOwner(
          container.owner,
          () -> {
            container.session.nextPhase();
            return null;
          });
      commit(container.session, onOwner(container.owner, () -> container.session.snapshot()));
      // The enclosing solve budgets already retain all consumed work. Episode-local definitions
      // must not retain the closed episode graph or reset the enclosing consumption when replaced.
      container.budget.retireScope();
      for (var child : container.children)
        if (child.memberBudget != null) child.memberBudget.retireScope();
    }
    if (container == root) recordRootWork();
  }

  private void finishTree(Session session, boolean error, Throwable failure) {
    if (session.closed) {
      if (sessions.contains(session))
        commit(session, onOwner(session.owner, () -> session.finish(error, failure)));
      return;
    }
    if (session.container != null) {
      for (var child : List.copyOf(session.container.children)) finishTree(child, error, failure);
      if (!error) closeContainer(session.container);
    }
    commit(session, onOwner(session.owner, () -> session.finish(error, failure)));
  }

  private void finishAll(boolean error, Throwable failure) {
    closing = true;
    cleanupDeadlineNanos =
        System.nanoTime() + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
    for (var session : List.copyOf(sessions)) {
      try {
        finishTree(session, error, failure);
      } catch (RuntimeException | Error cleanup) {
        if (failure == null) throw cleanup;
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(InnerScore<?> left, InnerScore<?> right) {
    if (left == null) return right == null ? 0 : -1;
    if (right == null) return 1;
    return ((InnerScore) left).compareTo((InnerScore) right);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void setBestScore(SolverScope<?> scope, InnerScore<?> score) {
    ((SolverScope) scope).setBestScore((InnerScore) score);
  }

  private static String index(int value) {
    return "%08d".formatted(value);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<PhaseConfig> childConfigs(PhaseConfig<?> config) {
    List<? extends PhaseConfig> declared;
    if (config instanceof IslandModelPhaseConfig island) {
      declared = island.getPhaseConfigList();
      if (declared == null || declared.isEmpty()) {
        var local = new LocalSearchPhaseConfig();
        local.setLocalSearchType(island.getLocalSearchType());
        local.setMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE);
        if (island.getMoveSelectorConfig() != null)
          local.setMoveSelectorConfig(
              (greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig)
                  island.getMoveSelectorConfig().copyConfig());
        if (island.getAcceptorConfig() != null)
          local.setAcceptorConfig(island.getAcceptorConfig().copyConfig());
        if (island.getForagerConfig() != null)
          local.setForagerConfig(island.getForagerConfig().copyConfig());
        if (island.getGuidedLocalSearchConfig() != null)
          local.setGuidedLocalSearchConfig(island.getGuidedLocalSearchConfig().copyConfig());
        declared = List.of(local);
      }
    } else {
      declared = ((PartitionedSearchPhaseConfig) config).getPhaseConfigList();
      if (declared == null || declared.isEmpty())
        declared = List.of(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
    }
    return declared.stream().map(c -> (PhaseConfig) c.copyConfig()).toList();
  }

  private final class Grant {
    final Session session;
    final long steps;
    final Future<SessionSummary<Solution_>> future;

    Grant(Session session, long steps, Future<SessionSummary<Solution_>> future) {
      this.session = session;
      this.steps = steps;
      this.future = future;
    }
  }

  private final class Session {
    final Container parent;
    final String path;
    final int childIndex;
    final long episode;
    final int owner;
    final SolverScope<Solution_> scope;
    final List<PhaseConfig> configs;
    final ResumableSearchWorkCounter work = new ResumableSearchWorkCounter();
    final SolverMetricRun metricRun = new SolverMetricRun();
    final ChildThreadPlumbingTermination<Solution_> stop = new ChildThreadPlumbingTermination<>();
    final ResumableSearchBudget<Solution_> memberBudget;
    final List<StepTrace<Solution_>> trace = new ArrayList<>();
    List<Phase<Solution_>> phases;
    ResidentSearchSolver<Solution_> solver;
    UniversalTermination<Solution_> termination;
    final List<UniversalTermination<Solution_>> partitionTerminations = new ArrayList<>();
    ResumablePhaseExecution<Solution_> execution;
    Container container;
    SessionSummary<Solution_> summary;
    SessionSummary<Solution_> terminalSummary;
    int phaseCursor;
    long completedSteps;
    long lastImprovementRound;
    InnerScore<?> lastSummaryScore;
    boolean closed;
    boolean started;
    boolean restoreBeforeNextPhase;
    long legacySteps;
    long completedSearchSteps;

    Session(
        Container parent,
        String path,
        int childIndex,
        long episode,
        int owner,
        SolverScope<Solution_> scope,
        List<PhaseConfig> configs) {
      this.parent = parent;
      this.path = path;
      this.childIndex = childIndex;
      this.episode = episode;
      this.owner = owner;
      this.scope = scope;
      this.configs = configs;
      lastImprovementRound = completedRounds;
      memberBudget =
          parent.cooperative || parent.partition
              ? null
              : new ResumableSearchBudget<>(
                  path + "/member-budget",
                  parent.config.getTerminationConfig(),
                  policy,
                  outerScope.getResumableSearchState(),
                  outerScope.getClock(),
                  parent.startMillis);
    }

    String source() {
      return path + "/revision-" + revision + "/episode-" + episode;
    }

    SessionSummary<Solution_> initialize(Solution_ input) {
      long seed =
          childIndex == 0 && episode == 0 && parent == root && revision == 0
              ? rootSeed
              : mix(rootSeed ^ pathSeed(path) ^ mix(episode) ^ mix(revision));
      var random = DefaultRandomSource.seeded(seed);
      scope.setWorkingRandom(random);
      scope.setWorkerPath(source() + "/");
      scope.setMetricSource(source());
      scope.setMetricRun(metricRun);
      scope.setStepMetricPublicationEnabled(false);
      scope.setMonitoringTags(scope.getMonitoringTags().and("resumable.session", source()));
      scope.setMetricSamplePublisher(ignored -> {});
      var mode = parent.phase.getEnvironmentMode();
      var director =
          scoreDirectorFactory
              .createScoreDirectorBuilder(mode)
              .withLookUpEnabled(true)
              .withConstraintMatchPolicy(
                  ScoreDirectorFactoryFactory.decideConstraintMatchPolicy(scope, mode))
              .buildDerived();
      director.setResumableSearchWorkCounter(work);
      scope.setScoreDirector(director);
      scope.setProblemChangeDirector(new DefaultProblemChangeDirector<>(director));
      var sessionPolicy =
          policy
              .copyChildThreadConfigPolicy()
              .copyConfigPolicy(mode)
              .cloneBuilder()
              .withRandom(random)
              .withClassInstanceCache(ClassInstanceCache.create())
              .withMoveThreadCount(null)
              .build();
      var recaller =
          BestSolutionRecallerFactory.create().<Solution_>buildBestSolutionRecaller(mode);
      termination = UniversalTermination.or(cancellation, stop);
      if (parent.session != null) {
        for (var inherited : parent.session.partitionTerminations) {
          var child =
              UniversalTermination.or(
                  ChildThreadSupportingTermination
                      .<Solution_, SolverScope<Solution_>>assertChildThreadSupport(inherited)
                      .createChildThreadTermination(scope, ChildThreadType.PART_THREAD));
          partitionTerminations.add(child);
          termination = UniversalTermination.or(termination, child);
        }
      }
      if (parent.partitionBudget != null) {
        var child = parent.partitionBudget.createChildTermination(scope);
        partitionTerminations.add(child);
        termination = UniversalTermination.or(termination, child);
      }
      phases = PhaseFactory.buildPhases(configs, sessionPolicy, recaller, termination);
      solver =
          new ResidentSearchSolver<>(mode, scoreDirectorFactory, recaller, termination, phases);
      scope.setSolver(solver);
      if (parent.legacy != null) {
        for (var phase : phases)
          phase.addPhaseLifecycleListener(
              new PhaseLifecycleListenerAdapter<>() {
                @Override
                public void stepEnded(AbstractStepScope<Solution_> step) {
                  publishLegacyBest();
                  if (!(step instanceof LocalSearchStepScope)) return;
                  legacySteps++;
                  var island = (IslandModelPhaseConfig) parent.config;
                  int migrationFrequency =
                      Objects.requireNonNullElse(
                          island.getMigrationFrequency(),
                          IslandModelPhaseConfig.DEFAULT_MIGRATION_FREQUENCY);
                  if (legacySteps % migrationFrequency == 0) {
                    parent
                        .legacy
                        .mailboxes
                        .get((childIndex + 1) % parent.legacy.mailboxes.size())
                        .set(new LegacyBest<>(scope.getBestSolution(), scope.getBestScore()));
                    adopt(parent.legacy.mailboxes.get(childIndex).getAndSet(null));
                  }
                  boolean compareGlobal =
                      Objects.requireNonNullElse(island.getCompareGlobalEnabled(), true);
                  int receiveFrequency =
                      Objects.requireNonNullElse(
                          island.getReceiveGlobalUpdateFrequency(),
                          Objects.requireNonNullElse(
                              island.getCompareGlobalFrequency(),
                              IslandModelPhaseConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY));
                  if (compareGlobal && legacySteps % receiveFrequency == 0)
                    adopt(parent.legacy.best.get());
                }

                @Override
                public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
                  publishLegacyBest();
                }
              });
      }
      if (traceSteps)
        solver.addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<Solution_> step) {
                String description =
                    step instanceof LocalSearchStepScope<Solution_> local && local.getStep() != null
                        ? local.getStep().describe()
                        : "construction";
                trace.add(
                    new StepTrace<>(
                        step.getPhaseScope().getPhaseIndex(),
                        step.getStepIndex(),
                        description,
                        step.getScore(),
                        scope.getScoreDirector().cloneWorkingSolution()));
              }
            });
      scope.setInitialSolution(input);
      started = true;
      solver.solvingStarted(scope);
      return snapshot();
    }

    SessionSummary<Solution_> advance(long grant) {
      try (var ignored = work.scope(ResumableSearchWorkCategory.SEARCH)) {
        var activeScope = execution.getPhaseScope();
        long before = activeScope.getNextStepIndex();
        try {
          execution.advance(grant);
        } finally {
          long completed = activeScope.getNextStepIndex() - before;
          completedSteps += completed;
          if (activeScope
              instanceof greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope)
            completedSearchSteps += completed;
        }
        if (execution.isComplete()) {
          execution.finish();
          execution = null;
          nextPhase();
        }
        return snapshot();
      } catch (RuntimeException | Error failure) {
        ownerFailure.compareAndSet(null, failure);
        cancellation.terminateChildren();
        interruptOwners();
        try {
          finish(true, failure);
        } catch (RuntimeException | Error cleanup) {
          if (cleanup != failure) failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    void nextPhase() {
      phaseCursor++;
      restoreBeforeNextPhase = phaseCursor < phases.size();
    }

    boolean prepareNextPhase() {
      if (ownerStopping()) return false;
      if (restoreBeforeNextPhase) {
        try (var ignored = work.scope(ResumableSearchWorkCategory.RESTORATION)) {
          solver.restoreWorkingForNextPhase(scope);
        }
        restoreBeforeNextPhase = false;
      }
      return !ownerStopping();
    }

    SessionSummary<Solution_> snapshot() {
      var best = scope.getBestSolution();
      if (best == scope.getWorkingSolution()) best = scope.getScoreDirector().cloneSolution(best);
      var score = scope.getBestScore();
      if (compare(score, lastSummaryScore) > 0) {
        lastSummaryScore = score;
        lastImprovementRound = inRound ? completedRounds + 1L : completedRounds;
      }
      var snapshot =
          new SessionSummary<>(
              path,
              episode,
              completedSteps,
              scope.getMoveEvaluationCount(),
              scope.getScoreCalculationCount(),
              score,
              best,
              trace,
              closed,
              work.snapshot().totalScoreCalculationCount(),
              work.snapshot().scoreCalculationCountsByCategory(),
              completedSearchSteps,
              scope.getMoveEvaluationCountPerType());
      trace.clear();
      return snapshot;
    }

    SessionSummary<Solution_> finish(boolean error, Throwable failure) {
      if (closed) return terminalSummary;
      try (var ignored = work.scope(ResumableSearchWorkCategory.FINALIZATION)) {
        if (error) {
          if (solver != null) solver.solvingError(scope, failure);
          else if (scope.getScoreDirector() != null) scope.getScoreDirector().close();
        } else {
          if (execution != null) {
            execution.finish();
            execution = null;
          }
          if (started) solver.solvingEnded(scope);
          else if (scope.getScoreDirector() != null) scope.getScoreDirector().close();
        }
      } catch (RuntimeException | Error cleanupFailure) {
        if (!error && solver != null) {
          try {
            solver.solvingError(scope, cleanupFailure);
          } catch (RuntimeException | Error additional) {
            if (additional != cleanupFailure) cleanupFailure.addSuppressed(additional);
          }
        }
        throw cleanupFailure;
      } finally {
        closed = true;
        // Even a failing final callback must publish its final counters and retain the original
        // error.
        terminalSummary =
            new SessionSummary<>(
                path,
                episode,
                completedSteps,
                scope.getMoveEvaluationCount(),
                scope.getScoreDirector() == null ? 0L : scope.getScoreCalculationCount(),
                scope.getBestScore(),
                scope.getBestSolution(),
                trace,
                true,
                work.snapshot().totalScoreCalculationCount(),
                work.snapshot().scoreCalculationCountsByCategory(),
                completedSearchSteps,
                scope.getMoveEvaluationCountPerType());
        trace.clear();
      }
      return terminalSummary;
    }

    boolean isDescendantOf(Container ancestor) {
      for (var current = parent; current != null; current = current.parentContainer())
        if (current == ancestor) return true;
      return false;
    }

    boolean isDescendantOf(Session ancestor) {
      for (var current = this; current != null; current = current.parent.session)
        if (current == ancestor) return true;
      return false;
    }

    void publishLegacyBest() {
      if (parent.legacy == null
          || scope.getBestScore() == null
          || scope.getBestScore().isStructurallyFlawed()) return;
      var solution = scope.getBestSolution();
      if (solution == scope.getWorkingSolution())
        solution = scope.getScoreDirector().cloneSolution(solution);
      var candidate = new LegacyBest<>(solution, scope.getBestScore());
      parent.legacy.best.accumulateAndGet(
          candidate,
          (previous, next) ->
              previous == null || compare(next.score(), previous.score()) > 0 ? next : previous);
    }

    void adopt(LegacyBest<Solution_> donor) {
      if (donor != null
          && !donor.score().isStructurallyFlawed()
          && compare(donor.score(), scope.getBestScore()) > 0) {
        scope.setPendingMoveIfBetter(
            SolutionSyncMove.createMove(scope.getScoreDirector(), donor.solution()),
            donor.score(),
            true);
      }
    }
  }

  private final class Container {
    final Session session;
    final SolverScope<Solution_> scope;
    final int owner;
    final PhaseConfig<?> config;
    final Phase<Solution_> phase;
    final int localPhaseIndex;
    final String path;
    final boolean partition;
    final boolean cooperative;
    final long startMillis;
    long lastImprovementMillis;
    Long searchStartMillis;
    final List<Session> children = new ArrayList<>();
    final ResumableSearchBudget<Solution_> budget;
    AbstractPhaseScope<Solution_> phaseScope;
    PartitionTerminationBudget<Solution_> partitionBudget;
    List<Solution_> inputs;
    PartitionOwnership<Solution_> ownership;
    InnerScore<?> bestScore;
    Solution_ bestSolution;
    String bestPath;
    long bestEpisode;
    long lastCooperationRound = -1;
    boolean complete;
    boolean started;
    long initialMoves;
    long initialCalculations;
    final Map<String, InnerScore<?>> lastPartitionScores = new java.util.HashMap<>();
    LegacyState<Solution_> legacy;

    Container(
        Session session,
        SolverScope<Solution_> scope,
        int owner,
        PhaseConfig<?> config,
        Phase<Solution_> phase,
        int localPhaseIndex,
        String path) {
      this.session = session;
      this.scope = scope;
      this.owner = owner;
      this.config = config;
      this.phase = phase;
      this.localPhaseIndex = localPhaseIndex;
      this.path = path;
      partition = config instanceof PartitionedSearchPhaseConfig;
      cooperative =
          config instanceof IslandModelPhaseConfig island
              && island.getResumableSearchConfig() != null;
      startMillis = outerScope.getClock().millis();
      lastImprovementMillis = startMillis;
      budget =
          new ResumableSearchBudget<>(
              path,
              cooperative
                  ? Objects.requireNonNullElseGet(
                      config.getTerminationConfig(), TerminationConfig::new)
                  : new TerminationConfig(),
              policy,
              outerScope.getResumableSearchState(),
              outerScope.getClock(),
              startMillis);
    }

    Container parentContainer() {
      return session == null ? null : session.parent;
    }

    @SuppressWarnings("unchecked")
    boolean open() {
      initialMoves = scope.getMoveEvaluationCount();
      initialCalculations = scope.getScoreCalculationCount();
      bestScore = scope.getBestScore();
      bestSolution = scope.getBestSolution();
      if (startupStopping()) return false;
      phaseScope =
          partition
              ? new PartitionedSearchPhaseScope<>(scope, localPhaseIndex)
              : new IslandModelPhaseScope<>(scope, localPhaseIndex);
      scope.getSolver().prepareForPhase(phaseScope);
      bestScore = scope.getBestScore();
      bestSolution = scope.getBestSolution();
      if (startupStopping()) return false;
      if (partition) {
        var partitionConfig = (PartitionedSearchPhaseConfig) config;
        var partitioner =
            (SolutionPartitioner<Solution_>)
                ConfigUtils.newInstance(
                    partitionConfig,
                    "solutionPartitionerClass",
                    partitionConfig.getSolutionPartitionerClass());
        if (startupStopping()) return false;
        ConfigUtils.applyCustomProperties(
            partitioner,
            "solutionPartitionerClass",
            partitionConfig.getSolutionPartitionerCustomProperties(),
            "solutionPartitionerCustomProperties");
        if (startupStopping()) return false;
        inputs =
            Objects.requireNonNull(
                partitioner.splitWorkingSolution(
                    scope.getScoreDirector(), partitionLimit(partitionConfig)));
        if (startupStopping()) return false;
        ownership = PartitionOwnership.validate(scope.getScoreDirector(), inputs);
        ((PartitionedSearchPhaseScope<Solution_>) phaseScope).setPartCount(inputs.size());
      } else {
        var island = (IslandModelPhaseConfig) config;
        int count =
            Objects.requireNonNullElse(
                island.getIslandCount(), IslandModelPhaseConfig.DEFAULT_ISLAND_COUNT);
        if (island.getResumableSearchConfig() == null)
          legacy = new LegacyState<>(count, new LegacyBest<>(bestSolution, bestScore));
        inputs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
          if (startupStopping()) return false;
          inputs.add(scope.getScoreDirector().cloneSolution(bestSolution));
        }
      }
      if (startupStopping()) return false;
      phase.phaseStarted(phaseScope);
      started = true;
      if (startupStopping()) return false;
      if (partition && config.getTerminationConfig() != null) {
        var declaration =
            TerminationFactory.<Solution_>create(config.getTerminationConfig())
                .buildTermination(policy);
        if (declaration != null) {
          partitionBudget =
              PartitionTerminationBudget.forResumableSearch(
                  UniversalTermination.or(declaration), phaseScope);
        }
      }
      return true;
    }

    private boolean startupStopping() {
      return owner < 0 ? stopping() : ownerStopping();
    }

    void reduce() {
      var previousBestScore = bestScore;
      for (var child :
          children.stream().sorted(Comparator.comparingInt(s -> s.childIndex)).toList()) {
        var candidate = child.summary;
        if (candidate == null
            || candidate.bestScore() == null
            || candidate.bestScore().isStructurallyFlawed()) continue;
        if (partition) {
          // Native partition publication only exports initialized child solutions. Construction
          // may retain a valid partial best, but it has not reached that publication boundary yet.
          if (!candidate.bestScore().isFullyAssigned()) continue;
          if (compare(candidate.bestScore(), lastPartitionScores.get(child.path)) <= 0) continue;
          var move =
              PartitionChangeMove.createMove(
                      scope.getScoreDirector(), candidate.bestSolution(), child.childIndex)
                  .rebase(scope.getScoreDirector());
          ownership.validateMove(move, scope.getScoreDirector());
          var step =
              new PartitionedSearchStepScope<>((PartitionedSearchPhaseScope<Solution_>) phaseScope);
          phase.stepStarted(step);
          step.setStep(move);
          scope.getScoreDirector().executeMove(move);
          step.setScore(scope.calculateScore());
          if (session == null) {
            scope
                .getSolver()
                .getBestSolutionRecaller()
                .processWorkingSolutionDuringStep(
                    step,
                    new ResumableSearchEventProducerId(
                        phaseIndex, revision, round, candidate.path(), candidate.episode()));
          } else {
            scope.getSolver().getBestSolutionRecaller().processWorkingSolutionDuringStep(step);
          }
          phase.stepEnded(step);
          ((PartitionedSearchPhaseScope<Solution_>) phaseScope).setLastCompletedStepScope(step);
          lastPartitionScores.put(child.path, candidate.bestScore());
          bestScore = scope.getBestScore();
          bestSolution = scope.getBestSolution();
        } else if (compare(candidate.bestScore(), bestScore) > 0) {
          bestScore = candidate.bestScore();
          bestSolution = candidate.bestSolution();
          bestPath = child.path;
          bestEpisode = child.episode;
        }
      }
      if (!partition && session != null && compare(bestScore, scope.getBestScore()) > 0) {
        boolean wasInitialized = scope.isBestSolutionInitialized();
        scope.setBestSolution(scope.getScoreDirector().cloneSolution(bestSolution));
        setBestScore(scope, bestScore);
        if (!wasInitialized && bestScore.isFullyAssigned()) {
          scope.setStartingInitializedScore(bestScore.raw());
        }
        scope.setBestSolutionTimeMillis(scope.getClock().millis());
        session.publishLegacyBest();
      }
      if (compare(bestScore, previousBestScore) > 0)
        lastImprovementMillis = scope.getClock().millis();
    }

    void relayLegacyMigrations() {
      if (legacy == null) return;
      for (var child : children)
        if (child.closed) {
          var incoming = legacy.mailboxes.get(child.childIndex).getAndSet(null);
          if (incoming != null)
            legacy.mailboxes.get((child.childIndex + 1) % legacy.mailboxes.size()).set(incoming);
        }
    }

    void finish() {
      if (!started) return;
      // Reinitialize the enclosing director and validate its native score before phase handoff.
      try (var ignored =
          (session == null ? rootWork : session.work)
              .scope(ResumableSearchWorkCategory.VERIFICATION)) {
        scope.setInitialSolution(bestSolution);
        var verified = scope.calculateScore();
        if (compare(verified, bestScore) != 0)
          throw new IllegalStateException("Resumable archive score mismatch at " + path);
        setBestScore(scope, verified);
      }
      try (var ignored =
          (session == null ? rootWork : session.work)
              .scope(ResumableSearchWorkCategory.FINALIZATION)) {
        phaseScope.endingNow();
        phase.phaseEnded(phaseScope);
      }
      started = false;
    }
  }

  private static Integer partitionLimit(PartitionedSearchPhaseConfig config) {
    var limit = config.getRunnablePartThreadLimit();
    if (limit == null || PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO.equals(limit)) {
      // This established partitioner input is independent of the resumable worker allocation.
      return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }
    if (PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED.equals(limit)) return null;
    return ConfigUtils.resolvePoolSize(
        "runnablePartThreadLimit",
        limit,
        PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_AUTO,
        PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
  }

  private record LegacyBest<Solution_>(Solution_ solution, InnerScore<?> score) {}

  private static final class LegacyState<Solution_> {
    final AtomicReference<LegacyBest<Solution_>> best;
    final List<AtomicReference<LegacyBest<Solution_>>> mailboxes;

    LegacyState(int count, LegacyBest<Solution_> baseline) {
      best = new AtomicReference<>(baseline);
      mailboxes = new ArrayList<>(count);
      for (int i = 0; i < count; i++) mailboxes.add(new AtomicReference<>());
    }
  }

  private static long mix(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }

  private static long pathSeed(String path) {
    long hash = 0xcbf29ce484222325L;
    for (int i = 0; i < path.length(); i++) hash = (hash ^ path.charAt(i)) * 0x100000001b3L;
    return hash;
  }

  private static RuntimeException propagate(Throwable failure) {
    if (failure instanceof Error error) throw error;
    return failure instanceof RuntimeException exception
        ? exception
        : new IllegalStateException(failure);
  }
}
