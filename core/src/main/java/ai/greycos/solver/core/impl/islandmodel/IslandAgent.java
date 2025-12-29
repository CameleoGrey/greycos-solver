package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An island agent that runs phases independently in island model. Each agent maintains its own
 * solution state and participates in periodic migration.
 *
 * <p>Agents run their configured phases sequentially, exchanging best solutions with neighboring
 * agents through migration at configured intervals.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public class IslandAgent<Solution_> implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(IslandAgent.class);

  private final int agentId;
  private final List<Phase<Solution_>> phases;
  private final Solution_ initialSolution;
  private final SharedGlobalState<Solution_> globalState;
  private final BoundedChannel<AgentUpdate<Solution_>> sender;
  private final BoundedChannel<AgentUpdate<Solution_>> receiver;
  private final IslandModelConfig config;
  private final Random random;
  private final SolverScope<Solution_> parentSolverScope;

  // Agent state
  private volatile AgentStatus status = AgentStatus.ALIVE;
  private volatile List<AgentStatus> statusVector;
  private volatile int stepsUntilNextMigration;
  private volatile boolean phasesCompleted = false;

  // Agent's solver scope (isolated from other agents)
  private SolverScope<Solution_> islandScope;

  /**
   * Creates a new island agent.
   *
   * @param agentId unique ID of this agent
   * @param phases phases to run on this island (cloned for each agent)
   * @param initialSolution starting solution for this island
   * @param globalState shared global state for tracking best solution across islands
   * @param sender channel for sending migration messages
   * @param receiver channel for receiving migration messages
   * @param config island model configuration
   * @param random random number generator for this agent
   * @param parentSolverScope parent solver scope to create child scope from
   */
  public IslandAgent(
      int agentId,
      List<Phase<Solution_>> phases,
      Solution_ initialSolution,
      SharedGlobalState<Solution_> globalState,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      IslandModelConfig config,
      Random random,
      SolverScope<Solution_> parentSolverScope) {
    this.agentId = agentId;
    this.phases = Objects.requireNonNull(phases, "Phases cannot be null");
    this.initialSolution =
        Objects.requireNonNull(initialSolution, "Initial solution cannot be null");
    this.globalState = Objects.requireNonNull(globalState, "Global state cannot be null");
    this.sender = Objects.requireNonNull(sender, "Sender channel cannot be null");
    this.receiver = Objects.requireNonNull(receiver, "Receiver channel cannot be null");
    this.config = Objects.requireNonNull(config, "Config cannot be null");
    this.random = Objects.requireNonNull(random, "Random cannot be null");
    this.parentSolverScope =
        Objects.requireNonNull(parentSolverScope, "Parent solver scope cannot be null");
    this.stepsUntilNextMigration = config.getMigrationFrequency();
  }

  /**
   * Runs the agent's phases and participates in migration. This is the main execution method called
   * by the executor service.
   */
  @Override
  public void run() {
    try {
      LOGGER.info("Agent {} started with {} phases", agentId, phases.size());

      // Initialize status vector (all agents start as ALIVE)
      statusVector = new ArrayList<>();
      for (int i = 0; i < config.getIslandCount(); i++) {
        statusVector.add(AgentStatus.ALIVE);
      }

      // Create isolated solver scope for this island
      islandScope = parentSolverScope.createChildThreadSolverScope(ChildThreadType.MOVE_THREAD);
      islandScope.setInitialSolution(initialSolution);

      // Run phases on this island
      for (Phase<Solution_> phase : phases) {
        if (shouldTerminate()) {
          LOGGER.info("Agent {} terminating early due to global termination", agentId);
          break;
        }

        LOGGER.debug("Agent {} running phase: {}", agentId, phase.getClass().getSimpleName());
        // Call solvingStarted to initialize selectors with the island's workingRandom
        phase.solvingStarted(islandScope);
        phase.solve(islandScope);
        phase.solvingEnded(islandScope);
      }

      phasesCompleted = true;
      markAsDead();

      // Continue participating in migration even after phases complete
      // to maintain ring topology
      AgentUpdate<Solution_> pendingMessage = null;
      int roundsWithoutReceivingMessage = 0;
      int maxRoundsWithoutMessage = config.getIslandCount() * 3; // Safety limit

      while (!shouldTerminate()) {
        // Safety check: if we're DEAD and haven't received any message for many rounds,
        // assume all agents are also DEAD and terminate
        if (status == AgentStatus.DEAD
            && roundsWithoutReceivingMessage >= maxRoundsWithoutMessage) {
          LOGGER.info(
              "Agent {} terminating after {} rounds without receiving messages (assuming all agents DEAD)",
              agentId,
              roundsWithoutReceivingMessage);
          break;
        }

        boolean receivedMessage = (pendingMessage != null);
        pendingMessage = performMigrationWithTimeout(pendingMessage);

        if (!receivedMessage && pendingMessage == null) {
          roundsWithoutReceivingMessage++;
        } else {
          roundsWithoutReceivingMessage = 0;
        }

        Thread.yield();
      }

      LOGGER.info("Agent {} terminated", agentId);

    } catch (InterruptedException e) {
      LOGGER.info("Agent {} interrupted", agentId);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOGGER.error("Agent {} encountered unexpected error", agentId, e);
      markAsDead();
    }
  }

  /**
   * Performs migration: sends best solution to next agent and receives from previous agent. Uses
   * alternating send/receive order to prevent deadlock in ring topology.
   */
  private void performMigration() throws InterruptedException {
    // Alternate send/receive order to prevent deadlock
    if (agentId % 2 == 0) {
      // Even agents: send first, then receive
      sendMigration();
      receiveMigration();
    } else {
      // Odd agents: receive first, then send
      receiveMigration();
      sendMigration();
    }

    stepsUntilNextMigration = config.getMigrationFrequency();
  }

  /**
   * Performs migration with timeout for dead agents to prevent deadlock. Dead agents use
   * non-blocking operations to avoid waiting forever.
   *
   * @param pendingMessage message to forward (for DEAD agents), or null (for ALIVE agents)
   * @return message received (for forwarding in next cycle), or null
   */
  private AgentUpdate<Solution_> performMigrationWithTimeout(AgentUpdate<Solution_> pendingMessage)
      throws InterruptedException {
    AgentUpdate<Solution_> receivedMessage;

    // Alternate send/receive order to prevent deadlock
    if (agentId % 2 == 0) {
      // Even agents: send first, then receive
      sendMigrationWithTimeout(pendingMessage);
      receivedMessage = receiveMigrationWithTimeout();
    } else {
      // Odd agents: receive first, then send
      receivedMessage = receiveMigrationWithTimeout();
      sendMigrationWithTimeout(pendingMessage);
    }

    stepsUntilNextMigration = config.getMigrationFrequency();
    return receivedMessage;
  }

  /** Sends current best solution as migrant to next agent in ring. */
  private void sendMigration() throws InterruptedException {
    if (status == AgentStatus.DEAD) {
      // Dead agents just forward any received message
      AgentUpdate<Solution_> receivedUpdate = receiver.tryReceive();
      if (receivedUpdate != null) {
        sender.send(receivedUpdate);
      }
      return;
    }

    Solution_ migrant = getCurrentBestSolution();
    AgentUpdate<Solution_> update =
        new AgentUpdate<>(agentId, deepClone(migrant), new ArrayList<>(statusVector));

    LOGGER.debug("Agent {} sending migration", agentId);
    sender.send(update);
  }

  /**
   * Receives migrant from previous agent in ring. Updates status vector and potentially replaces
   * current solution if migrant is better.
   */
  private void receiveMigration() throws InterruptedException {
    AgentUpdate<Solution_> update = receiver.receive();

    // Update status vector (exclude self)
    for (int i = 0; i < statusVector.size(); i++) {
      if (i != agentId) {
        statusVector.set(i, update.getStatusVector().get(i));
      }
    }

    // Update alive agents count
    updateAliveAgentsCount();

    // If dead, just forward message
    if (status == AgentStatus.DEAD) {
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      return;
    }

    // Integrate migrant if better than current best
    Solution_ migrant = update.getMigrant();
    var migrantInnerScore = calculateScore(migrant);
    var currentInnerScore = getCurrentBestScore();

    if (migrantInnerScore != null && currentInnerScore != null) {
      // Compare raw Score values (extract from InnerScore)
      // Use raw cast to handle wildcard type, similar to SharedGlobalState
      @SuppressWarnings("unchecked")
      var migrantScore = (Score) migrantInnerScore.raw();
      @SuppressWarnings("unchecked")
      var currentScore = (Score) currentInnerScore.raw();
      int comparisonResult = migrantScore.compareTo(currentScore);
      if (comparisonResult > 0) {
        LOGGER.info(
            "Agent {} received better migrant from agent {} (score: {} vs {})",
            agentId,
            update.getAgentId(),
            migrantScore,
            currentScore);
        replaceCurrentSolution(deepClone(migrant));
      } else {
        LOGGER.debug(
            "Agent {} received migrant from agent {} but kept current (score: {} vs {})",
            agentId,
            update.getAgentId(),
            currentScore,
            migrantScore);
      }
    }
  }

  /**
   * Receives migrant with timeout to prevent deadlock when agent is DEAD. Uses non-blocking receive
   * for dead agents.
   *
   * @return received update, or null if no message available (for DEAD agents)
   */
  private AgentUpdate<Solution_> receiveMigrationWithTimeout() throws InterruptedException {
    AgentUpdate<Solution_> update;

    if (status == AgentStatus.DEAD) {
      // Dead agents use non-blocking receive to avoid deadlock
      update = receiver.tryReceive();
      if (update == null) {
        // No message to process, skip this migration cycle
        LOGGER.trace("Agent {} (DEAD) no message to receive", agentId);
        return null;
      }
    } else {
      // Alive agents use blocking receive
      update = receiver.receive();
    }

    // Update status vector (exclude self)
    for (int i = 0; i < statusVector.size(); i++) {
      if (i != agentId) {
        statusVector.set(i, update.getStatusVector().get(i));
      }
    }

    // Update alive agents count
    updateAliveAgentsCount();

    // If dead, just return message for forwarding
    if (status == AgentStatus.DEAD) {
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      return update;
    }

    // Integrate migrant if better than current best
    Solution_ migrant = update.getMigrant();
    var migrantInnerScore = calculateScore(migrant);
    var currentInnerScore = getCurrentBestScore();

    if (migrantInnerScore != null && currentInnerScore != null) {
      // Compare raw Score values (extract from InnerScore)
      // Use raw cast to handle wildcard type, similar to SharedGlobalState
      @SuppressWarnings("unchecked")
      var migrantScore = (Score) migrantInnerScore.raw();
      @SuppressWarnings("unchecked")
      var currentScore = (Score) currentInnerScore.raw();
      int comparisonResult = migrantScore.compareTo(currentScore);
      if (comparisonResult > 0) {
        LOGGER.info(
            "Agent {} received better migrant from agent {} (score: {} vs {})",
            agentId,
            update.getAgentId(),
            migrantScore,
            currentScore);
        replaceCurrentSolution(deepClone(migrant));
      } else {
        LOGGER.debug(
            "Agent {} received migrant from agent {} but kept current (score: {} vs {})",
            agentId,
            update.getAgentId(),
            currentScore,
            migrantScore);
      }
    }

    return update;
  }

  /**
   * Sends migration with proper handling for dead agents.
   *
   * @param messageToSend message to send (for DEAD agents), or null (for ALIVE agents)
   * @return message that was sent, or null if nothing sent
   */
  private AgentUpdate<Solution_> sendMigrationWithTimeout(AgentUpdate<Solution_> messageToSend)
      throws InterruptedException {
    if (status == AgentStatus.DEAD) {
      // Dead agents just forward the received message
      if (messageToSend != null) {
        LOGGER.debug(
            "Agent {} (DEAD) forwarding migration from agent {}",
            agentId,
            messageToSend.getAgentId());
        sender.send(messageToSend);
        return messageToSend;
      }
      // No message to forward
      return null;
    }

    // Alive agents send their best solution
    Solution_ migrant = getCurrentBestSolution();
    AgentUpdate<Solution_> updateToSend =
        new AgentUpdate<>(agentId, deepClone(migrant), new ArrayList<>(statusVector));
    LOGGER.debug("Agent {} sending migration", agentId);
    sender.send(updateToSend);
    return updateToSend;
  }

  /**
   * Returns agent's current best solution.
   *
   * @return current best solution
   */
  private Solution_ getCurrentBestSolution() {
    return islandScope.getBestSolution();
  }

  /**
   * Returns score of agent's current best solution.
   *
   * @return InnerScore wrapping the score of current best solution
   */
  private ai.greycos.solver.core.impl.score.director.InnerScore<?> getCurrentBestScore() {
    return islandScope.getBestScore();
  }

  /**
   * Calculates score of a solution.
   *
   * @param solution solution to score
   * @return InnerScore wrapping the calculated score
   */
  private ai.greycos.solver.core.impl.score.director.InnerScore<?> calculateScore(
      Solution_ solution) {
    if (solution == null) {
      return null;
    }
    // For now, return solution's current score from scope
    // In a full implementation, we would score the specific solution
    return islandScope.calculateScore();
  }

  /**
   * Replaces agent's current solution with a new solution.
   *
   * @param newSolution new solution to use
   */
  private void replaceCurrentSolution(Solution_ newSolution) {
    // Set the new migrant as the working solution
    // The migrant is already a deep clone, so we can use it directly
    islandScope.getScoreDirector().setWorkingSolution(newSolution);
    // Update the best solution with the migrant (clone it to ensure separation)
    islandScope.setBestSolution(islandScope.getScoreDirector().cloneSolution(newSolution));
    // Update the best score to match
    islandScope.setBestScore(islandScope.getScoreDirector().calculateScore());
  }

  /**
   * Performs a deep clone of a solution using Greycos's solution cloning infrastructure.
   *
   * @param solution solution to clone
   * @return deep clone of solution
   */
  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution) {
    if (solution == null) {
      return null;
    }
    // Use Greycos's solution cloner from score director
    return islandScope.getScoreDirector().cloneSolution(solution);
  }

  /** Updates count of alive agents from status vector. */
  private void updateAliveAgentsCount() {
    int aliveCount = 0;
    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        aliveCount++;
      }
    }
    LOGGER.trace("Agent {} sees {} alive agents in status vector", agentId, aliveCount);
  }

  /** Counts the number of alive agents in the status vector. */
  private int countAliveAgents() {
    int count = 0;
    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        count++;
      }
    }
    return count;
  }

  /**
   * Checks if agent should terminate. Agent terminates when: 1. All phases completed 2. AND no
   * alive agents remain (status vector shows all DEAD)
   *
   * @return true if agent should terminate, false otherwise
   */
  private boolean shouldTerminate() {
    if (!phasesCompleted) {
      return false;
    }

    // Check if any agents are still alive
    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        return false;
      }
    }

    return true;
  }

  /** Marks this agent as DEAD and updates status vector. */
  private void markAsDead() {
    status = AgentStatus.DEAD;
    statusVector.set(agentId, AgentStatus.DEAD);
    LOGGER.info("Agent {} marked as DEAD", agentId);
  }

  /**
   * Returns the agent's ID.
   *
   * @return agent ID
   */
  public int getAgentId() {
    return agentId;
  }

  /**
   * Returns the agent's current status.
   *
   * @return current status (ALIVE or DEAD)
   */
  public AgentStatus getStatus() {
    return status;
  }

  /**
   * Returns the agent's solver scope.
   *
   * @return agent's isolated solver scope
   */
  public SolverScope<Solution_> getIslandScope() {
    return islandScope;
  }

  @Override
  public String toString() {
    return "IslandAgent{"
        + "agentId="
        + agentId
        + ", status="
        + status
        + ", phasesCompleted="
        + phasesCompleted
        + ", phaseCount="
        + phases.size()
        + '}';
  }
}
