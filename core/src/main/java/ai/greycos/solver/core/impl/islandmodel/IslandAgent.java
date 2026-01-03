package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.localsearch.LocalSearchPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Island agent running phases independently with periodic migration.
 *
 * <p>Exchanges best solutions with neighboring agents through migration.
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

  private volatile AgentStatus status = AgentStatus.ALIVE;
  private volatile List<AgentStatus> statusVector;
  private volatile int stepsUntilNextMigration;
  private volatile boolean phasesCompleted = false;

  private SolverScope<Solution_> islandScope;

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

  @Override
  public void run() {
    try {
      LOGGER.info("Agent {} started with {} phases", agentId, phases.size());

      statusVector = new ArrayList<>();
      for (int i = 0; i < config.getIslandCount(); i++) {
        statusVector.add(AgentStatus.ALIVE);
      }

      islandScope = parentSolverScope.createChildThreadSolverScope(ChildThreadType.MOVE_THREAD);
      islandScope.setWorkingRandom(random);
      islandScope.setInitialSolution(initialSolution);

      for (Phase<Solution_> phase : phases) {
        if (shouldTerminate()) {
          LOGGER.info("Agent {} terminating early due to global termination", agentId);
          break;
        }

        LOGGER.debug("Agent {} running phase: {}", agentId, phase.getClass().getSimpleName());

        MigrationTrigger<Solution_> migrationTrigger = new MigrationTrigger<>(this);
        phase.addPhaseLifecycleListener(migrationTrigger);

        GlobalBestUpdater<Solution_> globalBestUpdater =
            new GlobalBestUpdater<>(globalState, agentId);
        phase.addPhaseLifecycleListener(globalBestUpdater);

        if (config.isCompareGlobalEnabled() && phase instanceof LocalSearchPhase) {
          GlobalCompareListener<Solution_> globalCompareListener =
              new GlobalCompareListener<>(globalState, config, agentId);
          phase.addPhaseLifecycleListener(globalCompareListener);
          LOGGER.debug(
              "Agent {} attached global compare listener to phase {}",
              agentId,
              phase.getClass().getSimpleName());
        }

        phase.solvingStarted(islandScope);
        phase.solve(islandScope);
        phase.solvingEnded(islandScope);
      }

      phasesCompleted = true;
      markAsDead();

      AgentUpdate<Solution_> pendingMessage = null;
      int roundsWithoutReceivingMessage = 0;
      int maxRoundsWithoutMessage = config.getIslandCount() * 3;

      while (!shouldTerminate()) {
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

  private void performMigration() throws InterruptedException {
    if (agentId % 2 == 0) {
      sendMigration();
      receiveMigration();
    } else {
      receiveMigration();
      sendMigration();
    }

    stepsUntilNextMigration = config.getMigrationFrequency();
  }

  void checkAndPerformMigration() {
    stepsUntilNextMigration--;

    if (stepsUntilNextMigration <= 0) {
      try {
        LOGGER.debug("Agent {} triggering migration", agentId);
        performMigrationWithTimeout(null);
      } catch (InterruptedException e) {
        LOGGER.info("Agent {} interrupted during migration", agentId);
        Thread.currentThread().interrupt();
      }
    }
  }

  private AgentUpdate<Solution_> performMigrationWithTimeout(AgentUpdate<Solution_> pendingMessage)
      throws InterruptedException {
    AgentUpdate<Solution_> receivedMessage;

    if (agentId % 2 == 0) {
      sendMigrationWithTimeout(pendingMessage);
      receivedMessage = receiveMigrationWithTimeout();
    } else {
      receivedMessage = receiveMigrationWithTimeout();
      sendMigrationWithTimeout(pendingMessage);
    }

    stepsUntilNextMigration = config.getMigrationFrequency();
    return receivedMessage;
  }

  private void sendMigration() throws InterruptedException {
    if (status == AgentStatus.DEAD) {
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

  private void receiveMigration() throws InterruptedException {
    AgentUpdate<Solution_> update = receiver.receive();

    for (int i = 0; i < statusVector.size(); i++) {
      if (i != agentId) {
        statusVector.set(i, update.getStatusVector().get(i));
      }
    }

    updateAliveAgentsCount();

    if (status == AgentStatus.DEAD) {
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      return;
    }

    Solution_ migrant = update.getMigrant();
    var migrantScore = islandScope.getScoreDirector().getSolutionDescriptor().getScore(migrant);
    var currentScore =
        islandScope.getScoreDirector().getSolutionDescriptor().getScore(getCurrentBestSolution());

    if (migrantScore != null && currentScore != null) {
      @SuppressWarnings("unchecked")
      var migrantScoreCast = (Score) migrantScore;
      @SuppressWarnings("unchecked")
      var currentScoreCast = (Score) currentScore;
      int comparisonResult = migrantScoreCast.compareTo(currentScoreCast);
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

  private AgentUpdate<Solution_> receiveMigrationWithTimeout() throws InterruptedException {
    AgentUpdate<Solution_> update;

    if (status == AgentStatus.DEAD) {
      return null;
    }

    update = receiver.tryReceive(config.getMigrationTimeout(), TimeUnit.MILLISECONDS);
    if (update == null) {
      LOGGER.trace("Agent {} timeout waiting for migration message", agentId);
      return null;
    }

    for (int i = 0; i < statusVector.size(); i++) {
      if (i != agentId) {
        statusVector.set(i, update.getStatusVector().get(i));
      }
    }

    updateAliveAgentsCount();

    if (status == AgentStatus.DEAD) {
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      return update;
    }

    Solution_ migrant = update.getMigrant();
    var migrantInnerScore = calculateScore(migrant);
    var currentInnerScore = getCurrentBestScore();

    if (migrantInnerScore != null && currentInnerScore != null) {
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

  private AgentUpdate<Solution_> sendMigrationWithTimeout(AgentUpdate<Solution_> messageToSend)
      throws InterruptedException {
    if (status == AgentStatus.DEAD) {
      return null;
    }

    Solution_ migrant = getCurrentBestSolution();
    AgentUpdate<Solution_> updateToSend =
        new AgentUpdate<>(agentId, deepClone(migrant), new ArrayList<>(statusVector));
    LOGGER.debug("Agent {} sending migration", agentId);

    boolean sent = sender.send(updateToSend, config.getMigrationTimeout(), TimeUnit.MILLISECONDS);
    if (!sent) {
      LOGGER.warn("Agent {} failed to send migration within timeout", agentId);
    }
    return updateToSend;
  }

  private Solution_ getCurrentBestSolution() {
    return islandScope.getBestSolution();
  }

  private InnerScore<?> getCurrentBestScore() {
    return islandScope.getBestScore();
  }

  private InnerScore<?> calculateScore(Solution_ solution) {
    if (solution == null) {
      return null;
    }
    var score = islandScope.getScoreDirector().getSolutionDescriptor().getScore(solution);
    if (score == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    var scoreCast = (Score) score;
    return InnerScore.fullyAssigned(scoreCast);
  }

  private void replaceCurrentSolution(Solution_ newSolution) {
    islandScope.getScoreDirector().setWorkingSolution(newSolution);
    islandScope.setBestSolution(islandScope.getScoreDirector().cloneSolution(newSolution));
    var newBestScore = islandScope.getScoreDirector().calculateScore();
    islandScope.setBestScore(newBestScore);
    @SuppressWarnings("unchecked")
    var scoreToSet = (Score) newBestScore.raw();
    islandScope.getSolutionDescriptor().setScore(newSolution, scoreToSet);
  }

  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution) {
    if (solution == null) {
      return null;
    }
    return islandScope.getScoreDirector().cloneSolution(solution);
  }

  private void updateAliveAgentsCount() {
    int aliveCount = 0;
    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        aliveCount++;
      }
    }
    LOGGER.trace("Agent {} sees {} alive agents in status vector", agentId, aliveCount);
  }

  private int countAliveAgents() {
    int count = 0;
    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        count++;
      }
    }
    return count;
  }

  private boolean shouldTerminate() {
    if (!phasesCompleted) {
      return false;
    }

    for (AgentStatus status : statusVector) {
      if (status == AgentStatus.ALIVE) {
        return false;
      }
    }

    return true;
  }

  private void markAsDead() {
    status = AgentStatus.DEAD;
    statusVector.set(agentId, AgentStatus.DEAD);
    LOGGER.info("Agent {} marked as DEAD", agentId);
  }

  public int getAgentId() {
    return agentId;
  }

  public AgentStatus getStatus() {
    return status;
  }

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
