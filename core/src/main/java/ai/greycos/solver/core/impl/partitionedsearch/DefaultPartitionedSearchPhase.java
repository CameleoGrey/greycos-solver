package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.partitionedsearch.event.PartitionedSearchPhaseLifecycleListener;
import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.partitionedsearch.queue.PartitionQueue;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PhaseFactory;
import ai.greycos.solver.core.impl.phase.PhaseType;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.ChildThreadPlumbingTermination;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;
import ai.greycos.solver.core.impl.solver.thread.ThreadUtils;

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
    var hasAnythingToImprove =
        solverScope.getProblemSizeStatistics().approximateProblemSizeLog() != 0.0;
    if (!hasAnythingToImprove) {
      logger.info(
          "{}Partitioned Search phase ({}) has no entities or values to move.",
          logIndentation,
          phaseIndex);
      return;
    }

    var phaseScope = new PartitionedSearchPhaseScope<>(solverScope, phaseIndex);
    phaseScope.setPartCount(null);
    phaseStarted(phaseScope);

    List<Solution_> partList =
        solutionPartitioner.splitWorkingSolution(
            phaseScope.getScoreDirector(), runnablePartThreadLimit);

    int partCount = partList.size();
    phaseScope.setPartCount(partCount);
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

    PartitionQueue<Solution_> partitionQueue = new PartitionQueue<>(partCount);

    ExecutorService executor = createThreadPoolExecutor(partCount);

    ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination =
        new ChildThreadPlumbingTermination<>();

    Semaphore runnablePartThreadSemaphore =
        runnablePartThreadLimit == null ? null : new Semaphore(runnablePartThreadLimit, true);

    try {
      submitPartitionSolverTasks(
          executor,
          partList,
          solverScope,
          childThreadPlumbingTermination,
          runnablePartThreadSemaphore,
          partitionQueue);

      for (PartitionChangeMove<Solution_> step : partitionQueue) {
        PartitionedSearchStepScope<Solution_> stepScope =
            new PartitionedSearchStepScope<>(phaseScope);
        stepStarted(stepScope);
        stepScope.setStep(step);
        doStep(stepScope);
        stepEnded(stepScope);
        phaseScope.setLastCompletedStepScope(stepScope);
      }

      childThreadPlumbingTermination.terminateChildren();

      ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Partitioned Search");

      phaseScope.addChildThreadsScoreCalculationCount(partitionQueue.getPartsCalculationCount());

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

    } finally {
      if (!executor.isTerminated()) {
        childThreadPlumbingTermination.terminateChildren();
        ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Partitioned Search");
      }
    }

    phaseEnded(phaseScope);
  }

  private ExecutorService createThreadPoolExecutor(int partCount) {
    ThreadPoolExecutor threadPoolExecutor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(partCount, threadFactory);
    if (threadPoolExecutor.getMaximumPoolSize() < partCount) {
      throw new IllegalStateException(
          "The threadPoolExecutor's maximumPoolSize ("
              + threadPoolExecutor.getMaximumPoolSize()
              + ") is less than partCount ("
              + partCount
              + "), so some partitions will starve.\n"
              + "Normally this is impossible because the threadPoolExecutor should be unbounded."
              + " Use runnablePartThreadLimit ("
              + runnablePartThreadLimit
              + ") instead to avoid CPU hogging and live locks.");
    }
    return threadPoolExecutor;
  }

  private void submitPartitionSolverTasks(
      ExecutorService executor,
      List<Solution_> partList,
      SolverScope<Solution_> solverScope,
      ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination,
      Semaphore runnablePartThreadSemaphore,
      PartitionQueue<Solution_> partitionQueue) {

    int partIndex = 0;
    for (Solution_ part : partList) {
      final int currentPartIndex = partIndex++;

      PartitionSolver<Solution_> partitionSolver =
          buildPartitionSolver(
              currentPartIndex,
              solverScope,
              childThreadPlumbingTermination,
              runnablePartThreadSemaphore,
              partitionQueue);

      executor.submit(
          () -> {
            try {
              partitionSolver.solve(part);
              long partCalculationCount = partitionSolver.getScoreCalculationCount();
              partitionQueue.addFinish(currentPartIndex, partCalculationCount);
            } catch (Throwable throwable) {
              partitionQueue.addExceptionThrown(currentPartIndex, throwable);
            }
          });
    }
  }

  private PartitionSolver<Solution_> buildPartitionSolver(
      int partIndex,
      SolverScope<Solution_> solverScope,
      ChildThreadPlumbingTermination<Solution_> childThreadPlumbingTermination,
      Semaphore runnablePartThreadSemaphore,
      PartitionQueue<Solution_> partitionQueue) {

    BestSolutionRecaller<Solution_> bestSolutionRecaller =
        BestSolutionRecallerFactory.create()
            .buildBestSolutionRecaller(configPolicy.getEnvironmentMode());

    UniversalTermination<Solution_> partTermination =
        UniversalTermination.or(
            childThreadPlumbingTermination,
            phaseTermination.createChildThreadTermination(
                solverScope, ChildThreadType.PART_THREAD));

    List<PhaseConfig> effectivePhaseConfigList = phaseConfigList;
    if (effectivePhaseConfigList == null || effectivePhaseConfigList.isEmpty()) {
      effectivePhaseConfigList =
          Arrays.asList(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());
    }

    List<Phase<Solution_>> phaseList =
        PhaseFactory.buildPhases(
            effectivePhaseConfigList,
            configPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD),
            bestSolutionRecaller,
            partTermination);

    SolverScope<Solution_> partSolverScope =
        solverScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
    partSolverScope.setRunnableThreadSemaphore(runnablePartThreadSemaphore);

    PartitionSolver<Solution_> partitionSolver =
        new PartitionSolver<>(
            bestSolutionRecaller, partTermination, phaseList, partSolverScope, partIndex);

    partitionSolver.setBestSolutionChangedListener(
        (eventProducerId, newBestSolution) -> {
          InnerScoreDirector<Solution_, ?> childScoreDirector = partSolverScope.getScoreDirector();
          PartitionChangeMove<Solution_> move =
              PartitionChangeMove.createMove(childScoreDirector, partIndex);

          InnerScoreDirector<Solution_, ?> parentScoreDirector = solverScope.getScoreDirector();
          move = move.rebase(parentScoreDirector);

          partitionQueue.addMove(partIndex, move);
        });
    return partitionSolver;
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

  public static class Builder<Solution_> extends AbstractPhaseBuilder<Solution_> {

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
      super(phaseIndex, logIndentation, phaseTermination);
      this.configPolicy = configPolicy;
      this.solutionPartitioner = solutionPartitioner;
      this.threadFactory = threadFactory;
      this.runnablePartThreadLimit = runnablePartThreadLimit;
      this.phaseConfigList = phaseConfigList;
      this.solverTermination = solverTermination;
    }

    @Override
    public DefaultPartitionedSearchPhase.Builder<Solution_> enableAssertions(
        EnvironmentMode environmentMode) {
      super.enableAssertions(environmentMode);
      return this;
    }

    @Override
    public DefaultPartitionedSearchPhase<Solution_> build() {
      return new DefaultPartitionedSearchPhase<>(this);
    }
  }
}
