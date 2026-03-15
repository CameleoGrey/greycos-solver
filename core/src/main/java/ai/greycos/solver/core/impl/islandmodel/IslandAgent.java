package ai.greycos.solver.core.impl.islandmodel;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ai.greycos.solver.core.impl.localsearch.LocalSearchPhase;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Island agent that runs phases independently and participates in periodic migration. Maintains its
 * own solution state and exchanges best solutions with neighboring agents.
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
  private final SolverScope<Solution_> islandScope;
  private final CountDownLatch completionLatch;

  private volatile AgentStatus status = AgentStatus.ALIVE;
  private volatile BitSet aliveBits;
  private volatile int stepsUntilNextMigration;

  public IslandAgent(
      int agentId,
      List<Phase<Solution_>> phases,
      Solution_ initialSolution,
      SharedGlobalState<Solution_> globalState,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      IslandModelConfig config,
      Random random,
      SolverScope<Solution_> islandScope,
      CountDownLatch completionLatch) {
    this.agentId = agentId;
    this.phases = Objects.requireNonNull(phases, "Phases cannot be null");
    this.initialSolution =
        Objects.requireNonNull(initialSolution, "Initial solution cannot be null");
    this.globalState = Objects.requireNonNull(globalState, "Global state cannot be null");
    this.sender = Objects.requireNonNull(sender, "Sender channel cannot be null");
    this.receiver = Objects.requireNonNull(receiver, "Receiver channel cannot be null");
    this.config = Objects.requireNonNull(config, "Config cannot be null");
    this.random = Objects.requireNonNull(random, "Random cannot be null");
    this.islandScope = Objects.requireNonNull(islandScope, "Island solver scope cannot be null");
    this.completionLatch =
        Objects.requireNonNull(completionLatch, "Completion latch cannot be null");
    this.stepsUntilNextMigration = config.getMigrationFrequency();
  }

  @Override
  public void run() {
    try {
      LOGGER.info("Agent {} started with {} phases", agentId, phases.size());

      aliveBits = new BitSet(config.getIslandCount());
      aliveBits.set(0, config.getIslandCount());

      islandScope.setWorkingRandom(random);
      islandScope.setInitialSolution(initialSolution);
      islandScope.getSolver().solvingStarted(islandScope);

      for (Phase<Solution_> phase : phases) {
        LOGGER.debug("Agent {} running phase: {}", agentId, phase.getClass().getSimpleName());

        if (phase instanceof LocalSearchPhase) {
          MigrationTrigger<Solution_> migrationTrigger = new MigrationTrigger<>(this);
          phase.addPhaseLifecycleListener(migrationTrigger);
        }

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

      islandScope.getSolver().solvingEnded(islandScope);
      markAsDead();
    } catch (Exception e) {
      LOGGER.error("Agent {} encountered unexpected error", agentId, e);
      markAsDead();
      throw new IllegalStateException("Island agent " + agentId + " failed.", e);
    } finally {
      completionLatch.countDown();
    }

    awaitAllAgentsAndRelayMigrations();
    LOGGER.info("Agent {} terminated", agentId);
  }

  void checkAndPerformMigration() {
    stepsUntilNextMigration--;

    if (stepsUntilNextMigration <= 0) {
      LOGGER.debug("Agent {} triggering migration", agentId);
      performMigration();
    }
  }

  private AgentUpdate<Solution_> performMigration() {
    sendMigrationNonBlocking();
    var receivedMessage = receiveMigrationNonBlocking();
    stepsUntilNextMigration = config.getMigrationFrequency();
    return receivedMessage;
  }

  private AgentUpdate<Solution_> receiveMigrationNonBlocking() {
    AgentUpdate<Solution_> update;

    if (status == AgentStatus.DEAD) {
      update = receiver.tryReceive();
      if (update == null) {
        return null;
      }
      applyIncomingAliveBits(update.getAliveBits());
      updateAliveAgentsCount();
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      sender.replace(update);
      return update;
    }

    update = receiver.tryReceive();
    if (update == null) {
      return null;
    }

    applyIncomingAliveBits(update.getAliveBits());

    updateAliveAgentsCount();

    if (status == AgentStatus.DEAD) {
      LOGGER.debug("Agent {} (DEAD) forwarding migration", agentId);
      return update;
    }

    Solution_ migrant = update.getMigrant();
    var migrantInnerScore = update.getMigrantScore();
    var currentInnerScore = getCurrentBestScore();

    int comparisonResult = compareInnerScores(migrantInnerScore, currentInnerScore);
    if (comparisonResult > 0) {
      LOGGER.info(
          "Agent {} received better migrant from agent {} (score: {} vs {})",
          agentId,
          update.getAgentId(),
          migrantInnerScore.raw(),
          currentInnerScore.raw());
      scheduleAdoption(migrant, migrantInnerScore);
    } else {
      LOGGER.debug(
          "Agent {} received migrant from agent {} but kept current (score: {} vs {})",
          agentId,
          update.getAgentId(),
          currentInnerScore.raw(),
          migrantInnerScore.raw());
    }

    return update;
  }

  private AgentUpdate<Solution_> sendMigrationNonBlocking() {
    if (status == AgentStatus.DEAD) {
      AgentUpdate<Solution_> receivedUpdate = receiver.tryReceive();
      if (receivedUpdate != null) {
        sender.replace(receivedUpdate);
      }
      return receivedUpdate;
    }

    Solution_ migrant = getCurrentBestSolution();
    var migrantScore = getCurrentBestScore();
    AgentUpdate<Solution_> updateToSend =
        new AgentUpdate<>(agentId, migrant, migrantScore, snapshotAliveBits());
    LOGGER.debug("Agent {} sending migration", agentId);

    boolean sent = sender.replace(updateToSend);
    if (!sent) {
      LOGGER.trace(
          "Agent {} dropped migration update due to concurrent channel contention", agentId);
    }
    return updateToSend;
  }

  private Solution_ getCurrentBestSolution() {
    return islandScope.getBestSolution();
  }

  private InnerScore<?> getCurrentBestScore() {
    var score = islandScope.getBestScore();
    if (score == null) {
      throw new IllegalStateException("Agent " + agentId + " has no current best score.");
    }
    return score;
  }

  private void scheduleAdoption(Solution_ migrant, InnerScore<?> migrantScore) {
    var syncMove = SolutionSyncMove.createMove(islandScope.getScoreDirector(), migrant);
    islandScope.setPendingMoveIfBetter(syncMove, migrantScore, true);
  }

  private BitSet snapshotAliveBits() {
    return (BitSet) aliveBits.clone();
  }

  private void applyIncomingAliveBits(BitSet incomingAliveBits) {
    aliveBits.clear();
    aliveBits.or(incomingAliveBits);
    aliveBits.set(agentId, status == AgentStatus.ALIVE);
  }

  private void awaitAllAgentsAndRelayMigrations() {
    long relayTimeoutMs = Math.max(1L, Math.min(config.getMigrationTimeout(), 250L));
    while (completionLatch.getCount() > 0) {
      try {
        relayMigrationMessage(relayTimeoutMs);
      } catch (InterruptedException e) {
        LOGGER.info("Agent {} interrupted while relaying for peers", agentId);
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void relayMigrationMessage(long timeoutMs) throws InterruptedException {
    AgentUpdate<Solution_> update = receiver.tryReceive(timeoutMs, TimeUnit.MILLISECONDS);
    if (update == null) {
      return;
    }
    applyIncomingAliveBits(update.getAliveBits());
    aliveBits.clear(agentId);
    updateAliveAgentsCount();
    boolean forwarded = sender.send(update, timeoutMs, TimeUnit.MILLISECONDS);
    if (!forwarded) {
      LOGGER.trace("Agent {} dropped relay migration due to full outbound channel", agentId);
    }
  }

  private void updateAliveAgentsCount() {
    int aliveCount = aliveBits.cardinality();
    LOGGER.trace("Agent {} sees {} alive agents in status vector", agentId, aliveCount);
  }

  private void markAsDead() {
    status = AgentStatus.DEAD;
    if (aliveBits != null) {
      aliveBits.clear(agentId);
    }
    LOGGER.info("Agent {} marked as DEAD", agentId);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareInnerScores(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
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
        + ", phaseCount="
        + phases.size()
        + '}';
  }
}
