package greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.phase.PhaseFactory;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.phase.event.PhaseEventProducerId;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.ClassInstanceCache;
import greycos.solver.core.impl.solver.change.DefaultProblemChangeDirector;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.IslandTerminationBudget;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.termination.UniversalTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.ThreadUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Island model phase that coordinates multiple independent island agents. Agents run same phases
 * independently and exchange best solutions through migration in a ring topology.
 */
public class DefaultIslandModelPhase<Solution_> extends AbstractPhase<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIslandModelPhase.class);

  private final IslandModelPhaseConfig islandModelConfig;
  private final int islandCount;
  private final int migrationFrequency;
  private final boolean compareGlobalEnabled;
  private final int receiveGlobalUpdateFrequency;
  private final long migrationTimeout;
  private volatile SharedGlobalState<Solution_> globalState;
  private final HeuristicConfigPolicy<Solution_> configPolicy;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final SolverTermination<Solution_> solverTermination;

  private DefaultIslandModelPhase(Builder<Solution_> builder) {
    super(builder);
    this.islandModelConfig = builder.islandModelConfig;
    this.islandCount = builder.islandCount;
    this.migrationFrequency = builder.migrationFrequency;
    this.compareGlobalEnabled = builder.compareGlobalEnabled;
    this.receiveGlobalUpdateFrequency = builder.receiveGlobalUpdateFrequency;
    this.migrationTimeout = builder.migrationTimeout;
    this.globalState = new SharedGlobalState<>();
    this.configPolicy = builder.configPolicy;
    this.bestSolutionRecaller = builder.bestSolutionRecaller;
    this.solverTermination = builder.solverTermination;

    LOGGER.info(
        "DefaultIslandModelPhase created with {} islands, migration frequency: {}, compare to global: {} (receive frequency: {}, migration timeout: {}ms)",
        islandCount,
        migrationFrequency,
        compareGlobalEnabled,
        receiveGlobalUpdateFrequency,
        migrationTimeout);
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.ISLAND_MODEL;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return i -> new PhaseEventProducerId(getPhaseType(), i);
  }

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    var phaseScope = new IslandModelPhaseScope<>(solverScope, phaseIndex);
    boolean phaseLifecycleStarted = false;
    var runState = new SharedGlobalState<Solution_>();
    globalState = runState;
    GlobalBestPropagator<Solution_> propagator = null;
    Throwable failure = null;
    try {
      LOGGER.info(
          "{}Island Model phase ({}) starting with {} islands",
          logIndentation,
          phaseIndex,
          islandCount);

      phaseStarted(phaseScope);
      phaseLifecycleStarted = true;

      var outerBudget =
          new IslandTerminationBudget<Solution_>(
              Objects.requireNonNullElseGet(
                  islandModelConfig.getTerminationConfig(), TerminationConfig::new),
              configPolicy,
              solverScope.getClock(),
              phaseScope.getStartingSystemTimeMillis(),
              solverScope.getStartingInitializedScore());
      runState.reset(
          solverScope.getClock(),
          snapshot ->
              outerBudget.bestScoreImproved(
                  snapshot.getInnerScore(), snapshot.getTimestampMillis(), snapshot.getVersion()),
          solverScope.getSolver() instanceof IslandSolver<Solution_> islandSolver
              ? islandSolver.getEnclosingGlobalState()
              : null);
      warnAboutPotentialThreadOversubscription();

      var initialSolution = solverScope.getBestSolution();
      var innerScore = solverScope.getBestScore();
      if (innerScore == null) {
        innerScore = solverScope.calculateScore();
      }
      runState.tryUpdate(initialSolution, innerScore);

      propagator =
          new GlobalBestPropagator<>(
              runState,
              solverScope,
              getSolverEventSupport(solverScope),
              new PhaseEventProducerId(getPhaseType(), phaseIndex),
              islandCount);
      propagator.start();

      createAndRunAgents(solverScope, outerBudget, runState, propagator);

      var globalBest = runState.getBestSolution();
      if (globalBest != null) {
        LOGGER.info(
            "{}Island Model phase ({}) ended. Global best score: {}",
            logIndentation,
            phaseIndex,
            runState.getBestScore());
        solverScope.setInitialSolution(globalBest);
      } else {
        LOGGER.warn(
            "{}Island Model phase ({}) ended with no global best solution",
            logIndentation,
            phaseIndex);
      }
      phaseScope.endingNow();

    } catch (RuntimeException | Error e) {
      failure = e;
      LOGGER.error(
          "{}Island Model phase ({}) encountered error",
          logIndentation,
          phaseIndex,
          e.getMessage(),
          e);
      throw e;
    } finally {
      if (propagator != null) {
        propagator.stop();
      }
      runState.close();
      if (phaseLifecycleStarted) {
        try {
          phaseEnded(phaseScope);
        } catch (RuntimeException | Error cleanupFailure) {
          if (failure == null) {
            throw cleanupFailure;
          }
          if (cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
      }
    }
  }

  private void createAndRunAgents(
      SolverScope<Solution_> solverScope,
      IslandTerminationBudget<Solution_> outerBudget,
      SharedGlobalState<Solution_> runState,
      GlobalBestPropagator<Solution_> propagator) {
    var threadFactory =
        ThreadUtils.requireNonNullThreads(
            configPolicy.buildThreadFactory(ChildThreadType.PART_THREAD), "Island Model");
    var executor = Executors.newFixedThreadPool(islandCount, threadFactory);
    solverScope.getWorkerRegistry().registerExecutor(executor, "island phase " + phaseIndex);
    Queue<Future<Void>> completedTasks = new ConcurrentLinkedQueue<>();
    var channels = new ArrayList<BoundedChannel<AgentUpdate<Solution_>>>(islandCount);
    var completionLatch =
        new IslandCompletionTracker(islandCount, () -> channels.forEach(BoundedChannel::close));
    var futures = new ArrayList<Future<Void>>(islandCount);
    var agents = new ArrayList<IslandAgent<Solution_>>(islandCount);
    Throwable failure = null;
    boolean restoreInterrupt = false;

    try {
      LOGGER.info("Creating {} island agents with ring topology...", islandCount);
      for (int i = 0; i < islandCount; i++) {
        channels.add(new BoundedChannel<>(1));
      }
      for (int i = 0; i < islandCount; i++) {
        solverScope.checkYielding();
        if (Thread.currentThread().isInterrupted()) {
          throw new InterruptedException("Interrupted while creating island agents.");
        }
        var agentScope = createAgentSolverScope(solverScope, i);
        IslandAgent<Solution_> agent = null;
        try {
          var source = solverScope.getWorkerPath() + "phase-" + phaseIndex + "/island-" + i;
          agentScope.setWorkerPath(source + "/");
          agentScope.setMetricSource(source);
          solverScope.getIslandWorkAccounting().register(source);
          var accounting = solverScope.getIslandWorkAccounting();
          agentScope.setMetricSamplePublisher(
              sample -> {
                if (!solverScope.hasMetricSampleListeners()) {
                  // Reporting without a benchmark subscriber does not synchronize every search step
                  // with the coordinator. Only immutable cumulative counters cross this boundary.
                  accounting.publish(sample.source(), sample.work());
                } else if (solverScope.hasMetricSamplePublisher()) {
                  // Nested islands forward the unchanged local snapshot to the outer coordinator.
                  solverScope.publishMetricSample(sample);
                } else {
                  propagator.enqueue(
                      () -> {
                        if (accounting.publish(sample.source(), sample.work())) {
                          solverScope.publishMetricSample(
                              sample.withWork(SolverMetricSamples.reportedWork(solverScope)));
                        }
                      });
                }
              });
          var agentRandom = agentScope.getWorkingRandom();
          var agentConfigPolicy = createAgentConfigPolicy(agentRandom);
          var agentTermination =
              UniversalTermination.or(
                  outerBudget.createChildSolverTermination(solverTermination, agentScope),
                  outerBudget.createIslandTermination(agentScope));
          var agentRecaller =
              BestSolutionRecallerFactory.create()
                  .<Solution_>buildBestSolutionRecaller(agentConfigPolicy.getEnvironmentMode());
          var agentPhases = buildPhasesForAgent(agentConfigPolicy, agentRecaller, agentTermination);
          var islandSolver =
              new IslandSolver<>(
                  agentConfigPolicy.getEnvironmentMode(),
                  solverScope.getSolver().getScoreDirectorFactory(),
                  agentRecaller,
                  toUniversalTermination(agentTermination),
                  agentPhases,
                  runState);
          agentScope.setSolver(islandSolver);
          var initialSolution =
              solverScope.getScoreDirector().cloneSolution(solverScope.getBestSolution());
          agent =
              createAgent(
                  i,
                  channels.get((i + 1) % islandCount),
                  channels.get(i),
                  agentPhases,
                  agentScope,
                  initialSolution,
                  completionLatch,
                  agentRandom,
                  runState);
          // Keep ownership even if submission fails or cancellation wins before run() starts.
          agents.add(agent);
          var task = new IslandTask(agent, completedTasks, propagator.getSignal());
          futures.add(task);
          executor.execute(task);
        } catch (RuntimeException | Error startupFailure) {
          if (agent == null) {
            try {
              agentScope.getScoreDirector().close();
            } catch (RuntimeException | Error cleanupFailure) {
              if (cleanupFailure != startupFailure) startupFailure.addSuppressed(cleanupFailure);
            }
          }
          throw startupFailure;
        }
      }

      awaitAgents(solverScope, propagator, completedTasks, islandCount);
      LOGGER.info("All {} island agents completed", islandCount);
    } catch (InterruptedException e) {
      restoreInterrupt = true;
      var interrupted =
          new IllegalStateException("Interrupted while waiting for island agents.", e);
      failure = interrupted;
      throw interrupted;
    } catch (ExecutionException e) {
      var agentFailure = new IllegalStateException("Island agent failed.", e.getCause());
      failure = agentFailure;
      throw agentFailure;
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      if (failure != null) {
        propagator.stop();
        channels.forEach(BoundedChannel::close);
        for (var future : futures) future.cancel(true);
        for (var agent : agents) {
          try {
            agent.cancelBeforeStart();
          } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
          }
        }
        for (int i = agents.size(); i < islandCount; i++) completionLatch.countDown();
        executor.shutdownNow();
      } else {
        executor.shutdown();
      }
      restoreInterrupt |= awaitAgentExecutorTermination(executor);
      if (restoreInterrupt) Thread.currentThread().interrupt();
    }
  }

  static <Solution_> void awaitAgents(
      SolverScope<Solution_> solverScope,
      GlobalBestPropagator<Solution_> propagator,
      Queue<Future<Void>> completedTasks,
      int islandCount)
      throws InterruptedException, ExecutionException {
    var signal = propagator.getSignal();
    int completed = 0;
    while (completed < islandCount) {
      solverScope.checkYielding();
      if (Thread.interrupted()) {
        throw new InterruptedException("Interrupted while waiting for island agents.");
      }
      // Observe before checking either queue so an event between checking and waiting is retained.
      long observedGeneration = signal.generation();
      completed += collectCompletedAgents(completedTasks);
      int drained = propagator.drain();
      completed += collectCompletedAgents(completedTasks);
      if (completed < islandCount && drained == 0) {
        // An enclosing partition must not hold a runnable permit while this coordinator sleeps.
        // The next loop's checkYielding() reacquires it, including after a concurrent signal.
        solverScope.destroyYielding();
        signal.awaitChange(observedGeneration);
      }
    }
    // No publisher remains; one bounded batch contains all remaining publications.
    propagator.drain();
  }

  private static int collectCompletedAgents(Queue<Future<Void>> completedTasks)
      throws InterruptedException, ExecutionException {
    int completed = 0;
    Future<Void> future;
    while ((future = completedTasks.poll()) != null) {
      future.get();
      completed++;
    }
    return completed;
  }

  private boolean awaitAgentExecutorTermination(ExecutorService executor) {
    try {
      if (!executor.awaitTermination(ThreadUtils.getDefaultShutdownTimeout(), TimeUnit.SECONDS)) {
        LOGGER.warn(
            "{}Island agent thread pool did not terminate within {} seconds; forcing shutdown.",
            logIndentation,
            ThreadUtils.getDefaultShutdownTimeout());
        executor.shutdownNow();
      }
      return false;
    } catch (InterruptedException e) {
      executor.shutdownNow();
      return true;
    }
  }

  private IslandAgent<Solution_> createAgent(
      int agentId,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      List<Phase<Solution_>> agentPhases,
      SolverScope<Solution_> agentScope,
      Solution_ initialSolution,
      CountDownLatch completionLatch,
      RandomSource agentRandom,
      SharedGlobalState<Solution_> runState) {
    var config =
        IslandModelConfig.builder()
            .withIslandCount(islandCount)
            .withMigrationFrequency(migrationFrequency)
            .withCompareGlobalEnabled(compareGlobalEnabled)
            .withReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency)
            .withMigrationTimeout(migrationTimeout)
            .build();

    return new IslandAgent<>(
        agentId,
        agentPhases,
        initialSolution,
        runState,
        sender,
        receiver,
        config,
        agentRandom,
        agentScope,
        completionLatch);
  }

  private List<Phase<Solution_>> buildPhasesForAgent(
      HeuristicConfigPolicy<Solution_> agentConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    var configuredPhaseConfigList = islandModelConfig.getPhaseConfigList();
    if (configuredPhaseConfigList != null && !configuredPhaseConfigList.isEmpty()) {
      var copiedPhaseConfigList = copyPhaseConfigList(configuredPhaseConfigList);
      normalizeInnerPhaseMoveThreadCount(copiedPhaseConfigList);
      return PhaseFactory.buildPhases(
          copiedPhaseConfigList, agentConfigPolicy, bestSolutionRecaller, solverTermination);
    }

    var localSearchConfig = buildDefaultLocalSearchPhaseConfig();
    return PhaseFactory.buildPhases(
        List.of(localSearchConfig), agentConfigPolicy, bestSolutionRecaller, solverTermination);
  }

  private LocalSearchPhaseConfig buildDefaultLocalSearchPhaseConfig() {
    var localSearchConfig = new LocalSearchPhaseConfig();
    localSearchConfig.setLocalSearchType(islandModelConfig.getLocalSearchType());
    var configuredMoveThreadCount = islandModelConfig.getMoveThreadCount();
    localSearchConfig.setMoveThreadCount(
        configuredMoveThreadCount != null
            ? configuredMoveThreadCount
            : SolverConfig.MOVE_THREAD_COUNT_NONE);

    var moveSelectorConfig = islandModelConfig.getMoveSelectorConfig();
    if (moveSelectorConfig != null) {
      @SuppressWarnings("unchecked")
      var copiedConfig = (MoveSelectorConfig) moveSelectorConfig.copyConfig();
      localSearchConfig.setMoveSelectorConfig(copiedConfig);
    }

    var guidedLocalSearchConfig = islandModelConfig.getGuidedLocalSearchConfig();
    if (guidedLocalSearchConfig != null) {
      localSearchConfig.setGuidedLocalSearchConfig(guidedLocalSearchConfig.copyConfig());
    }

    var acceptorConfig = islandModelConfig.getAcceptorConfig();
    if (acceptorConfig != null) {
      localSearchConfig.setAcceptorConfig(acceptorConfig.copyConfig());
    }

    var foragerConfig = islandModelConfig.getForagerConfig();
    if (foragerConfig != null) {
      localSearchConfig.setForagerConfig(foragerConfig.copyConfig());
    }

    // The outer budget spans the entire island sequence and is already part of its termination.
    return localSearchConfig;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static List<PhaseConfig> copyPhaseConfigList(List<PhaseConfig<?>> phaseConfigList) {
    var copiedPhaseConfigList = new ArrayList<PhaseConfig>(phaseConfigList.size());
    for (var phaseConfig : phaseConfigList) {
      copiedPhaseConfigList.add(phaseConfig.copyConfig());
    }
    return copiedPhaseConfigList;
  }

  @SuppressWarnings("rawtypes")
  private void normalizeInnerPhaseMoveThreadCount(List<PhaseConfig> phaseConfigList) {
    var configuredMoveThreadCount = islandModelConfig.getMoveThreadCount();
    var defaultMoveThreadCount =
        configuredMoveThreadCount != null
            ? configuredMoveThreadCount
            : SolverConfig.MOVE_THREAD_COUNT_NONE;
    for (var phaseConfig : phaseConfigList) {
      if (phaseConfig instanceof LocalSearchPhaseConfig localSearchPhaseConfig
          && localSearchPhaseConfig.getMoveThreadCount() == null) {
        localSearchPhaseConfig.setMoveThreadCount(defaultMoveThreadCount);
      } else if (phaseConfig instanceof AlnsPhaseConfig alnsPhaseConfig
          && alnsPhaseConfig.getMoveThreadCount() == null) {
        alnsPhaseConfig.setMoveThreadCount(defaultMoveThreadCount);
      }
    }
  }

  private void warnAboutPotentialThreadOversubscription() {
    if (islandCount <= 1) {
      return;
    }
    var moveThreadCount = islandModelConfig.getMoveThreadCount();
    if (moveThreadCount != null && !SolverConfig.MOVE_THREAD_COUNT_NONE.equals(moveThreadCount)) {
      LOGGER.warn(
          "Island model is configured with islandCount={} and moveThreadCount='{}'. "
              + "This can oversubscribe CPUs; consider using moveThreadCount='{}' for islands.",
          islandCount,
          moveThreadCount,
          SolverConfig.MOVE_THREAD_COUNT_NONE);
    }
  }

  private UniversalTermination<Solution_> toUniversalTermination(
      SolverTermination<Solution_> termination) {
    if (termination instanceof UniversalTermination<?> universalTermination) {
      @SuppressWarnings("unchecked")
      var castTermination = (UniversalTermination<Solution_>) universalTermination;
      return castTermination;
    }
    return UniversalTermination.or(termination);
  }

  private HeuristicConfigPolicy<Solution_> createAgentConfigPolicy(RandomSource agentRandom) {
    var basePolicy = configPolicy.copyChildThreadConfigPolicy();
    return basePolicy
        .cloneBuilder()
        .withRandom(agentRandom)
        .withClassInstanceCache(ClassInstanceCache.create())
        .withEntitySorterManner(basePolicy.getEntitySorterManner())
        .withValueSorterManner(basePolicy.getValueSorterManner())
        .withReinitializeVariableFilterEnabled(basePolicy.isReinitializeVariableFilterEnabled())
        .withUnassignedValuesAllowed(basePolicy.isUnassignedValuesAllowed())
        .build();
  }

  private SolverScope<Solution_> createAgentSolverScope(
      SolverScope<Solution_> parentScope, int agentId) {
    var agentScope = parentScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
    var islandId =
        parentScope.getWorkerPath().isEmpty()
            ? Integer.toString(agentId)
            : parentScope.getWorkerPath() + "phase-" + phaseIndex + "/island-" + agentId;
    agentScope.setMonitoringTags(parentScope.getMonitoringTags().and("island.id", islandId));
    var scoreDirector = agentScope.getScoreDirector();
    scoreDirector.resetCalculationCount();
    agentScope.setProblemChangeDirector(new DefaultProblemChangeDirector<>(scoreDirector));
    return agentScope;
  }

  public SharedGlobalState<Solution_> getGlobalState() {
    return globalState;
  }

  private SolverEventSupport<Solution_> getSolverEventSupport(SolverScope<Solution_> solverScope) {
    var solver = solverScope.getSolver();
    if (solver instanceof AbstractSolver<Solution_>) {
      var abstractSolver = (AbstractSolver<Solution_>) solver;
      return abstractSolver.getSolverEventSupport();
    }
    throw new IllegalStateException(
        "Solver must be an AbstractSolver to access SolverEventSupport");
  }

  @Override
  public String toString() {
    return "DefaultIslandModelPhase{"
        + "phaseIndex="
        + phaseIndex
        + ", islandCount="
        + islandCount
        + ", migrationFrequency="
        + migrationFrequency
        + '}';
  }

  public static class Builder<Solution_>
      extends AbstractPhaseBuilder<Solution_, DefaultIslandModelPhase<Solution_>> {

    private IslandModelPhaseConfig islandModelConfig;
    private int islandCount = IslandModelConfig.DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = IslandModelConfig.DEFAULT_MIGRATION_FREQUENCY;
    private boolean compareGlobalEnabled = true;
    private int receiveGlobalUpdateFrequency =
        IslandModelConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
    private long migrationTimeout = IslandModelConfig.DEFAULT_MIGRATION_TIMEOUT;
    private HeuristicConfigPolicy<Solution_> configPolicy;
    private BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private SolverTermination<Solution_> solverTermination;

    public Builder(
        int phaseIndex,
        EnvironmentMode environmentMode,
        String logIndentation,
        PhaseTermination<Solution_> phaseTermination) {
      super(phaseIndex, environmentMode, logIndentation, phaseTermination);
    }

    public Builder<Solution_> withIslandModelConfig(IslandModelPhaseConfig islandModelConfig) {
      this.islandModelConfig = islandModelConfig;
      return this;
    }

    public Builder<Solution_> withConfigPolicy(HeuristicConfigPolicy<Solution_> configPolicy) {
      this.configPolicy = configPolicy;
      return this;
    }

    public Builder<Solution_> withBestSolutionRecaller(
        BestSolutionRecaller<Solution_> bestSolutionRecaller) {
      this.bestSolutionRecaller = bestSolutionRecaller;
      return this;
    }

    public Builder<Solution_> withSolverTermination(
        SolverTermination<Solution_> solverTermination) {
      this.solverTermination = solverTermination;
      return this;
    }

    public Builder<Solution_> withIslandCount(int islandCount) {
      this.islandCount = islandCount;
      return this;
    }

    public Builder<Solution_> withMigrationFrequency(int migrationFrequency) {
      this.migrationFrequency = migrationFrequency;
      return this;
    }

    public Builder<Solution_> withCompareGlobalEnabled(boolean compareGlobalEnabled) {
      this.compareGlobalEnabled = compareGlobalEnabled;
      return this;
    }

    public Builder<Solution_> withReceiveGlobalUpdateFrequency(int receiveGlobalUpdateFrequency) {
      this.receiveGlobalUpdateFrequency = receiveGlobalUpdateFrequency;
      return this;
    }

    public Builder<Solution_> withMigrationTimeout(long migrationTimeout) {
      this.migrationTimeout = migrationTimeout;
      return this;
    }

    @Deprecated
    public Builder<Solution_> withCompareGlobalFrequency(int compareGlobalFrequency) {
      this.receiveGlobalUpdateFrequency = compareGlobalFrequency;
      return this;
    }

    @Override
    public DefaultIslandModelPhase<Solution_> build() {
      return new DefaultIslandModelPhase<>(this);
    }
  }
}
