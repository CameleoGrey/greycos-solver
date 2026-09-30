package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchLevelScaleConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.LocalSearchPhaseDecider;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope.NoStepReason;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** First-improvement GLS, with penalty-only rounds contained within a real local-search step. */
public final class GuidedLocalSearchDecider<Solution_>
    implements LocalSearchPhaseDecider<Solution_> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GuidedLocalSearchDecider.class);

  private final String logIndentation;
  private final PhaseTermination<Solution_> termination;
  private final MoveRepository<Solution_> repository;
  private final GuidedLocalSearchFeatureProvider<Solution_, Object> provider;
  private final GuidedLocalSearchGuidanceMode guidanceMode;
  private final BigDecimal penaltyFactor;
  private final int fixedTarget;
  private final List<GuidedLocalSearchLevelScaleConfig> levelScaleOverrides;
  private final int focusStepLimit;
  private final int focusPenaltyUpdateLimit;
  private GuidedLocalSearchScoreComparator comparator;
  private final GuidedLocalSearchSearchMode searchMode;
  private final int sampleSize;
  private final int maxUnproductiveRounds;
  private final boolean resetPenaltiesOnNewBest;
  private final ThreadFactory threadFactory;
  private final int moveThreadCount;
  private final int bufferSize;
  private final List<GuidedLocalSearchPenaltyTable<Object>> penaltyTables = new ArrayList<>();
  private final List<GuidedLocalSearchScale.Calibration> calibrations = new ArrayList<>();
  private int focusLevel;
  private int focusSteps;
  private int focusPenaltyUpdates;
  private boolean feasibleSeen;
  private long guidanceVersion;
  private long focusSwitches;

  private boolean assertFromScratch;
  private boolean assertUndo;
  private boolean assertExpectedStepScore;
  private boolean resetOnPendingMove;
  private GuidedLocalSearchFeatureTracker<Solution_, Object> tracker;
  private MoveEvaluationPipeline<Solution_> pipeline;
  private MoveEvaluationPipeline.Diagnostics diagnostics;
  private long transferredCalculationCount;
  private long decisionRounds;
  private long penaltyUpdates;
  private long emptyRounds;
  private long recoveryMoves;
  private long featureResetVersion;
  private int nextMoveIndex;

  @SuppressWarnings("unchecked")
  public GuidedLocalSearchDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> repository,
      GuidedLocalSearchFeatureProvider<Solution_, ?> provider,
      GuidedLocalSearchGuidanceMode guidanceMode,
      BigDecimal penaltyFactor,
      int targetScoreLevelIndex,
      List<GuidedLocalSearchLevelScaleConfig> levelScaleOverrides,
      int focusStepLimit,
      int focusPenaltyUpdateLimit,
      GuidedLocalSearchSearchMode searchMode,
      int sampleSize,
      int maxUnproductiveRounds,
      boolean resetPenaltiesOnNewBest,
      ThreadFactory threadFactory,
      int moveThreadCount,
      int bufferSize) {
    this.logIndentation = logIndentation;
    this.termination = termination;
    this.repository = repository;
    this.provider = (GuidedLocalSearchFeatureProvider<Solution_, Object>) provider;
    this.guidanceMode = guidanceMode;
    this.penaltyFactor = penaltyFactor;
    fixedTarget = targetScoreLevelIndex;
    this.levelScaleOverrides = List.copyOf(levelScaleOverrides);
    this.focusStepLimit = focusStepLimit;
    this.focusPenaltyUpdateLimit = focusPenaltyUpdateLimit;
    this.searchMode = searchMode;
    this.sampleSize = sampleSize;
    this.maxUnproductiveRounds = maxUnproductiveRounds;
    this.resetPenaltiesOnNewBest = resetPenaltiesOnNewBest;
    this.threadFactory = threadFactory;
    this.moveThreadCount = moveThreadCount;
    this.bufferSize = bufferSize;
  }

  @Override
  public void enableAssertions(EnvironmentMode mode) {
    assertFromScratch = mode.isFullyAsserted();
    assertUndo = mode.isIntrusivelyAsserted();
    assertExpectedStepScore = mode.isIntrusivelyAsserted();
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    decisionRounds =
        penaltyUpdates = emptyRounds = recoveryMoves = transferredCalculationCount = 0L;
    diagnostics = null;
    focusLevel = -1;
    focusSwitches = 0;
    focusSteps = focusPenaltyUpdates = 0;
    penaltyTables.clear();
    calibrations.clear();
    repository.solvingStarted(solverScope);
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    repository.phaseStarted(phaseScope);
    penaltyTables.clear();
    calibrations.clear();
    int levelCount = phaseScope.getSolverScope().getScoreDefinition().getLevelsSize();
    for (int level = 0; level < levelCount; level++) {
      penaltyTables.add(new GuidedLocalSearchPenaltyTable<>());
    }
    resetCalibrations();
    guidanceVersion = 0;
    initializeFocus(phaseScope);
    focusSwitches = 0;
    decisionRounds =
        penaltyUpdates = emptyRounds = recoveryMoves = transferredCalculationCount = 0L;
    diagnostics = null;
    resetOnPendingMove = false;
    featureResetVersion = 0;
    tracker = newTracker(phaseScope.getScoreDirector());
    if (moveThreadCount == 0) {
      return;
    }
    ExecutorService executor =
        Executors.newFixedThreadPool(
            moveThreadCount,
            ThreadUtils.requireNonNullThreads(threadFactory, "Guided Local Search"));
    phaseScope
        .getSolverScope()
        .getWorkerRegistry()
        .registerExecutor(executor, "Guided Local Search");
    try {
      pipeline =
          new MoveEvaluationPipeline<>(
              executor,
              moveThreadCount,
              bufferSize,
              phaseScope.getPhaseIndex(),
              true,
              assertFromScratch,
              assertUndo,
              assertFromScratch,
              assertExpectedStepScore,
              assertExpectedStepScore);
      pipeline.setTerminationCheck(() -> termination.isPhaseTerminated(phaseScope));
      pipeline.setMetadataCollectorFactory(director -> new WorkerCollector(director));
      pipeline.start(phaseScope.getScoreDirector());
    } catch (RuntimeException | Error failure) {
      try {
        executor.shutdownNow();
        tracker.close();
        tracker = null;
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void resetCalibrations() {
    calibrations.clear();
    for (int level = 0; level < penaltyTables.size(); level++) {
      BigDecimal override = null;
      for (var config : levelScaleOverrides) {
        if (config.getScoreLevelIndex() == level) override = config.getScale();
      }
      calibrations.add(new GuidedLocalSearchScale.Calibration(override));
    }
  }

  private GuidedLocalSearchFeatureTracker<Solution_, Object> newTracker(
      InnerScoreDirector<Solution_, ?> director) {
    if (allLevels()) {
      return GuidedLocalSearchFeatureTracker.attachAutomatic(
          director,
          provider,
          snapshots(),
          tracker == null ? null : tracker.identityRegistry(),
          penaltyTables.size() - 1);
    }
    return GuidedLocalSearchFeatureTracker.attach(
        director, provider, penalties().snapshot(), fixedTarget);
  }

  private boolean allLevels() {
    return guidanceMode == GuidedLocalSearchGuidanceMode.ALL_LEVELS;
  }

  private GuidedLocalSearchPenaltyTable<Object> penalties() {
    return penaltyTables.get(focusLevel);
  }

  private List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> snapshots() {
    return penaltyTables.stream().map(GuidedLocalSearchPenaltyTable::snapshot).toList();
  }

  private GuidedLocalSearchScale scale() {
    return allLevels() ? calibrations.get(focusLevel).scale() : GuidedLocalSearchScale.ONE;
  }

  private GuidedLocalSearchNumber aggregate(
      GuidedLocalSearchFeatureTracker<Solution_, Object> featureTracker,
      int level,
      GuidedLocalSearchScale scale) {
    if (!allLevels()) return featureTracker.aggregate();
    var guidance = featureTracker.aggregates();
    return guidance
        .automatic()
        .get(level)
        .multiply(scale.numerator())
        .add(guidance.custom().get(level).multiply(scale.denominator()));
  }

  private void updateTrackerPenalties() {
    if (allLevels()) tracker.updatePenalties(snapshots());
    else tracker.updatePenalties(penalties().snapshot());
    guidanceVersion = Math.incrementExact(guidanceVersion);
  }

  private void initializeFocus(LocalSearchPhaseScope<Solution_> phaseScope) {
    var score = phaseScope.getLastCompletedStepScope().getScore().raw();
    feasibleSeen = score.isFeasible();
    int initial = fixedTarget;
    if (allLevels()) {
      initial = penaltyTables.size() - 1;
      var levels = score.toLevelNumbers();
      for (int i = 0;
          i < phaseScope.getSolverScope().getScoreDefinition().getFeasibleLevelsSize();
          i++) {
        if (GuidedLocalSearchNumber.of(levels[i]).signum() < 0) {
          initial = i;
          break;
        }
      }
    }
    setFocus(initial);
  }

  private void setFocus(int level) {
    if (focusLevel != level) focusSwitches++;
    focusLevel = level;
    focusSteps = focusPenaltyUpdates = 0;
    comparator = new GuidedLocalSearchScoreComparator(level, penaltyFactor);
    guidanceVersion = Math.incrementExact(guidanceVersion);
  }

  private void advanceFocus() {
    setFocus(focusLevel == 0 ? penaltyTables.size() - 1 : focusLevel - 1);
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    repository.stepStarted(stepScope);
    stepScope.setSelectedMoveCount(0L);
    stepScope.setAcceptedMoveCount(0L);
    nextMoveIndex = 0;
  }

  @Override
  public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
    var scoreDirector = stepScope.getScoreDirector();
    scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);
    try {
      if (pipeline != null) pipeline.startNextStep(stepScope.getStepIndex());
      if (terminated(stepScope)) {
        stepScope.setNoStepReason(NoStepReason.TERMINATED);
        return;
      }
      var pending = stepScope.getPhaseScope().getSolverScope().consumePendingMove();
      if (pending != null) {
        resetOnPendingMove = pending.requiresReset();
        var score =
            stepScope.getScoreDirector().executeTemporaryMove(pending.move(), assertFromScratch);
        stepScope.getPhaseScope().addMoveEvaluationCount(pending.move(), 1L);
        stepScope.setSelectedMoveCount(1L);
        select(stepScope, new Candidate<>(pending.move(), score, GuidedLocalSearchNumber.ZERO, 0));
        return;
      }
      InnerScore<?> currentScore = stepScope.getPhaseScope().getLastCompletedStepScope().getScore();
      if (allLevels()) tracker.markBaseline();
      var currentPenalty = aggregate(tracker, focusLevel, scale());
      if (assertFromScratch) tracker.assertFromScratch();
      int unproductiveRounds = 0;
      int emptyFocusLevels = 0;
      while (!terminated(stepScope)) {
        var result = runRound(stepScope, currentScore, currentPenalty, false, null);
        if (result.terminated()) {
          stepScope.setNoStepReason(NoStepReason.TERMINATED);
          return;
        }
        if (result.selected() != null) {
          select(stepScope, result.selected());
          return;
        }
        if (result.retained() == null) {
          emptyRounds++;
          if (searchMode == GuidedLocalSearchSearchMode.EXHAUSTIVE) {
            stepScope.setNoStepReason(NoStepReason.NO_ADMISSIBLE_MOVE);
            return;
          }
          if (++unproductiveRounds >= maxUnproductiveRounds) {
            stepScope.setNoStepReason(NoStepReason.SAMPLE_EXHAUSTED);
            return;
          }
          continue;
        }
        unproductiveRounds = 0;
        if (!awaitQuiescence(stepScope)) return;
        int incrementedFeatures;
        if (allLevels()) {
          var scale = calibrations.get(focusLevel).freezeAtPenaltyUpdate();
          incrementedFeatures =
              penalties()
                  .incrementMaximumUtility(
                      tracker.automaticFeatures(), tracker.customFeatures(focusLevel), scale);
        } else {
          incrementedFeatures = penalties().incrementMaximumUtility(tracker.activeFeatures());
        }
        if (incrementedFeatures == 0) {
          if (allLevels() && ++emptyFocusLevels < penaltyTables.size()) {
            advanceFocus();
            currentPenalty = aggregate(tracker, focusLevel, scale());
            continue;
          }
          stepScope.setNoStepReason(NoStepReason.NO_PENALIZABLE_FEATURES);
          return;
        }
        penaltyUpdates++;
        focusPenaltyUpdates++;
        LOGGER.debug(
            "{}GLS penalty update ({}), step ({}), focus level ({}), penalized features ({}), search mode ({}).",
            logIndentation,
            penalties().version(),
            stepScope.getStepIndex(),
            focusLevel,
            incrementedFeatures,
            searchMode);
        updateTrackerPenalties();
        currentPenalty = aggregate(tracker, focusLevel, scale());
        var escape =
            runRound(stepScope, currentScore, currentPenalty, true, result.retained().move());
        if (escape.terminated()) {
          stepScope.setNoStepReason(NoStepReason.TERMINATED);
        } else if (escape.selected() != null) {
          select(stepScope, escape.selected());
        } else {
          // A deterministic retained/re-enumerated move must remain admissible on unchanged state.
          throw new IllegalStateException(
              "Guided Local Search escape lost its previously admissible move at step ("
                  + stepScope.getStepIndex()
                  + "). Check move replay and feature-provider determinism.");
        }
        return;
      }
      stepScope.setNoStepReason(NoStepReason.TERMINATED);
    } finally {
      scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(false);
    }
  }

  private RoundResult<Solution_> runRound(
      LocalSearchStepScope<Solution_> stepScope,
      InnerScore<?> currentScore,
      GuidedLocalSearchNumber currentPenalty,
      boolean escape,
      Move<Solution_> retainedMove) {
    decisionRounds++;
    LOGGER.debug(
        "{}GLS round ({}), step ({}), focus level ({}), guidance version ({}), penalty version ({}), escape ({}).",
        logIndentation,
        decisionRounds,
        stepScope.getStepIndex(),
        focusLevel,
        guidanceVersion,
        penalties().version(),
        escape);
    var context =
        new RoundContext(
            stepScope.getStepIndex(),
            decisionRounds,
            featureResetVersion,
            guidanceVersion,
            focusLevel,
            scale(),
            snapshots());
    Iterator<Move<Solution_>> iterator = repository.iterator();
    RecoveryIterator recovery = null;
    if (escape && searchMode == GuidedLocalSearchSearchMode.SAMPLED) {
      recovery = new RecoveryIterator(iterator, Objects.requireNonNull(retainedMove));
      iterator = recovery;
    }
    long limit = searchMode == GuidedLocalSearchSearchMode.SAMPLED ? sampleSize : Long.MAX_VALUE;
    long attempted = 0;
    int inPlay = 0;
    Candidate<Solution_> retained = null;
    boolean sourceExhausted = false;
    while (true) {
      if (terminated(stepScope)) return new RoundResult<>(null, retained, true);
      Candidate<Solution_> candidate;
      if (pipeline == null) {
        if (attempted >= limit || !iterator.hasNext()) break;
        var move = iterator.next();
        attempted++;
        candidate = evaluateSequential(stepScope, move);
      } else {
        while (inPlay < bufferSize && attempted < limit && !sourceExhausted) {
          if (!iterator.hasNext()) {
            sourceExhausted = true;
            break;
          }
          pipeline.submit(nextMoveIndex, iterator.next(), context);
          nextMoveIndex = Math.incrementExact(nextMoveIndex);
          attempted++;
          inPlay++;
        }
        if (inPlay == 0) break;
        try {
          var result = pipeline.take();
          if (result == null) return new RoundResult<>(null, retained, true);
          inPlay--;
          if (!context.equals(result.context())) {
            throw new IllegalStateException(
                "Guided Local Search received a stale candidate generation.");
          }
          if (!result.isMoveDoable()) continue;
          stepScope.getScoreDirector().incrementCalculationCount();
          transferredCalculationCount++;
          var metadata = (CandidatePenalty) result.metadata();
          if (metadata == null
              && result.score().isFullyAssigned()
              && !result.score().isStructurallyFlawed()) {
            throw new IllegalStateException(
                "Guided Local Search received a valid candidate without its feature evaluation.");
          }
          candidate =
              new Candidate<>(
                  result.move(),
                  result.score(),
                  metadata == null ? GuidedLocalSearchNumber.ZERO : metadata.penalty(),
                  metadata == null ? 0 : metadata.automaticDifferenceCount());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return new RoundResult<>(null, retained, true);
        }
      }
      if (candidate == null) continue;
      stepScope.getPhaseScope().addMoveEvaluationCount(candidate.move(), 1L);
      if (!candidate.score().isStructurallyFlawed()) {
        stepScope.setSelectedMoveCount(stepScope.getSelectedMoveCount() + 1L);
      }
      if (!candidate.score().isFullyAssigned() || candidate.score().isStructurallyFlawed())
        continue;
      if (allLevels()) observeCalibration(candidate, currentScore);
      int prefixComparison =
          allLevels() ? 0 : comparator.compareProtectedPrefix(candidate.score(), currentScore);
      if (prefixComparison < 0) continue;
      if (retained == null) retained = candidate;
      if (escape
          || prefixComparison > 0
          || compareOriginal(
                  candidate.score(), stepScope.getPhaseScope().getSolverScope().getBestScore())
              > 0
          || comparator.compare(
                  candidate.score(),
                  candidate.penalty(),
                  currentScore,
                  currentPenalty,
                  context.scale().denominator())
              > 0) {
        if (recovery != null && candidate.move() == retainedMove) recoveryMoves++;
        return new RoundResult<>(candidate, retained, false);
      }
    }
    return new RoundResult<>(null, retained, false);
  }

  private void observeCalibration(Candidate<Solution_> candidate, InnerScore<?> currentScore) {
    var currentLevels = currentScore.raw().toLevelNumbers();
    var candidateLevels = candidate.score().raw().toLevelNumbers();
    for (int level = 0; level < calibrations.size(); level++) {
      calibrations
          .get(level)
          .observe(
              GuidedLocalSearchNumber.of(candidateLevels[level])
                  .subtract(GuidedLocalSearchNumber.of(currentLevels[level])),
              candidate.automaticDifferenceCount());
    }
  }

  private Candidate<Solution_> evaluateSequential(
      LocalSearchStepScope<Solution_> stepScope, Move<Solution_> move) {
    var director = stepScope.getScoreDirector();
    int moveIndex = nextMoveIndex;
    nextMoveIndex = Math.incrementExact(nextMoveIndex);
    if (move instanceof AbstractSelectorBasedMove<Solution_> selector
        && !selector.isMoveDoable(director)) return null;
    var holder = new GuidedLocalSearchNumber[] {GuidedLocalSearchNumber.ZERO};
    var differenceCount = new int[1];
    var score =
        director.executeTemporaryMove(
            move,
            view -> {
              if (validCandidate(director)) {
                holder[0] = aggregate(tracker, focusLevel, scale());
                if (allLevels()) differenceCount[0] = tracker.automaticDifferenceCount();
                if (assertFromScratch) tracker.assertFromScratch();
              }
            },
            assertFromScratch);
    if (assertUndo) {
      director.assertExpectedUndoMoveScore(
          move,
          stepScope.getPhaseScope().getLastCompletedStepScope().getScore(),
          SolverLifecyclePoint.of(
              -1, stepScope.getPhaseScope().getPhaseIndex(), stepScope.getStepIndex(), moveIndex));
    }
    if (assertFromScratch) tracker.assertFromScratch();
    return new Candidate<>(move, score, holder[0], differenceCount[0]);
  }

  private static boolean validCandidate(InnerScoreDirector<?, ?> director) {
    return director.getWorkingInitScore() == 0 && !isStructurallyFlawed(director);
  }

  private static <Solution_> boolean isStructurallyFlawed(
      InnerScoreDirector<Solution_, ?> director) {
    return director
            .getSolutionDescriptor()
            .getScore(director.getWorkingSolution())
            .structuralScore()
        < 0;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareOriginal(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo(right);
  }

  private void select(LocalSearchStepScope<Solution_> scope, Candidate<Solution_> candidate) {
    scope.setStep(candidate.move());
    scope.setScore(candidate.score());
    scope.setAcceptedMoveCount(scope.getAcceptedMoveCount() + 1L);
    if (LOGGER.isDebugEnabled()) scope.setStepString(candidate.move().toString());
    if (pipeline != null) {
      if (scope.getScoreDirector().requiresFlushing() && scope.getStepIndex() % 100 == 99) {
        scope.getScoreDirector().calculateScore();
      }
      pipeline.applyStep(scope.getStepIndex() + 1, candidate.move(), candidate.score());
    }
  }

  private boolean terminated(LocalSearchStepScope<Solution_> scope) {
    scope.getPhaseScope().getSolverScope().checkYielding();
    return Thread.currentThread().isInterrupted()
        || termination.isPhaseTerminated(scope.getPhaseScope());
  }

  private boolean awaitQuiescence(LocalSearchStepScope<Solution_> scope) {
    if (terminated(scope)) {
      scope.setNoStepReason(NoStepReason.TERMINATED);
      return false;
    }
    try {
      if (pipeline == null || pipeline.awaitEvaluationQuiescence()) return true;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    scope.setNoStepReason(NoStepReason.TERMINATED);
    return false;
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> scope) {
    repository.stepEnded(scope);
    boolean adopted = resetOnPendingMove;
    if (resetOnPendingMove || (resetPenaltiesOnNewBest && scope.getBestScoreImproved())) {
      if (awaitQuiescence(scope)) {
        penaltyTables.forEach(GuidedLocalSearchPenaltyTable::clear);
        updateTrackerPenalties();
        if (resetOnPendingMove) {
          tracker.reset();
          resetCalibrations();
          featureResetVersion = Math.incrementExact(featureResetVersion);
          // Adoption changes the incumbent but is still one real step.
          var phaseScope = scope.getPhaseScope();
          phaseScope.setLastCompletedStepScope(scope);
          initializeFocus(phaseScope);
          repository.phaseEnded(phaseScope);
          repository.phaseStarted(phaseScope);
        }
      }
      resetOnPendingMove = false;
    }
    if (allLevels() && !adopted) {
      focusSteps++;
      boolean firstFeasible = !feasibleSeen && scope.getScore().raw().isFeasible();
      if (firstFeasible
          || focusSteps >= focusStepLimit
          || focusPenaltyUpdates >= focusPenaltyUpdateLimit) {
        if (awaitQuiescence(scope)) {
          if (firstFeasible) {
            feasibleSeen = true;
            setFocus(penaltyTables.size() - 1);
          } else {
            advanceFocus();
          }
        }
      }
    }
    if (assertFromScratch) tracker.assertFromScratch();
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    Throwable failure = null;
    try {
      repository.phaseEnded(phaseScope);
    } catch (RuntimeException | Error error) {
      failure = error;
    }
    if (pipeline != null) {
      try {
        pipeline.close();
        phaseScope.addChildThreadsScoreCalculationCount(
            pipeline.getCalculationCount() - transferredCalculationCount);
        diagnostics = pipeline.getDiagnostics();
        LOGGER.debug("{}GLS move evaluation diagnostics: {}", logIndentation, diagnostics);
      } catch (RuntimeException | Error error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      } finally {
        pipeline = null;
      }
    }
    if (tracker != null) {
      try {
        tracker.close();
      } catch (RuntimeException | Error error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      } finally {
        tracker = null;
      }
    }
    if (failure instanceof Error error) throw error;
    if (failure != null) throw (RuntimeException) failure;
    LOGGER.info(
        "{}Guided Local Search rounds ({}), penalty updates ({}), empty rounds ({}), recovery moves ({}), focus switches ({}).",
        logIndentation,
        decisionRounds,
        penaltyUpdates,
        emptyRounds,
        recoveryMoves,
        focusSwitches);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    repository.solvingEnded(solverScope);
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable failure) {
    if (pipeline != null) {
      try {
        pipeline.abort();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      pipeline = null;
    }
    if (tracker != null) {
      try {
        tracker.close();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      tracker = null;
    }
  }

  @Override
  public long getUncreditedCalculationCount() {
    return pipeline == null
        ? 0L
        : Math.max(0L, pipeline.getCalculationCount() - transferredCalculationCount);
  }

  public MoveEvaluationPipeline.Diagnostics getMoveEvaluationDiagnostics() {
    return diagnostics;
  }

  public Statistics getStatistics() {
    return new Statistics(decisionRounds, penaltyUpdates, emptyRounds, recoveryMoves);
  }

  public int getFocusScoreLevelIndex() {
    return focusLevel;
  }

  public long getFocusSwitchCount() {
    return focusSwitches;
  }

  public GuidedLocalSearchScale getAutomaticScale(int level) {
    return calibrations.get(level).scale();
  }

  public record Statistics(
      long decisionRounds, long penaltyUpdates, long emptyRounds, long recoveryMoves) {}

  private record Candidate<Solution_>(
      Move<Solution_> move,
      InnerScore<?> score,
      GuidedLocalSearchNumber penalty,
      int automaticDifferenceCount) {}

  private record RoundResult<Solution_>(
      Candidate<Solution_> selected, Candidate<Solution_> retained, boolean terminated) {}

  private record RoundContext(
      int stepIndex,
      long roundIndex,
      long featureResetVersion,
      long guidanceVersion,
      int focusLevel,
      GuidedLocalSearchScale scale,
      List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> snapshots)
      implements MoveEvaluationPipeline.EvaluationContext {}

  private record CandidatePenalty(GuidedLocalSearchNumber penalty, int automaticDifferenceCount)
      implements MoveEvaluationPipeline.EvaluationMetadata {}

  private final class WorkerCollector
      implements MoveEvaluationPipeline.CandidateMetadataCollector<Solution_> {
    private final InnerScoreDirector<Solution_, ?> director;
    private final GuidedLocalSearchFeatureTracker<Solution_, Object> workerTracker;
    private long version;
    private long resetVersion;
    private int baselineStep = -1;

    private WorkerCollector(InnerScoreDirector<Solution_, ?> director) {
      this.director = director;
      workerTracker = newTracker(director);
      version = guidanceVersion;
    }

    @Override
    public void beforeEvaluation(MoveEvaluationPipeline.EvaluationContext evaluationContext) {
      var context = (RoundContext) evaluationContext;
      if (resetVersion != context.featureResetVersion()) {
        workerTracker.reset();
        resetVersion = context.featureResetVersion();
        baselineStep = -1;
      }
      if (version != context.guidanceVersion()) {
        if (allLevels()) workerTracker.updatePenalties(context.snapshots());
        else workerTracker.updatePenalties(context.snapshots().get(fixedTarget));
        version = context.guidanceVersion();
      }
      if (allLevels() && baselineStep != context.stepIndex()) {
        workerTracker.markBaseline();
        baselineStep = context.stepIndex();
      }
    }

    @Override
    public MoveEvaluationPipeline.EvaluationMetadata collect(
        SolutionView<Solution_> view,
        Move<Solution_> move,
        MoveEvaluationPipeline.EvaluationContext evaluationContext) {
      var context = (RoundContext) evaluationContext;
      if (!validCandidate(director)) return null;
      var penalty = aggregate(workerTracker, context.focusLevel(), context.scale());
      if (assertFromScratch) workerTracker.assertFromScratch();
      return new CandidatePenalty(
          penalty, allLevels() ? workerTracker.automaticDifferenceCount() : 0);
    }

    @Override
    public void close() {
      workerTracker.close();
    }
  }

  private final class RecoveryIterator implements Iterator<Move<Solution_>> {
    private final Iterator<Move<Solution_>> fresh;
    private final Move<Solution_> retained;
    private int freshCount;
    private boolean recovered;

    private RecoveryIterator(Iterator<Move<Solution_>> fresh, Move<Solution_> retained) {
      this.fresh = fresh;
      this.retained = retained;
    }

    @Override
    public boolean hasNext() {
      return !recovered;
    }

    @Override
    public Move<Solution_> next() {
      if (recovered) throw new NoSuchElementException();
      if (freshCount < sampleSize - 1 && fresh.hasNext()) {
        freshCount++;
        return fresh.next();
      }
      recovered = true;
      return retained;
    }
  }
}
