package greycos.solver.core.impl.partitionedsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.partitionedsearch.event.PartitionedSearchPhaseLifecycleListener;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.partitionedsearch.queue.PartitionQueue;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionOwnership;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.PhaseFactory;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.ChildThreadPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PartitionTerminationBudget;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.termination.UniversalTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.ThreadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default partitioned search phase implementation.
 *
 * <p>Splits problem using SolutionPartitioner, creates thread pool of PartitionSolver instances,
 * consumes improvements via PartitionQueue, and applies best solutions to main solution.
 *
 * <p>How: Partitions run in parallel threads; improvements queue to parent; parent applies latest
 * changes from each partition; terminates all threads before phase ends.
 *
 * <p>Why: Enables parallel solving for large problems; improves CPU utilization; reduces solving
 * time for partitionable cotwins.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public class DefaultPartitionedSearchPhase<Solution_> extends AbstractPhase<Solution_>
    implements PartitionedSearchPhase<Solution_>,
        PartitionedSearchPhaseLifecycleListener<Solution_> {

  protected final SolutionPartitioner<Solution_> solutionPartitioner;
  protected final ThreadFactory threadFactory;
  protected final Integer runnablePartThreadLimit;
  protected final List<PhaseConfig> phaseConfigList;
  protected final SolverTermination<Solution_> solverTermination;
  protected final HeuristicConfigPolicy<Solution_> configPolicy;

  private static final Logger logger = LoggerFactory.getLogger(DefaultPartitionedSearchPhase.class);

  private DefaultPartitionedSearchPhase(Builder<Solution_> builder) {
    super(builder);
    this.solutionPartitioner = builder.solutionPartitioner;
    this.threadFactory = builder.threadFactory;
    this.runnablePartThreadLimit = builder.runnablePartThreadLimit;
    this.phaseConfigList = builder.phaseConfigList;
    this.solverTermination = builder.solverTermination;
    this.configPolicy = builder.configPolicy;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.PARTITIONED_SEARCH;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::partitionedSearch;
  }

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    var phaseScope = new PartitionedSearchPhaseScope<>(solverScope, phaseIndex);
    // Partitioning uses the phase's director, while listeners receive the populated scope.
    solverScope.getSolver().prepareForPhase(phaseScope);
    List<Solution_> partList =
        Objects.requireNonNull(
            solutionPartitioner.splitWorkingSolution(
                phaseScope.getScoreDirector(), runnablePartThreadLimit),
            "The solutionPartitioner ("
                + solutionPartitioner
                + ") returned a null partition list.");
    var ownership = PartitionOwnership.validate(phaseScope.getScoreDirector(), partList);

    int partCount = partList.size();
    phaseScope.setPartCount(partCount);
    phaseStarted(phaseScope);
    if (partCount == 0) {
      logger.warn(
          "{}Partitioned Search phase ({}) produced 0 partitions. Skipping.",
          logIndentation,
          phaseIndex);
      phaseEnded(phaseScope);
      return;
    }

    logger.info(
        "{}Partitioned Search phase ({}) started: {} partitions.",
        logIndentation,
        phaseIndex,
        partCount);

    var terminationBudget = new PartitionTerminationBudget<>(phaseTermination, phaseScope);
    phaseScope.addChildThreadsScoreCalculationCount(
        runPartitionTasks(partList, phaseScope, ownership, terminationBudget));
    phaseScope.endingNow();
    logger.info(
        "{}Partitioned Search phase ({}) ended: time spent ({}), best score ({}),"
            + " move evaluation speed ({}/sec), step total ({}).",
        logIndentation,
        phaseIndex,
        phaseScope.getPhaseTimeMillisSpent(),
        phaseScope.getBestScore().raw(),
        phaseScope.getPhaseScoreCalculationSpeed(),
        phaseScope.getNextStepIndex());
    phaseEnded(phaseScope);
  }

  private long runPartitionTasks(
      List<Solution_> partList,
      PartitionedSearchPhaseScope<Solution_> phaseScope,
      PartitionOwnership<Solution_> ownership,
      PartitionTerminationBudget<Solution_> terminationBudget) {
    var solverScope = phaseScope.getSolverScope();
    var partitionQueue = new PartitionQueue<Solution_>(partList.size());
    var executor =
        Executors.newFixedThreadPool(
            partList.size(),
            ThreadUtils.requireNonNullThreads(threadFactory, "Partitioned Search"));
    solverScope.getWorkerRegistry().registerExecutor(executor, "Partitioned Search");
    var completionService = new ExecutorCompletionService<Long>(executor);
    var tasks = new ArrayList<PartitionTask<Solution_>>(partList.size());
    var futures = new ArrayList<Future<Long>>(partList.size());
    var pendingTasks = new IdentityHashMap<Future<Long>, PartitionTask<Solution_>>();
    var childTermination = new ChildThreadPlumbingTermination<Solution_>();
    var semaphore =
        runnablePartThreadLimit == null ? null : new Semaphore(runnablePartThreadLimit, true);
    long calculationCount = 0L;
    Throwable failure = null;
    try {
      for (int partIndex = 0; partIndex < partList.size(); partIndex++) {
        solverScope.checkYielding();
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("The parent solver thread was interrupted.");
        }
        terminationBudget.refresh();
        if (terminationBudget.isDefinitelyTerminated()) {
          break;
        }
        var childSolver =
            buildPartitionSolver(
                partIndex,
                solverScope,
                childTermination,
                semaphore,
                partitionQueue,
                terminationBudget);
        var task = new PartitionTask<>(childSolver, partList.get(partIndex));
        // Retain ownership before submission: a rejected or cancelled task never closes itself.
        tasks.add(task);
        var future = completionService.submit(task);
        futures.add(future);
        pendingTasks.put(future, task);
      }
      while (!pendingTasks.isEmpty()) {
        solverScope.checkYielding();
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("The parent solver thread was interrupted.");
        }
        terminationBudget.refresh();
        if (terminationBudget.isDefinitelyTerminated()
            || childTermination.isSolverTerminated(solverScope)) {
          break;
        }
        Future<Long> completed;
        while ((completed = completionService.poll()) != null) {
          var task = pendingTasks.remove(completed);
          calculationCount += completedCalculationCount(completed, task.getPartIndex());
        }
        var move = partitionQueue.poll(pendingTasks.isEmpty() ? 0L : 10L);
        if (move != null) {
          applyPartitionMove(move, phaseScope, ownership);
          terminationBudget.refresh();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      var interrupted =
          new IllegalStateException("Solver thread was interrupted in Partitioned Search.", e);
      failure = interrupted;
      throw interrupted;
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      if (failure != null) {
        partitionQueue.stopAcceptingMoves();
      }
      childTermination.terminateChildren();
      try {
        stopAndAwaitPartitions(executor, tasks, futures, partitionQueue, failure != null);
      } catch (RuntimeException | Error cleanupFailure) {
        partitionQueue.stopAcceptingMoves();
        if (failure == null) {
          throw cleanupFailure;
        }
        if (cleanupFailure != failure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
    }
    // Cooperative termination may have completed additional futures while the executor shut down.
    // Observe every result, including failures that happened after the parent requested
    // termination.
    try {
      for (var entry : pendingTasks.entrySet()) {
        calculationCount +=
            completedCalculationCount(entry.getKey(), entry.getValue().getPartIndex());
      }
    } finally {
      partitionQueue.stopAcceptingMoves();
    }
    // A cooperatively terminated construction/custom phase may publish its result only at its
    // end. No producer remains now, so this bounded final batch includes those last improvements.
    try {
      PartitionChangeMove<Solution_> move;
      while ((move = partitionQueue.poll(0L)) != null) {
        applyPartitionMove(move, phaseScope, ownership);
        terminationBudget.refresh();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solver thread was interrupted in Partitioned Search.", e);
    }
    return calculationCount;
  }

  private long completedCalculationCount(Future<Long> future, int partIndex) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Solver thread was interrupted in Partitioned Search.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException(
          "The partition child thread with partIndex ("
              + partIndex
              + ") has thrown an exception."
              + " Relayed here in the parent thread.",
          e.getCause());
    } catch (CancellationException e) {
      throw new IllegalStateException(
          "The partition task with partIndex (" + partIndex + ") was cancelled unexpectedly.", e);
    }
  }

  private void stopAndAwaitPartitions(
      ExecutorService executor,
      List<PartitionTask<Solution_>> tasks,
      List<Future<Long>> futures,
      PartitionQueue<Solution_> partitionQueue,
      boolean abort) {
    boolean interrupted = Thread.interrupted();
    RuntimeException shutdownFailure = null;
    try {
      if (abort || interrupted) {
        partitionQueue.stopAcceptingMoves();
        shutdownFailure = new IllegalStateException("Partitioned Search worker cleanup failed.");
        cancelPartitionTasks(executor, tasks, futures, shutdownFailure);
        if (shutdownFailure.getSuppressed().length == 0) {
          shutdownFailure = null;
        }
      } else {
        executor.shutdown();
      }
      long deadline =
          System.nanoTime() + TimeUnit.SECONDS.toNanos(ThreadUtils.getDefaultShutdownTimeout());
      while (!executor.isTerminated()) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
          partitionQueue.stopAcceptingMoves();
          var timeout =
              new IllegalStateException(
                  "Partitioned Search workers did not stop within "
                      + ThreadUtils.getDefaultShutdownTimeout()
                      + " seconds; forcing cancellation.");
          cancelPartitionTasks(executor, tasks, futures, timeout);
          if (shutdownFailure != null) {
            timeout.addSuppressed(shutdownFailure);
          }
          throw timeout;
        }
        try {
          executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
          partitionQueue.stopAcceptingMoves();
          var interruption =
              new IllegalStateException("Partitioned Search worker cleanup was interrupted.", e);
          if (shutdownFailure == null) {
            shutdownFailure = interruption;
          } else {
            shutdownFailure.addSuppressed(interruption);
          }
          cancelPartitionTasks(executor, tasks, futures, shutdownFailure);
        }
      }
      if (shutdownFailure != null) {
        throw shutdownFailure;
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void cancelPartitionTasks(
      ExecutorService executor,
      List<PartitionTask<Solution_>> tasks,
      List<Future<Long>> futures,
      Throwable failure) {
    for (var future : futures) {
      future.cancel(true);
    }
    for (var task : tasks) {
      try {
        task.cancelBeforeStart();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != failure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
    }
    executor.shutdownNow();
  }

  private void applyPartitionMove(
      PartitionChangeMove<Solution_> move,
      PartitionedSearchPhaseScope<Solution_> phaseScope,
      PartitionOwnership<Solution_> ownership) {
    var parentDirector = phaseScope.getScoreDirector();
    var step = move.rebase(parentDirector);
    ownership.validateMove(step, parentDirector);
    var stepScope = new PartitionedSearchStepScope<>(phaseScope);
    stepStarted(stepScope);
    stepScope.setStep(step);
    doStep(stepScope);
    stepEnded(stepScope);
    phaseScope.setLastCompletedStepScope(stepScope);
  }

  private PartitionSolver<Solution_> buildPartitionSolver(
      int partIndex,
      SolverScope<Solution_> solverScope,
      ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination,
      Semaphore runnablePartThreadSemaphore,
      PartitionQueue<Solution_> partitionQueue,
      PartitionTerminationBudget<Solution_> terminationBudget) {

    BestSolutionRecaller<Solution_> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(configPolicy.getEnvironmentMode());

    List<PhaseConfig> effectivePhaseConfigList = phaseConfigList;
    if (effectivePhaseConfigList == null || effectivePhaseConfigList.isEmpty()) {
      effectivePhaseConfigList =
          Arrays.asList(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
    }

    SolverScope<Solution_> partSolverScope =
        solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
    partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);

    try {
      UniversalTermination<Solution_> partTermination =
          UniversalTermination.or(
              childThreadPlumbingTermination,
              terminationBudget.createChildTermination(partSolverScope));
      var partConfigPolicy =
          configPolicy
              .copyChildThreadConfigPolicy()
              .cloneBuilder()
              .withRandom(partSolverScope.getWorkingRandom())
              .build();
      List<Phase<Solution_>> phaseList =
          PhaseFactory.buildPhases(
              effectivePhaseConfigList, partConfigPolicy, bestSolutionRecaller, partTermination);

      PartitionSolver<Solution_> partitionSolver =
          new PartitionSolver<>(
              configPolicy.getEnvironmentMode(),
              solverScope.getSolver().getScoreDirectorFactory(),
              bestSolutionRecaller,
              partTermination,
              phaseList,
              partSolverScope,
              partIndex);

      partitionSolver.addEventListener(
          bestSolutionChangedEvent -> {
            InnerScoreDirector<Solution_, ?> childScoreDirector =
                partSolverScope.getScoreDirector();
            PartitionChangeMove<Solution_> move =
                PartitionChangeMove.createMove(
                    childScoreDirector, bestSolutionChangedEvent.getNewBestSolution(), partIndex);

            partitionQueue.addMove(partIndex, move);
          });
      return partitionSolver;
    } catch (RuntimeException | Error failure) {
      try {
        partSolverScope.getScoreDirector().close();
      } catch (RuntimeException | Error cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  protected void doStep(PartitionedSearchStepScope<Solution_> stepScope) {
    var step = stepScope.getStep();
    stepScope.getScoreDirector().executeMove(step);
    calculateWorkingStepScore(stepScope, step);
    var solver = stepScope.getPhaseScope().getSolverScope().getSolver();
    solver.getBestSolutionRecaller().processWorkingSolutionDuringStep(stepScope);
  }

  @Override
  public void phaseStarted(PartitionedSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
  }

  @Override
  public void stepStarted(PartitionedSearchStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
  }

  @Override
  public void stepEnded(PartitionedSearchStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
  }

  @Override
  public void phaseEnded(PartitionedSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
  }

  public static class Builder<Solution_>
      extends AbstractPhaseBuilder<Solution_, DefaultPartitionedSearchPhase<Solution_>> {

    private final SolutionPartitioner<Solution_> solutionPartitioner;
    private final ThreadFactory threadFactory;
    private final Integer runnablePartThreadLimit;
    private final List<PhaseConfig> phaseConfigList;
    private final SolverTermination<Solution_> solverTermination;
    private final HeuristicConfigPolicy<Solution_> configPolicy;

    public Builder(
        int phaseIndex,
        String logIndentation,
        PhaseTermination<Solution_> phaseTermination,
        HeuristicConfigPolicy<Solution_> configPolicy,
        SolutionPartitioner<Solution_> solutionPartitioner,
        ThreadFactory threadFactory,
        Integer runnablePartThreadLimit,
        List<PhaseConfig> phaseConfigList,
        SolverTermination<Solution_> solverTermination) {
      super(phaseIndex, configPolicy.getEnvironmentMode(), logIndentation, phaseTermination);
      this.configPolicy = configPolicy;
      this.solutionPartitioner = solutionPartitioner;
      this.threadFactory = threadFactory;
      this.runnablePartThreadLimit = runnablePartThreadLimit;
      this.phaseConfigList = phaseConfigList;
      this.solverTermination = solverTermination;
    }

    @Override
    public DefaultPartitionedSearchPhase<Solution_> build() {
      return new DefaultPartitionedSearchPhase<>(this);
    }
  }
}
