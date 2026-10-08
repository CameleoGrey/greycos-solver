package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
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

/**
 * Serial population search with immutable genomes and a retained evaluation workspace. Population
 * admission never determines workspace ownership or best-solution publication.
 */
public final class DefaultGeneticAlgorithmPhase<Solution_> extends AbstractPhase<Solution_> {
  private final GeneticAlgorithmPhaseConfig config;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final Function<RandomSource, GeneticAlgorithmLocalImprovementMoves<Solution_>>
      localImprovementMovesFactory;
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
    if (originalFailure == null || localMoves == null) {
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
    private final List<Individual<Score_>> population = new ArrayList<>();
    private final List<Individual<Score_>> winners = new ArrayList<>();
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

    private Search(
        GeneticAlgorithmPhaseScope<Solution_> scope,
        GeneticAlgorithmWorkspace<Solution_, Score_> workspace,
        GeneticAlgorithmOperators<Solution_> operators,
        GeneticAlgorithmLocalImprovementMoves<Solution_> localMoves) {
      this.scope = scope;
      this.workspace = workspace;
      this.operators = operators;
      this.localMoves = localMoves;
      migrationCodec =
          migration != null && config.getMigrationRate() > 0.0
              ? new GeneticAlgorithmMigrationCodec<>(workspace, scope.getScoreDirector())
              : null;
    }

    private void run() {
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
      while (!stopped()) {
        population.sort(ranking);
        winners.clear();
        pendingChild = null;
        long generation = scope.getGeneration() + 1;
        for (int i = 0; i < population.size(); i++) {
          if (stopped()) return;
          var winner = attempt(false, null, () -> nextChild());
          if (winner == null) return;
          winners.add(winner);
        }
        population.clear();
        population.addAll(winners);
        winners.clear();
        // An unused second child of an odd population is neither mutated nor evaluated.
        pendingChild = null;
        scope.setGeneration(generation);
        completedGenerations = generation;
        updatePopulationDiagnostics();
        if (!migrateAtGenerationBoundary()) return;
      }
    }

    /** Migrants are evaluated serially and become population members in one batch commit. */
    private boolean migrateAtGenerationBoundary() {
      if (migrationCodec == null || scope.getGeneration() % migration.frequency() != 0) return true;
      if (stopped()) return false;
      population.sort(ranking);
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
          population.sort(ranking);
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
      var random = scope.getWorkingRandom().moveIteratorUsage();
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
        scope.commitStep(step);
        SolverMetricSamples.publishIslandStep(scope.getSolverScope(), step);
        logger.debug(
            "{}    Genetic Algorithm step ({}), generation ({}), score ({}), candidate ({}),"
                + " parents ({}, {}), mutation ({}, {}), outcome ({}), admitted ({}), changed ({}).",
            logIndentation,
            step.getStepIndex(),
            step.getGeneration(),
            step.getScore(),
            step.getCandidateId(),
            step.getFirstParentId(),
            step.getSecondParentId(),
            step.getMutationType(),
            step.getMutationGroup(),
            step.getOutcome(),
            admitted,
            step.getChangedAssignmentCount());
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
      for (var individual : population) if (individual.genome().equals(genome)) return individual;
      for (var individual : winners) if (individual.genome().equals(genome)) return individual;
      return null;
    }

    private boolean stopped() {
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

    @Override
    public DefaultGeneticAlgorithmPhase<Solution_> build() {
      return new DefaultGeneticAlgorithmPhase<>(this);
    }
  }
}
