package greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.impl.alns.AlnsPhase;
import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhase;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMigration;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMigrationBatch;
import greycos.solver.core.impl.localsearch.LocalSearchPhase;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.Nullable;
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
  private final RandomSource random;
  private final SolverScope<Solution_> islandScope;
  private final CountDownLatch completionLatch;
  private final AtomicReference<ExecutionState> executionState =
      new AtomicReference<>(ExecutionState.NEW);

  private volatile AgentStatus status = AgentStatus.ALIVE;
  private volatile BitSet aliveBits;
  private volatile int stepsUntilNextMigration;
  private volatile List<IslandGeneticAlgorithmDiagnostics> geneticAlgorithmDiagnostics = List.of();
  private volatile List<IslandRunDiagnostics> islandDiagnostics = List.of();

  public IslandAgent(
      int agentId,
      List<Phase<Solution_>> phases,
      Solution_ initialSolution,
      SharedGlobalState<Solution_> globalState,
      BoundedChannel<AgentUpdate<Solution_>> sender,
      BoundedChannel<AgentUpdate<Solution_>> receiver,
      IslandModelConfig config,
      RandomSource random,
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
    aliveBits = new BitSet(config.getIslandCount());
    aliveBits.set(0, config.getIslandCount());
  }

  @Override
  public void run() {
    if (!executionState.compareAndSet(ExecutionState.NEW, ExecutionState.RUNNING)) {
      return;
    }
    try {
      islandScope.transferWorkingRandomOwnershipToCurrentThread();
      LOGGER.info("Agent {} started with {} phases", agentId, phases.size());

      aliveBits = new BitSet(config.getIslandCount());
      aliveBits.set(0, config.getIslandCount());

      islandScope.setWorkingRandom(random);
      islandScope.setInitialSolution(initialSolution);

      var globalBestUpdater = new GlobalBestUpdater<Solution_>(globalState, agentId);
      for (Phase<Solution_> phase : phases) {
        if (phase instanceof DefaultGeneticAlgorithmPhase<Solution_> geneticAlgorithm) {
          geneticAlgorithm.setMigration(createGeneticAlgorithmMigration(globalBestUpdater));
        }
        if (phase instanceof LocalSearchPhase || phase instanceof AlnsPhase) {
          MigrationTrigger<Solution_> migrationTrigger = new MigrationTrigger<>(this);
          phase.addPhaseLifecycleListener(migrationTrigger);
        }

        phase.addPhaseLifecycleListener(globalBestUpdater);

        if (config.isCompareGlobalEnabled()
            && (phase instanceof LocalSearchPhase || phase instanceof AlnsPhase)) {
          GlobalCompareListener<Solution_> globalCompareListener =
              new GlobalCompareListener<>(globalState, config, agentId);
          phase.addPhaseLifecycleListener(globalCompareListener);
          LOGGER.debug(
              "Agent {} attached global compare listener to phase {}",
              agentId,
              phase.getClass().getSimpleName());
        }
      }

      var islandSolver = (IslandSolver<Solution_>) islandScope.getSolver();
      islandSolver.solvingStarted(islandScope);
      islandSolver.runPhases(islandScope);
      globalBestUpdater.publishCurrentBest(islandScope);
      islandSolver.solvingEnded(islandScope);
      markAsDead();
    } catch (Exception | Error e) {
      try {
        islandScope.getSolver().solvingError(islandScope, e);
      } catch (Throwable cleanupException) {
        if (cleanupException != e) {
          e.addSuppressed(cleanupException);
        }
      }
      LOGGER.error("Agent {} encountered unexpected error", agentId, e);
      markAsDead();
      if (e instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Island agent " + agentId + " failed.", e);
    } finally {
      try {
        captureGeneticAlgorithmDiagnostics();
      } finally {
        executionState.set(ExecutionState.CLOSED);
        completionLatch.countDown();
      }
    }

    awaitAllAgentsAndRelayMigrations();
    LOGGER.info("Agent {} terminated", agentId);
  }

  private void captureGeneticAlgorithmDiagnostics() {
    var diagnostics = new ArrayList<IslandGeneticAlgorithmDiagnostics>();
    var directDiagnostics = new ArrayList<IslandGeneticAlgorithmDiagnostics>();
    var runs = new ArrayList<IslandRunDiagnostics>();
    for (var phase : phases) {
      if (phase instanceof DefaultGeneticAlgorithmPhase<Solution_> geneticAlgorithm) {
        var phaseDiagnostics =
            new IslandGeneticAlgorithmDiagnostics(
                islandScope.getMetricSource(),
                geneticAlgorithm.getPhaseIndex(),
                geneticAlgorithm.getCompletedGenerations(),
                geneticAlgorithm.getMigrationDiagnostics());
        diagnostics.add(phaseDiagnostics);
        directDiagnostics.add(phaseDiagnostics);
      } else if (phase instanceof DefaultIslandModelPhase<Solution_> island) {
        diagnostics.addAll(island.getGeneticAlgorithmMigrationDiagnostics());
        runs.addAll(island.getIslandDiagnostics());
      }
    }
    geneticAlgorithmDiagnostics = List.copyOf(diagnostics);
    runs.addFirst(
        new IslandRunDiagnostics(
            agentId,
            islandScope.getMetricSource(),
            islandScope.getScoreCalculationCount(),
            islandScope.getMoveEvaluationCount(),
            -1L,
            directDiagnostics));
    islandDiagnostics = List.copyOf(runs);
  }

  List<IslandGeneticAlgorithmDiagnostics> getGeneticAlgorithmDiagnostics() {
    return geneticAlgorithmDiagnostics;
  }

  List<IslandRunDiagnostics> getIslandDiagnostics() {
    return islandDiagnostics;
  }

  /** Claims cleanup only when cancellation prevented the agent from entering its lifecycle. */
  public void cancelBeforeStart() {
    if (!executionState.compareAndSet(ExecutionState.NEW, ExecutionState.CLOSED)) {
      return;
    }
    try {
      markAsDead();
      islandScope.getScoreDirector().close();
    } finally {
      completionLatch.countDown();
    }
  }

  private enum ExecutionState {
    NEW,
    RUNNING,
    CLOSED
  }

  /** A separate endpoint preserves population batches instead of applying the archive-best gate. */
  GeneticAlgorithmMigration<Solution_> createGeneticAlgorithmMigration(
      GlobalBestUpdater<Solution_> bestUpdater) {
    return new GeneticAlgorithmMigration<>() {
      private @Nullable Thread owner;

      @Override
      public int frequency() {
        return config.getMigrationFrequency();
      }

      @Override
      public void phaseStarted() {
        if (owner != null) {
          throw new IllegalStateException(
              "The genetic algorithm migration endpoint is already active.");
        }
        owner = Thread.currentThread();
        // A previous local-search phase may have queued a global import. GA only imports the ring.
        islandScope.consumePendingMove();
        pollLatestMigration();
      }

      @Override
      public @Nullable GeneticAlgorithmMigrationBatch<Solution_> exchange(
          long generation, List<GeneticAlgorithmMigrationBatch.Entry<Solution_>> emigrants) {
        requireOwner();
        if (config.getIslandCount() <= 1) {
          return null; // A single island must not compete against its own exported population.
        }
        var batch = new GeneticAlgorithmMigrationBatch<>(agentId, generation, emigrants);
        sender.replace(
            new AgentUpdate<>(
                agentId,
                getCurrentBestSolution(),
                getCurrentBestScore(),
                snapshotAliveBits(),
                batch));
        var update = pollLatestMigration();
        if (update == null || update.getAgentId() == agentId) {
          return null;
        }
        if (update.getPopulationBatch() != null) {
          return update.getPopulationBatch();
        }
        // A local-search or ALNS neighbor exports an incumbent, which competes as one individual.
        var score = update.getMigrantScore();
        if (score.isStructurallyFlawed() || !score.isFullyAssigned()) {
          return null;
        }
        return new GeneticAlgorithmMigrationBatch<>(
            update.getAgentId(),
            0L,
            List.of(
                new GeneticAlgorithmMigrationBatch.Entry<>(
                    SolutionAssignments.capture(
                        islandScope.getScoreDirector().getSolutionDescriptor(),
                        update.getMigrant()),
                    score)));
      }

      @Override
      public void publishBest() {
        requireOwner();
        bestUpdater.publishCurrentBest(islandScope);
      }

      @Override
      public void phaseEnded() {
        if (owner == null) {
          return;
        }
        requireOwner();
        try {
          islandScope.consumePendingMove();
          // Population messages do not outlive the receiving GA phase.
          pollLatestMigration();
        } finally {
          owner = null;
        }
      }

      private void requireOwner() {
        if (owner != Thread.currentThread()) {
          throw new IllegalStateException(
              "The genetic algorithm migration endpoint must run on its active solver thread.");
        }
      }
    };
  }

  private @Nullable AgentUpdate<Solution_> pollLatestMigration() {
    AgentUpdate<Solution_> latest = null;
    // Bound the drain even if a faster neighbor keeps publishing. Production channels hold one
    // item.
    for (int remaining = receiver.capacity(); remaining > 0; remaining--) {
      var update = receiver.tryReceive();
      if (update == null) {
        break;
      }
      latest = update;
    }
    if (latest != null) {
      applyIncomingAliveBits(latest.getAliveBits());
      updateAliveAgentsCount();
    }
    return latest;
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
    if (!migrantInnerScore.isStructurallyFlawed() && comparisonResult > 0) {
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
    if (migrantScore.isStructurallyFlawed()) {
      return null;
    }
    AgentUpdate<Solution_> updateToSend =
        new AgentUpdate<>(agentId, migrant, migrantScore, snapshotAliveBits());
    LOGGER.debug("Agent {} sending migration", agentId);

    boolean sent = sender.replace(updateToSend);
    if (!sent) {
      LOGGER.trace("Agent {} dropped migration update because its channel is closed", agentId);
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
