package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmStepLoggingMode;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.TerminationGraphAccess;

/**
 * Population search with immutable genomes and retained evaluation workspaces. Optional bounded
 * workers score candidates while the coordinator owns population admission and best publication.
 */
public final class DefaultGeneticAlgorithmPhase<Solution_> extends AbstractPhase<Solution_> {
  private final GeneticAlgorithmPhaseConfig config;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final Function<RandomSource, GeneticAlgorithmLocalImprovementMoves<Solution_>>
      localImprovementMovesFactory;
  private final ThreadFactory evaluatorThreadFactory;
  // Keep gauge backing objects reachable after the phase; replace them on every run.
  private GeneticAlgorithmMetrics<Solution_> metrics;
  private GeneticAlgorithmMigration<Solution_> migration;
  private long exportedBatches;
  private long exportedEntries;
  private long receivedBatches;
  private long receivedEntries;
  private long evaluatedEntries;
  private long admittedEntries;
  private long rejectedEntries;
  private long committedBatches;
  private long completedGenerations;
  private boolean workspaceChangedOutsideStep;
  private GeneticAlgorithmEvaluatorPool.Diagnostics evaluatorDiagnostics;
  private volatile Supplier<GeneticAlgorithmEvaluatorPool.Diagnostics> liveEvaluatorDiagnostics;

  private RunDiagnostics runDiagnostics;
  private Consumer<LogicalAttempt> logicalAttemptObserver;

  /**
   * Detached counters for one invocation. Search time includes seeding and evaluator lifecycle;
   * coordinator materializations count pooled transitions only, excluding serial seeding.
   */
  public record RunDiagnostics(
      long completedAttempts,
      long evaluatedOffspring,
      long seedingNanos,
      long searchNanos,
      long coordinatorMaterializations,
      boolean deferredMaterializationUsed,
      String eagerReason) {}

  public RunDiagnostics getRunDiagnostics() {
    return runDiagnostics;
  }

  /** Test observer receives immutable list assignments and scalar logical state only. */
  record LogicalAttempt(
      int stepIndex,
      boolean seeding,
      long generation,
      long candidateId,
      long firstParentId,
      long secondParentId,
      long nativeId,
      boolean crossed,
      GeneticAlgorithmMutationType mutationType,
      String mutationGroup,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      int changedAssignmentCount,
      InnerScore<?> beforeScore,
      InnerScore<?> bestBeforeScore,
      InnerScore<?> candidateScore,
      InnerScore<?> score,
      boolean bestScoreImproved,
      GeneticAlgorithmListSnapshot assignments) {}

  void setLogicalAttemptObserver(Consumer<LogicalAttempt> observer) {
    logicalAttemptObserver = observer;
  }

  /** Final pool counters remain available after workers have closed; null means no pool started. */
  public GeneticAlgorithmEvaluatorPool.Diagnostics getEvaluatorDiagnostics() {
    var live = liveEvaluatorDiagnostics;
    return live == null ? evaluatorDiagnostics : live.get();
  }

  public long getCompletedGenerations() {
    return completedGenerations;
  }

  /** Immutable counters; exported batches count calls to the owning island endpoint. */
  public record MigrationDiagnostics(
      long exportedBatches,
      long exportedEntries,
      long receivedBatches,
      long receivedEntries,
      long evaluatedEntries,
      long admittedEntries,
      long rejectedEntries,
      long committedBatches) {}

  public MigrationDiagnostics getMigrationDiagnostics() {
    return new MigrationDiagnostics(
        exportedBatches,
        exportedEntries,
        receivedBatches,
        receivedEntries,
        evaluatedEntries,
        admittedEntries,
        rejectedEntries,
        committedBatches);
  }

  private DefaultGeneticAlgorithmPhase(Builder<Solution_> builder) {
    super(builder);
    config = builder.config.copyConfig();
    bestSolutionRecaller = builder.bestSolutionRecaller;
    localImprovementMovesFactory = builder.localImprovementMovesFactory;
    evaluatorThreadFactory = builder.evaluatorThreadFactory;
  }

  /** Attaches the owning island transport; the endpoint never owns the evaluation workspace. */
  public void setMigration(GeneticAlgorithmMigration<Solution_> migration) {
    this.migration = migration;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.GENETIC_ALGORITHM;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::geneticAlgorithm;
  }

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    solveTyped(solverScope);
  }

  private <Score_ extends Score<Score_>> void solveTyped(SolverScope<Solution_> solverScope) {
    var scope = new GeneticAlgorithmPhaseScope<>(solverScope, phaseIndex);
    metrics = new GeneticAlgorithmMetrics<>();
    exportedBatches = exportedEntries = receivedBatches = receivedEntries = 0;
    evaluatedEntries = admittedEntries = rejectedEntries = committedBatches = 0;
    completedGenerations = 0;
    workspaceChangedOutsideStep = false;
    evaluatorDiagnostics = null;
    runDiagnostics = null;
    liveEvaluatorDiagnostics = null;
    // Backend changes must precede capturing phase-relative counters and the workspace director.
    solverScope.getSolver().prepareForPhase(scope);
    Throwable originalFailure = null;
    GeneticAlgorithmLocalImprovementMoves<Solution_> localMoves = null;
    try {
      if (migration != null) migration.phaseStarted();
      phaseStarted(scope);
      assertWorkingSolutionInitialized(scope);
      InnerScoreDirector<Solution_, Score_> director = solverScope.getScoreDirector();
      var initialScore = director.calculateScore();
      var workspace = new GeneticAlgorithmWorkspace<>(director, initialScore);
      scope.getLastCompletedStepScope().setScore(initialScore);
      metrics.phaseStarted(scope);
      var operators =
          new GeneticAlgorithmOperators<>(workspace.slots(), workspace.listModel(), config);
      if (!operators.hasMovableSlots()) {
        scope.setPopulationSize(1, 1);
        scope.setTerminationReason("no movable assignments");
      } else {
        if (localImprovementMovesFactory != null) {
          localMoves = localImprovementMovesFactory.apply(solverScope.getWorkingRandom());
          localMoves.initialize(workspace);
          localMoves.solvingStarted(solverScope);
          localMoves.phaseStarted(scope);
        }
        new Search<>(scope, workspace, operators, localMoves).run();
      }
    } catch (RuntimeException | Error failure) {
      originalFailure = failure;
      throw failure;
    } finally {
      finishPhase(scope, localMoves, originalFailure);
    }
    logger.info(
        "{}Genetic Algorithm phase ({}) ended: time spent ({}), best score ({}), attempts ({}),"
            + " generations ({}), population ({}, distinct {}), local probes ({}), local accepted ({}), reason ({}).",
        logIndentation,
        phaseIndex,
        scope.calculateSolverTimeMillisSpentUpToNow(),
        scope.getBestScore(),
        scope.getNextStepIndex(),
        scope.getGeneration(),
        scope.getPopulationSize(),
        scope.getDistinctPopulationSize(),
        scope.getLocalImprovementProbeCount(),
        scope.getLocalImprovementAcceptedCount(),
        scope.getTerminationReason());
  }

  private void logStep(GeneticAlgorithmStepScope<Solution_> step) {
    if (!logger.isDebugEnabled()
        || (config.getStepLoggingMode() == GeneticAlgorithmStepLoggingMode.BEST_SCORE_IMPROVED
            && !step.getBestScoreImproved())) {
      return;
    }
    var scope = step.getPhaseScope();
    var islandSuffix = "";
    for (var tag : scope.getSolverScope().getMonitoringTags()) {
      if (tag.getKey().equals("island.id") && !tag.getValue().equals("root")) {
        islandSuffix = ", island (" + tag.getValue() + ")";
        break;
      }
    }
    // Only completed attempts are logged. Local probes share their outer attempt's best flag;
    // migration and interrupted attempts may publish best solutions without completing a step.
    logger.debug(
        "{}    Genetic Algorithm step ({}), phase ({}), generation ({}), time spent ({}), score ({}),"
            + " {} best score ({}), candidate score ({}), candidate ({}), parents ({}, {}),"
            + " mutation ({}, {}), outcome ({}), admitted ({}), changed ({}){}.",
        logIndentation,
        step.getStepIndex(),
        phaseIndex,
        step.getGeneration(),
        scope.calculateSolverTimeMillisSpentUpToNow(),
        step.getScore().raw(),
        step.getBestScoreImproved() ? "new" : "   ",
        scope.getBestScore().raw(),
        step.getCandidateScore() == null ? null : step.getCandidateScore().raw(),
        step.getCandidateId(),
        step.getFirstParentId(),
        step.getSecondParentId(),
        step.getMutationType(),
        step.getMutationGroup(),
        step.getOutcome(),
        step.isAdmitted(),
        step.getChangedAssignmentCount(),
        islandSuffix);
  }

  private void finishPhase(
      GeneticAlgorithmPhaseScope<Solution_> scope,
      GeneticAlgorithmLocalImprovementMoves<Solution_> localMoves,
      Throwable originalFailure) {
    var cleanup = new ArrayList<Runnable>();
    if (localMoves != null) {
      cleanup.add(() -> localMoves.phaseEnded(scope));
      cleanup.add(() -> localMoves.solvingEnded(scope.getSolverScope()));
    }
    cleanup.add(scope::endingNow);
    // Failed probes may leave deferred undo updates or uncertain state. Keep the last stable step
    // metrics instead of sampling unverified constraint totals during exceptional teardown.
    if (originalFailure == null || (localMoves == null && config.getEvaluatorThreadCount() == 0)) {
      cleanup.add(() -> metrics.phaseEnded(scope, !workspaceChangedOutsideStep));
    }
    cleanup.add(() -> phaseEnded(scope));
    if (migration != null) cleanup.add(migration::phaseEnded);
    runCleanup(originalFailure, cleanup);
  }

  private static void runCleanup(Throwable originalFailure, List<Runnable> actions) {
    Throwable failure = originalFailure;
    for (Runnable action : actions) {
      try {
        action.run();
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) failure = cleanupFailure;
        else if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
    }
    if (originalFailure == null) {
      if (failure instanceof RuntimeException exception) throw exception;
      if (failure instanceof Error error) throw error;
    }
  }

  private record Individual<Score_ extends Score<Score_>>(
      GeneticAlgorithmGenome genome, InnerScore<Score_> score, long id) {}

  /** Run-local state is deliberately never retained by the reusable phase object. */
  private final class Search<Score_ extends Score<Score_>> {
    private final GeneticAlgorithmPhaseScope<Solution_> scope;
    private final GeneticAlgorithmWorkspace<Solution_, Score_> workspace;
    private final GeneticAlgorithmOperators<Solution_> operators;
    private final GeneticAlgorithmLocalImprovementMoves<Solution_> localMoves;
    private final GeneticAlgorithmMigrationCodec<Solution_, Score_> migrationCodec;
    private final int[] logicalOldOwners;
    private final int[] logicalNewOwners;
    private final int[] logicalOldIndexes;
    private final int[] logicalNewIndexes;
    private final List<Individual<Score_>> population = new ArrayList<>();
    private final List<Individual<Score_>> winners = new ArrayList<>();
    private final GeneticAlgorithmGenomeIndex<Individual<Score_>> populationIndex =
        new GeneticAlgorithmGenomeIndex<>();
    private final GeneticAlgorithmGenomeIndex<Individual<Score_>> winnerIndex =
        new GeneticAlgorithmGenomeIndex<>();
    private final GeneticAlgorithmPopulationDiversity populationDiversity =
        new GeneticAlgorithmPopulationDiversity();
    private final Comparator<Individual<Score_>> ranking =
        Comparator.<Individual<Score_>, InnerScore<Score_>>comparing(Individual::score)
            .reversed()
            .thenComparingLong(Individual::id);
    private long nextId = 1;
    private GeneticAlgorithmGenome pendingChild;
    private long firstParentId;
    private long secondParentId;
    private boolean crossed;
    private GeneticAlgorithmEvaluatorPool<Solution_, Score_> evaluatorPool;
    private boolean coordinatorScoreDirty;
    private long pooledAttemptCount;
    private GeneticAlgorithmGenome logicalGenome;
    private InnerScore<Score_> logicalScore;
    private boolean deferredMaterialization;
    private boolean deferredMaterializationUsed;
    private String eagerReason = "serial evaluation";
    private long coordinatorMaterializations;
    private long evaluatedOffspring;
    private long seedingNanos;
    private long searchStartedNanos;

    private Search(
        GeneticAlgorithmPhaseScope<Solution_> scope,
        GeneticAlgorithmWorkspace<Solution_, Score_> workspace,
        GeneticAlgorithmOperators<Solution_> operators,
        GeneticAlgorithmLocalImprovementMoves<Solution_> localMoves) {
      this.scope = scope;
      this.workspace = workspace;
      this.operators = operators;
      this.localMoves = localMoves;
      int valueCount =
          config.getEvaluatorThreadCount() == 0 || workspace.listModel() == null
              ? 0
              : workspace.listModel().valueCount();
      logicalOldOwners = new int[valueCount];
      logicalNewOwners = new int[valueCount];
      logicalOldIndexes = new int[valueCount];
      logicalNewIndexes = new int[valueCount];
      migrationCodec =
          migration != null && config.getMigrationRate() > 0.0
              ? new GeneticAlgorithmMigrationCodec<>(workspace, scope.getScoreDirector())
              : null;
    }

    private void run() {
      searchStartedNanos = System.nanoTime();
      try {
        runSearch();
      } finally {
        long elapsed = System.nanoTime() - searchStartedNanos;
        runDiagnostics =
            new RunDiagnostics(
                scope.getNextStepIndex(),
                evaluatedOffspring,
                seedingNanos == 0 ? elapsed : seedingNanos,
                elapsed,
                coordinatorMaterializations,
                deferredMaterializationUsed,
                eagerReason);
      }
    }

    private void runSearch() {
      var initial = new Individual<>(workspace.genome(), workspace.score(), 0L);
      addPopulationMember(initial);
      while (population.size() < config.getPopulationSize() && !stopped()) {
        var winner =
            attempt(
                true,
                initial,
                () ->
                    operators.sampleSeed(
                        initial.genome(), scope.getWorkingRandom().moveIteratorUsage()));
        if (winner == null) return;
        addPopulationMember(winner);
      }
      seedingNanos = System.nanoTime() - searchStartedNanos;
      if (config.getEvaluatorThreadCount() > 0) {
        if (!stopped()) runPooled();
        return;
      }
      while (!stopped()) {
        sortPopulation();
        clearWinners();
        pendingChild = null;
        long generation = scope.getGeneration() + 1;
        for (int i = 0; i < population.size(); i++) {
          if (stopped()) return;
          var winner = attempt(false, null, () -> nextChild());
          if (winner == null) return;
          addWinner(winner);
        }
        population.clear();
        population.addAll(winners);
        rebuildPopulationIndex();
        clearWinners();
        // An unused second child of an odd population is neither mutated nor evaluated.
        pendingChild = null;
        scope.setGeneration(generation);
        completedGenerations = generation;
        updatePopulationDiagnostics();
        if (!migrateAtGenerationBoundary()) return;
      }
    }

    /**
     * The proposal stream is separate from step callbacks and is split once, before any workers
     * start. Worker count and completion order therefore cannot change genetic decisions.
     */
    private void runPooled() {
      logicalGenome = workspace.genome();
      logicalScore = workspace.score();
      eagerReason = eagerMaterializationReason();
      deferredMaterialization = eagerReason == null;
      var proposalRandom = ((DefaultRandomSource) scope.getWorkingRandom()).moveRandom().split();
      evaluatorPool =
          evaluatorThreadFactory == null
              ? new GeneticAlgorithmEvaluatorPool<>(
                  scope.getScoreDirector(),
                  workspace,
                  scope.getSolverScope(),
                  config.getEvaluatorThreadCount())
              : new GeneticAlgorithmEvaluatorPool<>(
                  scope.getScoreDirector(),
                  workspace,
                  scope.getSolverScope(),
                  config.getEvaluatorThreadCount(),
                  evaluatorThreadFactory);
      metrics.setBeforeConstraintSampling(this::verifyCoordinatorScore);
      liveEvaluatorDiagnostics = evaluatorPool::getDiagnostics;
      Throwable originalFailure = null;
      try {
        if (!evaluatorPool.start(this::stopped)) return;
        while (!stopped()) {
          sortPopulation();
          clearWinners();
          pendingChild = null;
          int preparedCount = 0;
          var pending = new ArrayDeque<PreparedAttempt>(config.getEvaluatorThreadCount());
          PreparedAttempt dependencyBarrier = null;
          while (preparedCount < population.size() || !pending.isEmpty()) {
            while (dependencyBarrier == null
                && pending.size() < config.getEvaluatorThreadCount()
                && preparedCount < population.size()) {
              if (stopped()) return;
              var prepared = prepareAttempt(proposalRandom);
              preparedCount++;
              boolean dependency = prepareEvaluation(prepared, pending);
              pending.addLast(prepared);
              if (dependency) dependencyBarrier = prepared;
            }
            if (stopped()) return;
            var prepared = pending.getFirst();
            var result = awaitEvaluation(prepared);
            if (stopped()) return;
            var winner = attemptPooled(prepared, result);
            if (winner == null) return;
            addWinner(winner);
            pooledAttemptCount++;
            if (pooledAttemptCount % 100 == 0) verifyCoordinatorScore();
            pending.removeFirst();
            if (dependencyBarrier == prepared) dependencyBarrier = null;
          }
          verifyCoordinatorScore();
          population.clear();
          population.addAll(winners);
          rebuildPopulationIndex();
          clearWinners();
          pendingChild = null;
          scope.setGeneration(scope.getGeneration() + 1);
          completedGenerations = scope.getGeneration();
          updatePopulationDiagnostics();
        }
      } catch (RuntimeException | Error failure) {
        originalFailure = failure;
        throw failure;
      } finally {
        var cleanup = new ArrayList<Runnable>();
        var progress = evaluatorPool.getDiagnostics();
        boolean abort =
            originalFailure != null
                || Thread.currentThread().isInterrupted()
                || progress.submittedCount() > progress.consumedCount();
        cleanup.add(abort ? evaluatorPool::abort : evaluatorPool::close);
        cleanup.add(evaluatorPool::transferCalculationCount);
        cleanup.add(
            () -> {
              evaluatorDiagnostics = evaluatorPool.getDiagnostics();
              liveEvaluatorDiagnostics = null;
            });
        // A failed setter, scorer or callback may have left uncertain state. Never replace that
        // initiating failure with speculative verification of the coordinator graph.
        if (originalFailure == null) cleanup.add(this::verifyCoordinatorScore);
        cleanup.add(() -> metrics.setBeforeConstraintSampling(() -> {}));
        runCleanup(originalFailure, cleanup);
      }
    }

    private final class PreparedAttempt {
      private final GeneticAlgorithmGenome genome;
      private final long id;
      private final long firstParent;
      private final long secondParent;
      private final boolean didCross;
      private final GeneticAlgorithmMutationType mutationType;
      private final String mutationGroup;
      private final Individual<Score_> nativeIndividual;
      private GeneticAlgorithmEvaluatorPool.Ticket<Score_> ticket;

      private PreparedAttempt(
          GeneticAlgorithmOperators.Mutation mutation, Individual<Score_> nativeIndividual) {
        genome = mutation.genome();
        id = nextId++;
        firstParent = firstParentId;
        secondParent = secondParentId;
        didCross = crossed;
        mutationType = mutation.type();
        mutationGroup = mutation.group();
        this.nativeIndividual = nativeIndividual;
      }
    }

    private PreparedAttempt prepareAttempt(RandomGenerator random) {
      var child = nextChild(random);
      var mutation = operators.mutate(child, random);
      var nativeIndividual =
          population.get(
              GeneticAlgorithmOperators.selectRankedIndex(
                  population.size(), config.getPBestRate(), false, random));
      return new PreparedAttempt(mutation, nativeIndividual);
    }

    /**
     * An unresolved equal fresh child, or the current workspace behind unresolved fresh children,
     * is a dependency barrier. Admission and validity must resolve before deciding whether this
     * proposal is a cached duplicate. The bounded equality scan also avoids hashing mutable values.
     */
    private boolean prepareEvaluation(
        PreparedAttempt prepared, Iterable<PreparedAttempt> preceding) {
      if (cached(prepared.genome) != null) return false;
      boolean hasFreshPredecessor = false;
      for (var previous : preceding) {
        if (previous.ticket == null) continue;
        hasFreshPredecessor = true;
        if (previous.genome.equals(prepared.genome)) return true;
      }
      if (prepared.genome.equals(logicalGenome)) return hasFreshPredecessor;
      prepared.ticket = evaluatorPool.submit(prepared.id, prepared.genome);
      return false;
    }

    private GeneticAlgorithmEvaluatorPool.Result<Score_> awaitEvaluation(PreparedAttempt prepared) {
      if (prepared.genome.equals(logicalGenome) || cached(prepared.genome) != null) {
        if (prepared.ticket != null) {
          throw new IllegalStateException(
              "A genetic algorithm evaluator job became a duplicate before ordered commitment.");
        }
        return null;
      }
      if (prepared.ticket == null) {
        prepared.ticket = evaluatorPool.submit(prepared.id, prepared.genome);
      }
      // The caller checks termination before and after this wait. Repeat the check after each
      // timed-out poll, without traversing the termination tree twice before the first poll.
      while (true) {
        try {
          var result = evaluatorPool.poll(prepared.ticket, 10L);
          evaluatorPool.transferCalculationCount();
          if (result != null) return result;
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return null;
        }
        if (stopped()) return null;
      }
    }

    private Individual<Score_> attemptPooled(
        PreparedAttempt prepared, GeneticAlgorithmEvaluatorPool.Result<Score_> result) {
      var step = new GeneticAlgorithmStepScope<>(scope);
      step.setGeneration(scope.getGeneration() + 1);
      step.setBeforeScore(logicalScore);
      step.setBestBeforeScore(scope.getBestScore());
      step.setCandidateId(prepared.id);
      step.setParentIds(prepared.firstParent, prepared.secondParent);
      step.setCrossed(prepared.didCross);
      step.setMutationType(prepared.mutationType);
      step.setMutationGroup(prepared.mutationGroup);
      step.setNativeId(prepared.nativeIndividual.id());
      var randomSource = (DefaultRandomSource) scope.getWorkingRandom();
      var previousRandom = randomSource.moveRandom().getDelegate();
      boolean lifecycleCompleted = false;
      boolean credited = false;
      boolean typeCredited = false;
      Throwable attemptFailure = null;
      try {
        ensureObservationCompatibility();
        stepStarted(step);
        if (stopped()) return null;
        var candidate = evaluatePooled(step, prepared, result);
        boolean admitted =
            candidate != null
                && candidate.score().compareTo(prepared.nativeIndividual.score()) >= 0;
        step.setAdmitted(admitted);
        var winner = admitted ? candidate : prepared.nativeIndividual;
        scope.getSolverScope().addMoveEvaluationCount(1);
        credited = true;
        if (scope.getSolverScope().isMetricEnabled(SolverMetric.MOVE_COUNT_PER_TYPE)) {
          scope.getSolverScope().addMoveEvaluationCountPerType(step.getMoveTypeDescription(), 1);
          typeCredited = true;
        }
        ensureObservationCompatibility();
        metrics.record(step);
        stepEnded(step);
        workspaceChangedOutsideStep = false;
        ensureObservationCompatibility();
        lifecycleCompleted = true;
        commitAttempt(step);
        observeLogicalAttempt(step, logicalGenome);
        SolverMetricSamples.publishIslandStep(scope.getSolverScope(), step);
        logStep(step);
        return winner;
      } catch (RuntimeException | Error failure) {
        attemptFailure = failure;
        throw failure;
      } finally {
        if (!lifecycleCompleted) {
          var cleanup = new ArrayList<Runnable>();
          if (credited) cleanup.add(() -> scope.getSolverScope().addMoveEvaluationCount(-1));
          if (typeCredited) {
            cleanup.add(
                () -> {
                  scope
                      .getSolverScope()
                      .addMoveEvaluationCountPerType(step.getMoveTypeDescription(), -1);
                  scope
                      .getSolverScope()
                      .getMoveEvaluationCountPerType()
                      .remove(step.getMoveTypeDescription(), 0L);
                });
          }
          cleanup.add(() -> metrics.restoreStepCounts(scope.getLastCompletedStepScope()));
          cleanup.add(() -> randomSource.restoreState(previousRandom));
          runCleanup(attemptFailure, cleanup);
        }
      }
    }

    private Individual<Score_> evaluatePooled(
        GeneticAlgorithmStepScope<Solution_> step,
        PreparedAttempt prepared,
        GeneticAlgorithmEvaluatorPool.Result<Score_> result) {
      InnerScore<Score_> candidateScore;
      if (prepared.genome.equals(logicalGenome)) {
        step.setOutcome(GeneticAlgorithmOutcome.NO_CHANGE);
        candidateScore = logicalScore;
        step.setScore(logicalScore);
      } else {
        var duplicate = cached(prepared.genome);
        if (duplicate != null) {
          step.setOutcome(GeneticAlgorithmOutcome.DUPLICATE);
          candidateScore = duplicate.score();
          step.setScore(logicalScore);
        } else {
          Objects.requireNonNull(
              result, "A fresh genetic algorithm candidate needs an evaluator result.");
          if (result.workId() != prepared.id) {
            throw new IllegalStateException(
                "The genetic algorithm evaluator result work ID (%d) differs from its attempt ID (%d)."
                    .formatted(result.workId(), prepared.id));
          }
          if (!result.valid()) {
            step.setOutcome(GeneticAlgorithmOutcome.INVALID);
            step.setChangedAssignmentCount(
                result.changedAssignmentCount() == 0
                    ? 0
                    : logicalChangedAssignmentCount(prepared.genome));
            step.setScore(logicalScore);
            return null;
          }
          candidateScore = Objects.requireNonNull(result.score());
          step.setScore(candidateScore);
          if (deferredMaterialization) {
            deferredMaterializationUsed = true;
            step.setChangedAssignmentCount(logicalChangedAssignmentCount(prepared.genome));
            logicalGenome = prepared.genome;
            logicalScore = candidateScore;
          } else {
            var transition = transitionCoordinator(prepared.genome);
            step.setChangedAssignmentCount(transition.changedAssignmentCount());
            logicalGenome = prepared.genome;
            logicalScore = candidateScore;
            scoreMaterializedCandidate(step);
          }
          step.setOutcome(GeneticAlgorithmOutcome.EVALUATED);
          if (candidateScore.compareTo(scope.<Score_>getBestScore()) > 0) {
            materializeLogicalCursor();
          }
          publishBest(step, () -> bestSolutionRecaller.processWorkingSolutionDuringStep(step));
        }
      }
      step.setCandidateScore(candidateScore);
      return new Individual<>(prepared.genome, candidateScore, prepared.id);
    }

    /** Count logical assignment differences independently of the worker's preceding job. */
    private int logicalChangedAssignmentCount(GeneticAlgorithmGenome candidate) {
      int changed = 0;
      var baseline = logicalGenome;
      for (int i = 0; i < baseline.size(); i++) {
        if (!Objects.equals(baseline.value(i), candidate.value(i))) changed++;
      }
      if (workspace.listModel() != null) {
        int valueCount = workspace.listModel().valueCount();
        var oldOwners = logicalOldOwners;
        var newOwners = logicalNewOwners;
        var oldIndexes = logicalOldIndexes;
        var newIndexes = logicalNewIndexes;
        Arrays.fill(oldOwners, -1);
        Arrays.fill(newOwners, -1);
        Arrays.fill(oldIndexes, 0);
        Arrays.fill(newIndexes, 0);
        var oldLists = baseline.listSnapshot();
        var newLists = candidate.listSnapshot();
        for (int owner = 0; owner < oldLists.ownerCount(); owner++) {
          for (int index = 0; index < oldLists.size(owner); index++) {
            int value = oldLists.get(owner, index);
            oldOwners[value] = owner;
            oldIndexes[value] = index;
          }
          for (int index = 0; index < newLists.size(owner); index++) {
            int value = newLists.get(owner, index);
            newOwners[value] = owner;
            newIndexes[value] = index;
          }
        }
        for (int value = 0; value < valueCount; value++) {
          if (oldOwners[value] != newOwners[value] || oldIndexes[value] != newIndexes[value])
            changed++;
        }
      }
      return changed;
    }

    private String eagerMaterializationReason() {
      if (!workspace.slots().isEmpty()) return "basic assignments";
      if (environmentMode != EnvironmentMode.NO_ASSERT
          || assertPhaseScoreFromScratch
          || assertStepScoreFromScratch
          || assertExpectedStepScore
          || assertShadowVariablesAreNotStaleAfterStep) return "assertions";
      if (hasStateObservers()) return "lifecycle observers";
      if (!TerminationGraphAccess.isWorkingSolutionIndependent(phaseTermination)
          || !scope.getSolverScope().getSolver().isTerminationWorkingSolutionIndependent()) {
        return "working-solution-dependent termination";
      }
      return null;
    }

    private boolean hasStateObservers() {
      return hasPhaseLifecycleListeners()
          || scope.getSolverScope().getSolver().hasPhaseLifecycleListeners()
          || scope.hasCommittedStepListeners()
          || scope.getScoreDirector().getWorkingSolutionMutationObserver() != null;
    }

    /** Once an observer is installed, preserve eager observation for the rest of this run. */
    private void ensureObservationCompatibility() {
      if (deferredMaterialization && hasStateObservers()) {
        materializeLogicalCursor();
        deferredMaterialization = false;
        eagerReason = "lifecycle observers";
      }
    }

    private GeneticAlgorithmWorkspace.Transition transitionCoordinator(
        GeneticAlgorithmGenome genome) {
      var transition = workspace.transition(genome);
      if (!transition.valid()) {
        throw new IllegalStateException(
            "A worker-valid genetic algorithm candidate was invalid in the coordinator workspace.");
      }
      coordinatorMaterializations++;
      return transition;
    }

    private void scoreMaterializedCandidate(GeneticAlgorithmStepScope<Solution_> step) {
      coordinatorScoreDirty = true;
      predictWorkingStepScore(
          step, "Genetic Algorithm evaluator candidate " + step.getCandidateId());
      workspace.scored(logicalScore);
      if (assertExpectedStepScore || assertShadowVariablesAreNotStaleAfterStep) {
        coordinatorScoreDirty = false;
      }
    }

    /** A logical score is never installed on a graph with different genuine assignments. */
    private void materializeLogicalCursor() {
      if (logicalGenome == null) return;
      if (logicalGenome.equals(workspace.genome())) {
        if (!logicalScore.equals(workspace.score())) {
          throw new IllegalStateException(
              "The genetic algorithm evaluator score (%s) differs from its materialized score (%s) for equal assignments."
                  .formatted(logicalScore, workspace.score()));
        }
        return;
      }
      transitionCoordinator(logicalGenome);
      scope.getSolutionDescriptor().setScore(scope.getWorkingSolution(), logicalScore.raw());
      workspace.scored(logicalScore);
      coordinatorScoreDirty = true;
    }

    private void verifyCoordinatorScore() {
      materializeLogicalCursor();
      if (!coordinatorScoreDirty) return;
      var predicted = workspace.score();
      InnerScore<Score_> actual = scope.calculateScore();
      if (!actual.equals(predicted)) {
        throw new IllegalStateException(
            "The genetic algorithm coordinator score (%s) differs from its evaluator prediction (%s)."
                .formatted(actual, predicted));
      }
      workspace.scored(actual);
      coordinatorScoreDirty = false;
    }

    /** Migrants are evaluated serially and become population members in one batch commit. */
    private boolean migrateAtGenerationBoundary() {
      if (migrationCodec == null || scope.getGeneration() % migration.frequency() != 0) return true;
      if (stopped()) return false;
      sortPopulation();
      int exportCount = Math.max(1, (int) Math.ceil(config.getMigrationRate() * population.size()));
      var leading = population.subList(0, exportCount);
      var emigrants =
          migrationCodec.export(
              leading.stream().map(Individual::genome).toList(),
              leading.stream().map(Individual::score).toList());
      if (stopped()) return false;
      // Export is deliberately completed before importing, so a migrant cannot be relayed at the
      // same boundary. The endpoint keeps the whole latest batch, never a mixture of donors.
      exportedBatches++;
      exportedEntries += emigrants.size();
      var batch = migration.exchange(scope.getGeneration(), emigrants);
      if (stopped()) return false;
      if (batch == null) return true;
      receivedBatches++;
      int importCount = Math.min(population.size(), batch.entries().size());
      receivedEntries += importCount;
      if (importCount == 0) return true;
      int replacementStart = population.size() - importCount;
      var replacements = new ArrayList<Individual<Score_>>(importCount);
      var entryGenome = workspace.genome();
      var entryScore = workspace.score();
      boolean entryWorkspaceChangedOutsideStep = workspaceChangedOutsideStep;
      boolean workspaceTouched = false;
      boolean committed = false;
      Throwable originalFailure = null;
      try {
        for (int i = 0; i < importCount; i++) {
          if (stopped()) return false;
          var migrant = batch.entries().get(i);
          var genome = migrationCodec.importGenome(migrant.assignments());
          // Rebase/range code may invoke user code. Cancellation still precedes any notification.
          if (stopped()) return false;
          workspaceTouched = true;
          workspaceChangedOutsideStep = true;
          var transition = workspace.transition(genome);
          if (!transition.valid()) {
            throw new IllegalStateException(
                "The migrated assignments are structurally invalid or violate recipient pins or ranges.");
          }
          var publication =
              new GeneticAlgorithmStepScope<>(
                  scope, scope.getLastCompletedStepScope().getStepIndex());
          publication.setGeneration(scope.getGeneration());
          calculateWorkingStepScore(
              publication, "Genetic Algorithm migrant from island " + batch.sourceIslandId());
          InnerScore<Score_> verifiedScore = publication.getScore();
          if (!verifiedScore.isFullyAssigned() || verifiedScore.isStructurallyFlawed()) {
            throw new IllegalStateException(
                "The migrated assignments produced an invalid native score ("
                    + verifiedScore
                    + ").");
          }
          if (!verifiedScore.equals(migrant.score())) {
            throw new IllegalStateException(
                "The migrated native score ("
                    + verifiedScore
                    + ") differs from the advertised score ("
                    + migrant.score()
                    + ").");
          }
          workspace.scored(verifiedScore);
          evaluatedEntries++;
          publishBest(
              publication,
              () -> bestSolutionRecaller.processWorkingSolutionDuringStep(publication));
          // Pair against the original ranked suffix. Previously staged replacements never alter
          // a later migrant's admission threshold, and the archived best is not an admission gate.
          var nativeMember = population.get(replacementStart + i);
          replacements.add(
              verifiedScore.compareTo(nativeMember.score()) > 0
                  ? new Individual<>(genome, verifiedScore, -1L)
                  : null);
        }
        if (stopped()) return false;
        boolean membershipChanged = false;
        for (int i = 0; i < replacements.size(); i++) {
          var replacement = replacements.get(i);
          if (replacement == null) continue;
          population.set(
              replacementStart + i,
              new Individual<>(replacement.genome(), replacement.score(), nextId++));
          membershipChanged = true;
          admittedEntries++;
        }
        rejectedEntries += replacements.stream().filter(java.util.Objects::isNull).count();
        committedBatches++;
        committed = true;
        if (membershipChanged) {
          scope.resetNoProgressAttemptCount();
          sortPopulation();
          updatePopulationDiagnostics();
        }
        return true;
      } catch (RuntimeException | Error failure) {
        originalFailure = failure;
        failure.addSuppressed(
            new IllegalStateException(
                "Genetic Algorithm migration invariant failed for donor island ("
                    + batch.sourceIslandId()
                    + "), donor generation ("
                    + batch.generation()
                    + "), recipient generation ("
                    + scope.getGeneration()
                    + ")."));
        throw failure;
      } finally {
        if (!committed && workspaceTouched) {
          runCleanup(
              originalFailure,
              List.of(
                  () -> {
                    try {
                      workspace.restore(entryGenome, entryScore);
                      workspaceChangedOutsideStep = entryWorkspaceChangedOutsideStep;
                    } catch (RuntimeException | Error restorationFailure) {
                      restorationFailure.addSuppressed(
                          new IllegalStateException(
                              "Restoring Genetic Algorithm migration from donor island ("
                                  + batch.sourceIslandId()
                                  + "), donor generation ("
                                  + batch.generation()
                                  + "), recipient generation ("
                                  + scope.getGeneration()
                                  + ")."));
                      throw restorationFailure;
                    }
                  }));
        }
      }
    }

    private GeneticAlgorithmGenome nextChild() {
      return nextChild(scope.getWorkingRandom().moveIteratorUsage());
    }

    private GeneticAlgorithmGenome nextChild(RandomGenerator random) {
      if (pendingChild != null) {
        var child = pendingChild;
        pendingChild = null;
        return child;
      }
      var first =
          population.get(
              GeneticAlgorithmOperators.selectRankedIndex(
                  population.size(), config.getPBestRate(), true, random));
      var second =
          population.get(
              GeneticAlgorithmOperators.selectRankedIndex(
                  population.size(), config.getPBestRate(), true, random));
      firstParentId = first.id();
      secondParentId = second.id();
      var pair = operators.cross(first.genome(), second.genome(), random);
      pendingChild = pair.second();
      crossed = pair.crossed();
      return pair.first();
    }

    /** Null means interruption; completed probes retain their separate work credits. */
    private Individual<Score_> attempt(
        boolean seeding,
        Individual<Score_> seedFallback,
        Supplier<GeneticAlgorithmGenome> proposal) {
      var step = new GeneticAlgorithmStepScope<>(scope);
      step.setSeeding(seeding);
      step.setGeneration(seeding ? 0 : scope.getGeneration() + 1);
      step.setBeforeScore(workspace.score());
      step.setBestBeforeScore(scope.getBestScore());
      var attemptEntryGenome = workspace.genome();
      var attemptEntryScore = workspace.score();
      var randomSource = (DefaultRandomSource) scope.getWorkingRandom();
      var previousRandom = randomSource.moveRandom().getDelegate();
      boolean lifecycleCompleted = false;
      boolean credited = false;
      boolean typeCredited = false;
      Throwable attemptFailure = null;
      try {
        stepStarted(step);
        if (stopped()) return null;
        var genome = proposal.get();
        if (!seeding) {
          step.setParentIds(firstParentId, secondParentId);
          step.setCrossed(crossed);
          var mutation = operators.mutate(genome, randomSource.moveIteratorUsage());
          genome = mutation.genome();
          step.setMutationType(mutation.type());
          step.setMutationGroup(mutation.group());
        }
        step.setCandidateId(nextId++);
        if (stopped()) return null;
        var candidate = evaluate(step, genome);
        if (localMoves != null
            && !seeding
            && step.getOutcome() == GeneticAlgorithmOutcome.EVALUATED) {
          if (!improve(step)) {
            workspace.restore(attemptEntryGenome, attemptEntryScore);
            step.setScore(workspace.score());
            return null;
          }
          candidate =
              new Individual<>(workspace.genome(), workspace.score(), step.getCandidateId());
          step.setCandidateScore(candidate.score());
        }
        Individual<Score_> nativeIndividual = seedFallback;
        if (!seeding) {
          nativeIndividual =
              population.get(
                  GeneticAlgorithmOperators.selectRankedIndex(
                      population.size(),
                      config.getPBestRate(),
                      false,
                      randomSource.moveIteratorUsage()));
          step.setNativeId(nativeIndividual.id());
        }
        boolean admitted =
            candidate != null
                && (seeding || candidate.score().compareTo(nativeIndividual.score()) >= 0);
        step.setAdmitted(admitted);
        var winner = admitted ? candidate : nativeIndividual;
        // Callbacks observe this step's work. A failed callback rolls back its provisional credit.
        scope.getSolverScope().addMoveEvaluationCount(1);
        credited = true;
        if (scope.getSolverScope().isMetricEnabled(SolverMetric.MOVE_COUNT_PER_TYPE)) {
          scope.getSolverScope().addMoveEvaluationCountPerType(step.getMoveTypeDescription(), 1);
          typeCredited = true;
        }
        metrics.record(step);
        stepEnded(step);
        lifecycleCompleted = true;
        workspaceChangedOutsideStep = false;
        commitAttempt(step);
        observeLogicalAttempt(step, workspace.genome());
        SolverMetricSamples.publishIslandStep(scope.getSolverScope(), step);
        logStep(step);
        return winner;
      } catch (RuntimeException | Error failure) {
        attemptFailure = failure;
        throw failure;
      } finally {
        if (!lifecycleCompleted) {
          var cleanup = new ArrayList<Runnable>();
          if (credited) cleanup.add(() -> scope.getSolverScope().addMoveEvaluationCount(-1));
          if (typeCredited) {
            cleanup.add(
                () -> {
                  scope
                      .getSolverScope()
                      .addMoveEvaluationCountPerType(step.getMoveTypeDescription(), -1);
                  scope
                      .getSolverScope()
                      .getMoveEvaluationCountPerType()
                      .remove(step.getMoveTypeDescription(), 0L);
                });
          }
          cleanup.add(() -> metrics.restoreStepCounts(scope.getLastCompletedStepScope()));
          // Interrupted attempts do not fire completion callbacks, but must release the RNG split.
          cleanup.add(() -> randomSource.restoreState(previousRandom));
          runCleanup(attemptFailure, cleanup);
        }
      }
    }

    private Individual<Score_> evaluate(
        GeneticAlgorithmStepScope<Solution_> step, GeneticAlgorithmGenome genome) {
      InnerScore<Score_> candidateScore;
      if (genome.equals(workspace.genome())) {
        step.setOutcome(GeneticAlgorithmOutcome.NO_CHANGE);
        candidateScore = workspace.score();
        step.setScore(workspace.score());
      } else {
        var duplicate = cached(genome);
        if (duplicate != null) {
          step.setOutcome(GeneticAlgorithmOutcome.DUPLICATE);
          candidateScore = duplicate.score();
          step.setScore(workspace.score());
        } else {
          var previous = workspace.genome();
          var previousScore = workspace.score();
          var transition = workspace.transition(genome);
          step.setChangedAssignmentCount(transition.changedAssignmentCount());
          if (!transition.valid()) {
            step.setOutcome(GeneticAlgorithmOutcome.INVALID);
            step.setScore(workspace.score());
            return null;
          }
          calculateWorkingStepScore(step, "Genetic Algorithm candidate " + step.getCandidateId());
          candidateScore = step.getScore();
          if (!candidateScore.isFullyAssigned() || candidateScore.isStructurallyFlawed()) {
            workspace.restore(previous, previousScore);
            step.setOutcome(GeneticAlgorithmOutcome.INVALID);
            step.setScore(workspace.score());
            return null;
          }
          workspace.scored(candidateScore);
          step.setOutcome(GeneticAlgorithmOutcome.EVALUATED);
          if (localMoves == null && migration == null) {
            bestSolutionRecaller.processWorkingSolutionDuringStep(step);
          } else {
            publishBest(step, () -> bestSolutionRecaller.processWorkingSolutionDuringStep(step));
          }
        }
      }
      step.setCandidateScore(candidateScore);
      return new Individual<>(genome, candidateScore, step.getCandidateId());
    }

    /** Greedy probes own their work credits; they never create additional GA steps. */
    private boolean improve(GeneticAlgorithmStepScope<Solution_> step) {
      if (stopped()) return false;
      Throwable originalFailure = null;
      try {
        localMoves.stepStarted(step);
        boolean needsScoreRefresh = false;
        for (long probe = 0; probe < config.getLocalImprovementMoveCountLimit(); probe++) {
          if (stopped()) return false;
          var move = localMoves.nextMove(scope.getWorkingRandom().moveIteratorUsage());
          if (move == null) break;
          // Selection may invoke user range/filter code; cancellation is safe before application.
          if (stopped()) return false;
          InnerScoreDirector<Solution_, Score_> director = scope.getScoreDirector();
          var baselineGenome = workspace.genome();
          var baselineScore = workspace.score();
          var improvedGenome = new AtomicReference<GeneticAlgorithmGenome>();
          InnerScore<Score_> probeScore = baselineScore;
          String outcome = "NO_CHANGE";
          if (!(move instanceof AbstractSelectorBasedMove<Solution_> selectorMove)
              || selectorMove.isMoveDoable(director)) {
            probeScore =
                director.executeTemporaryMoveWithScore(
                    move,
                    (view, score) -> {
                      if (score.isFullyAssigned()
                          && !score.isStructurallyFlawed()
                          && score.compareTo(baselineScore) > 0) {
                        improvedGenome.set(workspace.captureGenome());
                        publishBest(
                            step,
                            () ->
                                bestSolutionRecaller.processWorkingSolutionDuringMove(score, step));
                      }
                    },
                    getEnvironmentMode().isFullyAsserted());
            needsScoreRefresh = true;
            if (!probeScore.isFullyAssigned() || probeScore.isStructurallyFlawed()) {
              workspace.restore(baselineGenome, baselineScore);
              needsScoreRefresh = false;
              outcome = "INVALID";
            } else {
              outcome = improvedGenome.get() == null ? "REJECTED" : "IMPROVING";
            }
          }
          // executeTemporaryMoveWithScore has finished its required undo before this credit.
          scope.getSolverScope().addMoveEvaluationCount(1);
          step.recordLocalImprovementProbe();
          if (scope.getSolverScope().isMetricEnabled(SolverMetric.MOVE_COUNT_PER_TYPE)) {
            scope
                .getSolverScope()
                .addMoveEvaluationCountPerType(
                    "GeneticAlgorithm/LOCAL_IMPROVEMENT/" + move.describe() + "/" + outcome, 1);
          }
          if (stopped()) return false;
          if (improvedGenome.get() != null) {
            var transition = workspace.transition(improvedGenome.get());
            if (!transition.valid()) {
              throw new IllegalStateException(
                  "A valid local improvement could not be materialized.");
            }
            calculateWorkingStepScore(step, "Genetic Algorithm local improvement " + move);
            if (!step.getScore().equals(probeScore)) {
              throw new IllegalStateException(
                  "The materialized local improvement score (%s) differs from its probe score (%s)."
                      .formatted(step.getScore(), probeScore));
            }
            workspace.scored(step.getScore());
            needsScoreRefresh = false;
            step.recordLocalImprovementAccepted();
            localMoves.reset();
            if (stopped()) return false;
          }
        }
        if (stopped()) return false;
        if (needsScoreRefresh) {
          // Temporary undo queues Bavet updates. Flush before completion listeners/metrics read
          // constraint totals; restoring only the score field would expose the last trial's totals.
          workspace.restore(workspace.genome(), workspace.score());
          step.setScore(workspace.score());
        }
        return !stopped();
      } catch (RuntimeException | Error failure) {
        originalFailure = failure;
        throw failure;
      } finally {
        // Only selector cleanup: an interrupted offspring must not fire GA completion callbacks.
        runCleanup(originalFailure, List.of(() -> localMoves.stepEnded(step)));
      }
    }

    private void publishBest(GeneticAlgorithmStepScope<Solution_> step, Runnable publication) {
      InnerScore<Score_> previousBest = scope.getBestScore();
      Throwable originalFailure = null;
      try {
        publication.run();
      } catch (RuntimeException | Error failure) {
        originalFailure = failure;
        throw failure;
      } finally {
        // The recaller updates the archive before firing external listeners. Even if a listener
        // fails, sample that archive's totals before the temporary move is undone.
        if (scope.<Score_>getBestScore().compareTo(previousBest) > 0) {
          runCleanup(
              originalFailure,
              List.of(
                  () -> metrics.recordBest(scope),
                  () -> phaseTermination.bestScoreImproved(step),
                  () -> {
                    if (migration != null) migration.publishBest();
                  }));
        }
      }
    }

    private Individual<Score_> cached(GeneticAlgorithmGenome genome) {
      var individual = populationIndex.firstMatch(genome);
      return individual == null ? winnerIndex.firstMatch(genome) : individual;
    }

    private void rebuildPopulationIndex() {
      populationIndex.clear();
      for (var individual : population) populationIndex.add(individual.genome(), individual);
    }

    private void sortPopulation() {
      population.sort(ranking);
      rebuildPopulationIndex();
    }

    private void clearWinners() {
      winners.clear();
      winnerIndex.clear();
    }

    private void addWinner(Individual<Score_> winner) {
      winners.add(winner);
      winnerIndex.add(winner.genome(), winner);
    }

    private void observeLogicalAttempt(
        GeneticAlgorithmStepScope<Solution_> step, GeneticAlgorithmGenome genome) {
      if (logicalAttemptObserver == null) return;
      if (genome.size() != 0) {
        throw new IllegalStateException(
            "Detached GA attempt observation requires list-only assignments.");
      }
      logicalAttemptObserver.accept(
          new LogicalAttempt(
              step.getStepIndex(),
              step.isSeeding(),
              step.getGeneration(),
              step.getCandidateId(),
              step.getFirstParentId(),
              step.getSecondParentId(),
              step.getNativeId(),
              step.isCrossed(),
              step.getMutationType(),
              step.getMutationGroup(),
              step.getOutcome(),
              step.isAdmitted(),
              step.getChangedAssignmentCount(),
              step.getBeforeScore(),
              step.getBestBeforeScore(),
              step.getCandidateScore(),
              step.getScore(),
              step.getBestScoreImproved(),
              genome.listSnapshot()));
    }

    private void commitAttempt(GeneticAlgorithmStepScope<Solution_> step) {
      try {
        scope.commitStep(step);
      } finally {
        // Committed-step callbacks can fail after the attempt and its work were committed.
        if (scope.getLastCompletedStepScope() == step
            && !step.isSeeding()
            && step.getOutcome() == GeneticAlgorithmOutcome.EVALUATED) {
          evaluatedOffspring++;
        }
      }
    }

    private boolean stopped() {
      if (evaluatorPool != null) {
        evaluatorPool.transferCalculationCount();
        evaluatorPool.checkFailure();
      }
      scope.getSolverScope().checkYielding();
      if (Thread.currentThread().isInterrupted() || phaseTermination.isPhaseTerminated(scope))
        return true;
      if (scope.getNoProgressAttemptCount() >= config.getNoProgressAttemptLimit()) {
        scope.setTerminationReason(
            "no fresh valid offspring score for "
                + scope.getNoProgressAttemptCount()
                + " attempts");
        return true;
      }
      return false;
    }

    private void addPopulationMember(Individual<Score_> individual) {
      population.add(individual);
      populationIndex.add(individual.genome(), individual);
      populationDiversity.add(individual.genome());
      scope.setPopulationSize(population.size(), populationDiversity.size());
    }

    private void updatePopulationDiagnostics() {
      populationDiversity.clear();
      for (var individual : population) {
        populationDiversity.add(individual.genome());
      }
      scope.setPopulationSize(population.size(), populationDiversity.size());
    }
  }

  public static final class Builder<Solution_>
      extends AbstractPhaseBuilder<Solution_, DefaultGeneticAlgorithmPhase<Solution_>> {
    private final GeneticAlgorithmPhaseConfig config;
    private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private Function<RandomSource, GeneticAlgorithmLocalImprovementMoves<Solution_>>
        localImprovementMovesFactory;
    private ThreadFactory evaluatorThreadFactory;

    public Builder(
        int phaseIndex,
        EnvironmentMode environmentMode,
        String logIndentation,
        PhaseTermination<Solution_> phaseTermination,
        GeneticAlgorithmPhaseConfig config,
        BestSolutionRecaller<Solution_> bestSolutionRecaller) {
      super(phaseIndex, environmentMode, logIndentation, phaseTermination);
      this.config = config;
      this.bestSolutionRecaller = bestSolutionRecaller;
    }

    public Builder<Solution_> withLocalImprovementMovesFactory(
        Function<RandomSource, GeneticAlgorithmLocalImprovementMoves<Solution_>> factory) {
      localImprovementMovesFactory = factory;
      return this;
    }

    public Builder<Solution_> withEvaluatorThreadFactory(ThreadFactory threadFactory) {
      evaluatorThreadFactory = threadFactory;
      return this;
    }

    @Override
    public DefaultGeneticAlgorithmPhase<Solution_> build() {
      return new DefaultGeneticAlgorithmPhase<>(this);
    }
  }
}
