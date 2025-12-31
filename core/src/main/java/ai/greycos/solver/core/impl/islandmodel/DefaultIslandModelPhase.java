package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PhaseFactory;
import ai.greycos.solver.core.impl.phase.PhaseType;
import ai.greycos.solver.core.impl.phase.event.PhaseEventProducerId;
import ai.greycos.solver.core.impl.solver.AbstractSolver;
import ai.greycos.solver.core.impl.solver.event.SolverEventSupport;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.solver.termination.SolverTermination;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Island model phase that coordinates multiple independent island agents.
 *
 * <p>This phase creates and manages multiple island agents, each running same phases independently.
 * Agents periodically exchange their best solutions through migration in a ring topology.
 *
 * <p>The island model is an opt-in feature that provides:
 *
 * <ul>
 *   <li>Enhanced solution quality through migration
 *   <li>Near-linear horizontal scaling
 *   <li>Fault tolerance (if one island fails, others continue)
 * </ul>
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public class DefaultIslandModelPhase<Solution_> extends AbstractPhase<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIslandModelPhase.class);

  private final ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig islandModelConfig;
  private final int islandCount;
  private final int migrationFrequency;
  private final boolean compareGlobalEnabled;
  private final int receiveGlobalUpdateFrequency;
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
    this.globalState = new SharedGlobalState<>();
    this.configPolicy = builder.configPolicy;
    this.bestSolutionRecaller = builder.bestSolutionRecaller;
    this.solverTermination = builder.solverTermination;

    LOGGER.info(
        "DefaultIslandModelPhase created with {} islands, migration frequency: {}, compare to global: {} (receive frequency: {})",
        islandCount,
        migrationFrequency,
        compareGlobalEnabled,
        receiveGlobalUpdateFrequency);
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
    try {
      LOGGER.info(
          "{}Island Model phase ({}) starting with {} islands",
          logIndentation,
          phaseIndex,
          islandCount);

      this.solverScope = solverScope;

      var initialSolution = solverScope.getBestSolution();
      var innerScore = solverScope.calculateScore();
      globalState.tryUpdate(initialSolution, innerScore.raw());

      // Start propagating global best updates to main solver scope
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

    } catch (Exception e) {
      LOGGER.error(
          "{}Island Model phase ({}) encountered error",
          logIndentation,
          phaseIndex,
          e.getMessage(),
          e);
      throw e;
    } finally {
      // Ensure propagator is stopped even on exception
      if (globalBestPropagator != null) {
        globalBestPropagator.stop();
      }
    }
  }

  private void createAndRunAgents(SolverScope<Solution_> solverScope) {
    var executor = Executors.newFixedThreadPool(islandCount);
    var random = solverScope.getWorkingRandom();

    LOGGER.info("Creating {} island agents with ring topology...", islandCount);

    var channels = new ArrayList<BoundedChannel<AgentUpdate<Solution_>>>(islandCount);
    for (int i = 0; i < islandCount; i++) {
      channels.add(new BoundedChannel<>(1));
    }

    for (int i = 0; i < islandCount; i++) {
      var receiver = channels.get(i);
      var sender = channels.get((i + 1) % islandCount);

      var agentPhases = buildPhasesForAgent();
      var agent = createAgent(solverScope, random, i, sender, receiver, agentPhases);
      executor.submit(agent);
    }

    executor.shutdown();

    try {
      executor.awaitTermination(24, TimeUnit.HOURS);
      LOGGER.info("All {} island agents completed", islandCount);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for agents", e);
    }
  }

  private IslandAgent<Solution_> createAgent(
      SolverScope<Solution_> solverScope,
      Random random,
      int agentId,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      List<Phase<Solution_>> agentPhases) {
    var agentRandom = new Random(random.nextLong());
    var config =
        IslandModelConfig.builder()
            .withIslandCount(islandCount)
            .withMigrationFrequency(migrationFrequency)
            .withCompareGlobalEnabled(compareGlobalEnabled)
            .withReceiveGlobalUpdateFrequency(receiveGlobalUpdateFrequency)
            .build();

    return new IslandAgent<>(
        agentId,
        agentPhases,
        deepCloneSolution(solverScope.getBestSolution()),
        globalState,
        sender,
        receiver,
        config,
        agentRandom,
        solverScope);
  }

  private List<Phase<Solution_>> buildPhasesForAgent() {
    // IslandModelPhaseConfig includes all local search configuration fields,
    // so each island runs the same local search configuration with independent random seeds
    var childConfigPolicy = configPolicy.createChildThreadConfigPolicy(ChildThreadType.MOVE_THREAD);

    // Create a LocalSearchPhaseConfig from the island model's local search fields
    var localSearchConfig = new ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig();
    localSearchConfig.setLocalSearchType(islandModelConfig.getLocalSearchType());
    localSearchConfig.setMoveSelectorConfig(islandModelConfig.getMoveSelectorConfig());
    localSearchConfig.setAcceptorConfig(islandModelConfig.getAcceptorConfig());
    localSearchConfig.setForagerConfig(islandModelConfig.getForagerConfig());
    localSearchConfig.setMoveThreadCount(islandModelConfig.getMoveThreadCount());
    localSearchConfig.setTerminationConfig(islandModelConfig.getTerminationConfig());

    // Build a single LocalSearchPhase from the local search config
    return PhaseFactory.buildPhases(
        List.of(localSearchConfig), childConfigPolicy, bestSolutionRecaller, solverTermination);
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

    private ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig islandModelConfig;
    private int islandCount = IslandModelConfig.DEFAULT_ISLAND_COUNT;
    private int migrationFrequency = IslandModelConfig.DEFAULT_MIGRATION_FREQUENCY;
    private boolean compareGlobalEnabled = true;
    private int receiveGlobalUpdateFrequency =
        IslandModelConfig.DEFAULT_RECEIVE_GLOBAL_UPDATE_FREQUENCY;
    private HeuristicConfigPolicy<Solution_> configPolicy;
    private BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private SolverTermination<Solution_> solverTermination;

    public Builder(
        int phaseIndex, String logIndentation, PhaseTermination<Solution_> phaseTermination) {
      super(phaseIndex, logIndentation, phaseTermination);
    }

    public Builder<Solution_> withIslandModelConfig(
        ai.greycos.solver.core.config.islandmodel.IslandModelPhaseConfig islandModelConfig) {
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

    /**
     * @deprecated Use {@link #withReceiveGlobalUpdateFrequency(int)} instead.
     */
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
