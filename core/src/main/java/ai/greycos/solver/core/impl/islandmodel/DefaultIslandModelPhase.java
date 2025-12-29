package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.config.phase.PhaseConfig;
import ai.greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import ai.greycos.solver.core.impl.phase.AbstractPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.PhaseFactory;
import ai.greycos.solver.core.impl.phase.PhaseType;
import ai.greycos.solver.core.impl.phase.event.PhaseEventProducerId;
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
 * <p>This phase creates and manages multiple island agents, each running the same phases
 * independently. Agents periodically exchange their best solutions through migration in a ring
 * topology.
 *
 * <p>The island model is an opt-in feature that provides:
 *
 * <ul>
 *   <li>Enhanced solution quality through migration
 *   <li>Near-linear horizontal scaling
 *   <li>Fault tolerance (if one island fails, others continue)
 * </ul>
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class DefaultIslandModelPhase<Solution_> extends AbstractPhase<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIslandModelPhase.class);

  private final List<PhaseConfig<?>> wrappedPhaseConfigList;
  private final int islandCount;
  private final double migrationRate;
  private final int migrationFrequency;
  private final SharedGlobalState<Solution_> globalState;
  private SolverScope<Solution_> solverScope; // Cache for solution cloning
  private final HeuristicConfigPolicy<Solution_> configPolicy;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final SolverTermination<Solution_> solverTermination;

  private DefaultIslandModelPhase(Builder<Solution_> builder) {
    super(builder);
    this.wrappedPhaseConfigList = builder.wrappedPhaseConfigList;
    this.islandCount = builder.islandCount;
    this.migrationRate = builder.migrationRate;
    this.migrationFrequency = builder.migrationFrequency;
    this.globalState = new SharedGlobalState<>();
    this.configPolicy = builder.configPolicy;
    this.bestSolutionRecaller = builder.bestSolutionRecaller;
    this.solverTermination = builder.solverTermination;

    LOGGER.info(
        "DefaultIslandModelPhase created with {} islands, migration frequency: {}",
        islandCount,
        migrationFrequency);
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

      // Cache solver scope for solution cloning and set in global state
      this.solverScope = solverScope;
      globalState.setSolverScope(solverScope);

      // Initialize global state with initial solution
      var initialSolution = solverScope.getBestSolution();
      var innerScore = solverScope.calculateScore();
      globalState.tryUpdate(initialSolution, innerScore.raw());

      // Create island agents and run them
      createAndRunAgents(solverScope);

      // Update solver scope with global best solution
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
    }
  }

  /**
   * Creates island agents and runs them in parallel.
   *
   * @param solverScope the solver scope
   */
  private void createAndRunAgents(SolverScope<Solution_> solverScope) {
    var executor = java.util.concurrent.Executors.newFixedThreadPool(islandCount);
    var random = solverScope.getWorkingRandom();

    LOGGER.info("Creating {} island agents with ring topology...", islandCount);

    // Create channels for ring topology: agent i sends to agent (i+1) mod n
    var channels = new java.util.ArrayList<BoundedChannel<AgentUpdate<Solution_>>>(islandCount);
    for (int i = 0; i < islandCount; i++) {
      channels.add(new BoundedChannel<>(1));
    }

    // Create and submit agents with proper ring connections
    for (int i = 0; i < islandCount; i++) {
      // Agent i receives from channel i, sends to channel (i+1) mod islandCount
      var receiver = channels.get(i);
      var sender = channels.get((i + 1) % islandCount);

      // Rebuild phases for each agent to avoid sharing mutable selector state
      // This is necessary because phases contain selectors (like MimicReplayingEntitySelector)
      // that maintain state during execution, and sharing them across parallel agents causes conflicts
      var agentPhases = buildPhasesForAgent();
      var agent = createAgent(solverScope, random, i, sender, receiver, agentPhases);
      executor.submit(agent);
    }

    executor.shutdown();

    try {
      // Wait for all agents to complete
      executor.awaitTermination(24, java.util.concurrent.TimeUnit.HOURS);
      LOGGER.info("All {} island agents completed", islandCount);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for agents", e);
    }
  }

  /**
   * Creates a single island agent.
   *
   * @param solverScope the solver scope
   * @param random the random number generator
   * @param agentId the agent ID
   * @param agentPhases the phases for this agent (rebuilt)
   * @return the created agent
   */
  private IslandAgent<Solution_> createAgent(
      SolverScope<Solution_> solverScope,
      Random random,
      int agentId,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      List<Phase<Solution_>> agentPhases) {
    var agentRandom = new Random(random.nextLong());
    var config = new IslandModelConfig(islandCount, migrationRate, migrationFrequency);

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

  /**
   * Builds phases for a single island agent.
   * This is necessary because phases contain selectors (like MimicReplayingEntitySelector)
   * that maintain state during execution, and sharing them across parallel agents causes conflicts.
   * Each agent gets its own independent phase instances with separate selector states.
   *
   * @return a new list of phases for this agent
   */
  private List<Phase<Solution_>> buildPhasesForAgent() {
    if (wrappedPhaseConfigList == null || wrappedPhaseConfigList.isEmpty()) {
      return new ArrayList<>();
    }

    // Build phases using child thread config policy for MOVE_THREAD type
    // This ensures each agent gets its own phase instances
    @SuppressWarnings("unchecked")
    List<PhaseConfig> rawPhaseConfigList = (List<PhaseConfig>) (List<?>) wrappedPhaseConfigList;
    var childConfigPolicy = configPolicy.createChildThreadConfigPolicy(ChildThreadType.MOVE_THREAD);
    
    return PhaseFactory.buildPhases(
        rawPhaseConfigList,
        childConfigPolicy,
        bestSolutionRecaller,
        solverTermination);
  }

  /**
   * Performs a deep clone of a solution using Greycos's solution cloning infrastructure.
   *
   * @param solution the solution to clone
   * @return a deep clone of solution
   */
  @SuppressWarnings("unchecked")
  private Solution_ deepCloneSolution(Solution_ solution) {
    if (solution == null) {
      throw new IllegalStateException("Solution to clone cannot be null");
    }
    if (solverScope == null) {
      throw new IllegalStateException("Solver scope not set for solution cloning");
    }
    // Use Greycos's solution cloner from the score director
    return solverScope.getScoreDirector().cloneSolution(solution);
  }

  /**
   * Returns the shared global state. This can be used for testing or monitoring.
   *
   * @return the shared global state
   */
  public SharedGlobalState<Solution_> getGlobalState() {
    return globalState;
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
        + ", migrationRate="
        + migrationRate
        + ", wrappedPhaseConfigs="
        + (wrappedPhaseConfigList != null ? wrappedPhaseConfigList.size() : 0)
        + '}';
  }

  /**
   * Builder for creating DefaultIslandModelPhase instances.
   *
   * @param <Solution_> the solution type
   */
  public static class Builder<Solution_> extends AbstractPhaseBuilder<Solution_> {

    private List<PhaseConfig<?>> wrappedPhaseConfigList;
    private int islandCount = IslandModelConfig.DEFAULT_ISLAND_COUNT;
    private double migrationRate = IslandModelConfig.DEFAULT_MIGRATION_RATE;
    private int migrationFrequency = IslandModelConfig.DEFAULT_MIGRATION_FREQUENCY;
    private HeuristicConfigPolicy<Solution_> configPolicy;
    private BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private SolverTermination<Solution_> solverTermination;

    /**
     * Creates a builder for the island model phase.
     *
     * @param phaseIndex the phase index
     * @param logIndentation the log indentation
     * @param phaseTermination the phase termination criteria
     */
    public Builder(
        int phaseIndex, String logIndentation, PhaseTermination<Solution_> phaseTermination) {
      super(phaseIndex, logIndentation, phaseTermination);
    }

    /**
     * Sets the wrapped phase configs to run on each island.
     *
     * @param wrappedPhaseConfigList the phase configs to run on each island
     * @return this builder
     */
    public Builder<Solution_> withWrappedPhaseConfigs(
        List<PhaseConfig<?>> wrappedPhaseConfigList) {
      this.wrappedPhaseConfigList = wrappedPhaseConfigList;
      return this;
    }

    /**
     * Sets the configuration policy for building phases.
     *
     * @param configPolicy the configuration policy
     * @return this builder
     */
    public Builder<Solution_> withConfigPolicy(HeuristicConfigPolicy<Solution_> configPolicy) {
      this.configPolicy = configPolicy;
      return this;
    }

    /**
     * Sets the best solution recaller for building phases.
     *
     * @param bestSolutionRecaller the best solution recaller
     * @return this builder
     */
    public Builder<Solution_> withBestSolutionRecaller(
        BestSolutionRecaller<Solution_> bestSolutionRecaller) {
      this.bestSolutionRecaller = bestSolutionRecaller;
      return this;
    }

    /**
     * Sets the solver termination for building phases.
     *
     * @param solverTermination the solver termination
     * @return this builder
     */
    public Builder<Solution_> withSolverTermination(
        SolverTermination<Solution_> solverTermination) {
      this.solverTermination = solverTermination;
      return this;
    }

    /**
     * Sets the number of islands.
     *
     * @param islandCount the number of islands
     * @return this builder
     */
    public Builder<Solution_> withIslandCount(int islandCount) {
      this.islandCount = islandCount;
      return this;
    }

    /**
     * Sets the migration rate.
     *
     * @param migrationRate the migration rate
     * @return this builder
     */
    public Builder<Solution_> withMigrationRate(double migrationRate) {
      this.migrationRate = migrationRate;
      return this;
    }

    /**
     * Sets the migration frequency.
     *
     * @param migrationFrequency the number of steps between migrations
     * @return this builder
     */
    public Builder<Solution_> withMigrationFrequency(int migrationFrequency) {
      this.migrationFrequency = migrationFrequency;
      return this;
    }

    @Override
    public Builder<Solution_> enableAssertions(
        ai.greycos.solver.core.config.solver.EnvironmentMode environmentMode) {
      super.enableAssertions(environmentMode);
      return this;
    }

    @Override
    public DefaultIslandModelPhase<Solution_> build() {
      return new DefaultIslandModelPhase<>(this);
    }
  }
}
