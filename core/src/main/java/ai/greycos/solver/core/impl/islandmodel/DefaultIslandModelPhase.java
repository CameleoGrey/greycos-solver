package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PhaseFactory;
import ai.greycos.solver.core.impl.phase.PhaseType;
import ai.greycos.solver.core.impl.phase.event.PhaseEventProducerId;
import ai.greycos.solver.core.impl.solver.AbstractSolver;
import ai.greycos.solver.core.impl.solver.ClassInstanceCache;
import ai.greycos.solver.core.impl.solver.change.DefaultProblemChangeDirector;
import ai.greycos.solver.core.impl.solver.event.SolverEventSupport;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecallerFactory;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.ChildThreadSupportingTermination;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

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
  private final SharedGlobalState<Solution_> globalState;
  private SolverScope<Solution_> solverScope; // Cache for solution cloning
  private final HeuristicConfigPolicy<Solution_> configPolicy;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final SolverTermination<Solution_> solverTermination;
  private GlobalBestPropagator<Solution_> globalBestPropagator;

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
    try {
      LOGGER.info(
          "{}Island Model phase ({}) starting with {} islands",
          logIndentation,
          phaseIndex,
          islandCount);

      phaseStarted(phaseScope);
      phaseLifecycleStarted = true;

      this.solverScope = solverScope;
      globalState.reset();

      var initialSolution = solverScope.getBestSolution();
      var innerScore = solverScope.getBestScore();
      if (innerScore == null) {
        innerScore = solverScope.calculateScore();
      }
      globalState.tryUpdate(initialSolution, innerScore.raw());

      globalBestPropagator =
          new GlobalBestPropagator<>(
              globalState,
              solverScope,
              getSolverEventSupport(solverScope),
              new PhaseEventProducerId(getPhaseType(), phaseIndex));
      globalBestPropagator.start();

      createAndRunAgents(solverScope);

      var globalBest = globalState.getBestSolution();
      if (globalBest != null) {
        LOGGER.info(
            "{}Island Model phase ({}) ended. Global best score: {}",
            logIndentation,
            phaseIndex,
            globalState.getBestScore());
        solverScope.setInitialSolution(globalBest);
      } else {
        LOGGER.warn(
            "{}Island Model phase ({}) ended with no global best solution",
            logIndentation,
            phaseIndex);
      }
      phaseScope.endingNow();

    } catch (Exception e) {
      LOGGER.error(
          "{}Island Model phase ({}) encountered error",
          logIndentation,
          phaseIndex,
          e.getMessage(),
          e);
      throw e;
    } finally {
      if (globalBestPropagator != null) {
        globalBestPropagator.stop();
      }
      if (phaseLifecycleStarted) {
        phaseEnded(phaseScope);
      }
    }
  }

  private void createAndRunAgents(SolverScope<Solution_> solverScope) {
    var threadCounter = new AtomicInteger(0);
    var executor =
        Executors.newFixedThreadPool(
            islandCount,
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setName("island-agent-" + threadCounter.getAndIncrement());
              return thread;
            });
    var random = solverScope.getWorkingRandom();
    var completionLatch = new CountDownLatch(islandCount);
    var futures = new ArrayList<Future<?>>(islandCount);

    LOGGER.info("Creating {} island agents with ring topology...", islandCount);

    var channels = new ArrayList<BoundedChannel<AgentUpdate<Solution_>>>(islandCount);
    for (int i = 0; i < islandCount; i++) {
      channels.add(new BoundedChannel<>(1));
    }

    for (int i = 0; i < islandCount; i++) {
      var receiver = channels.get(i);
      var sender = channels.get((i + 1) % islandCount);

      var agentRandom = new Random(random.nextLong());
      var agentConfigPolicy = createAgentConfigPolicy(agentRandom);
      var agentScope = createAgentSolverScope(solverScope);
      var agentTermination = createAgentTermination(agentScope);
      var agentRecaller =
          BestSolutionRecallerFactory.create()
              .<Solution_>buildBestSolutionRecaller(agentConfigPolicy.getEnvironmentMode());
      var agentPhases = buildPhasesForAgent(agentConfigPolicy, agentRecaller, agentTermination);
      var islandSolver =
          new IslandSolver<>(agentRecaller, toUniversalTermination(agentTermination));
      agentScope.setSolver(islandSolver);
      var initialSolution = deepCloneSolution(solverScope.getBestSolution());
      var agent =
          createAgent(
              i,
              sender,
              receiver,
              agentPhases,
              agentScope,
              initialSolution,
              completionLatch,
              agentRandom);
      futures.add(executor.submit(agent));
    }

    executor.shutdown();

    try {
      boolean terminated = executor.awaitTermination(24, TimeUnit.HOURS);
      if (!terminated) {
        executor.shutdownNow();
        throw new IllegalStateException("Timed out waiting for island agents to complete.");
      }
      for (Future<?> future : futures) {
        future.get();
      }
      LOGGER.info("All {} island agents completed", islandCount);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for agents", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Island agent failed.", e.getCause());
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
      Random agentRandom) {
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
        globalState,
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
    localSearchConfig.setMoveThreadCount(islandModelConfig.getMoveThreadCount());

    var moveSelectorConfig = islandModelConfig.getMoveSelectorConfig();
    if (moveSelectorConfig != null) {
      @SuppressWarnings("unchecked")
      var copiedConfig = (MoveSelectorConfig) moveSelectorConfig.copyConfig();
      localSearchConfig.setMoveSelectorConfig(copiedConfig);
    }

    var acceptorConfig = islandModelConfig.getAcceptorConfig();
    if (acceptorConfig != null) {
      localSearchConfig.setAcceptorConfig(acceptorConfig.copyConfig());
    }

    var foragerConfig = islandModelConfig.getForagerConfig();
    if (foragerConfig != null) {
      localSearchConfig.setForagerConfig(foragerConfig.copyConfig());
    }

    var terminationConfig = islandModelConfig.getTerminationConfig();
    if (terminationConfig != null) {
      localSearchConfig.setTerminationConfig(terminationConfig.copyConfig());
    }
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

  private SolverTermination<Solution_> createAgentTermination(SolverScope<Solution_> solverScope) {
    var childTermination =
        ChildThreadSupportingTermination.assertChildThreadSupport(solverTermination)
            .createChildThreadTermination(solverScope, ChildThreadType.PART_THREAD);
    if (childTermination instanceof SolverTermination<?> solverChildTermination) {
      @SuppressWarnings("unchecked")
      var castTermination = (SolverTermination<Solution_>) solverChildTermination;
      return castTermination;
    }
    throw new IllegalStateException(
        "Child termination (" + childTermination + ") does not implement SolverTermination.");
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

  private HeuristicConfigPolicy<Solution_> createAgentConfigPolicy(Random agentRandom) {
    var basePolicy = configPolicy.createChildThreadConfigPolicy(ChildThreadType.PART_THREAD);
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

  private SolverScope<Solution_> createAgentSolverScope(SolverScope<Solution_> parentScope) {
    var agentScope = parentScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
    var parentScoreDirector = parentScope.getScoreDirector();
    var scoreDirectorFactory = parentScoreDirector.getScoreDirectorFactory();
    var newScoreDirector =
        scoreDirectorFactory
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .withConstraintMatchPolicy(parentScoreDirector.getConstraintMatchPolicy())
            .build();
    var previousScoreDirector = agentScope.getScoreDirector();
    try {
      previousScoreDirector.close();
    } catch (Exception e) {
      LOGGER.warn("Failed to close island score director replacement.", e);
    }
    agentScope.setScoreDirector(newScoreDirector);
    agentScope.setProblemChangeDirector(new DefaultProblemChangeDirector<>(newScoreDirector));
    return agentScope;
  }

  @SuppressWarnings("unchecked")
  private Solution_ deepCloneSolution(Solution_ solution) {
    if (solution == null) {
      throw new IllegalStateException("Solution to clone cannot be null");
    }
    if (solverScope == null) {
      throw new IllegalStateException("Solver scope not set for solution cloning");
    }
    return solverScope.getScoreDirector().cloneSolution(solution);
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

  public static class Builder<Solution_> extends AbstractPhaseBuilder<Solution_> {

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
        int phaseIndex, String logIndentation, PhaseTermination<Solution_> phaseTermination) {
      super(phaseIndex, logIndentation, phaseTermination);
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
    public Builder<Solution_> enableAssertions(EnvironmentMode environmentMode) {
      super.enableAssertions(environmentMode);
      return this;
    }

    @Override
    public DefaultIslandModelPhase<Solution_> build() {
      return new DefaultIslandModelPhase<>(this);
    }
  }
}
