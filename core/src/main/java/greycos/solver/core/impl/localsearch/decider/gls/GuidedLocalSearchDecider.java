package greycos.solver.core.impl.localsearch.decider.gls;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
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
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.move.PreparedMoveFilters;
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
  private final GuidedLocalSearchFeatureComposition featureComposition;
  private final boolean automaticListOwnershipEnabled;
  private final GuidedLocalSearchSelectionContext<Solution_> selectionContext;
  private final BigDecimal penaltyFactor;
  private final int fixedTarget;
  private final List<GuidedLocalSearchLevelScaleConfig> levelScaleOverrides;
  private final int focusStepLimit;
  private final int focusPenaltyUpdateLimit;
  private final int maxPenaltyUpdatesPerStep;
  private final int excursionStepLimit;
  private final int excursionRepairStepLimit;
  private GuidedLocalSearchScoreComparator comparator;
  private final GuidedLocalSearchSearchMode searchMode;
  private final int sampleSize;
  private final int maxUnproductiveRounds;
  private final boolean resetPenaltiesOnNewBest;
  private final ThreadFactory threadFactory;
  private final int moveThreadCount;
  private final int bufferSize;
  private final List<GuidedLocalSearchPenaltyTable<Object>> penaltyTables = new ArrayList<>();
  private GuidedLocalSearchLearning learning;
  private int hardLevelCount;
  private SearchState state = SearchState.NORMAL;
  private InnerScore<?> epochStartScore;
  private InnerScore<?> epochBestScore;
  private int excursionSteps;
  private int repairSteps;
  private boolean excursionBecameInfeasible;
  private boolean repairExhausted;
  private long excursionsStarted;
  private long excursionsRecovered;
  private long totalExcursionSteps;
  private long totalRepairSteps;
  private long retryExhaustions;
  private ControllerDiagnostics controllerDiagnostics;
  private int focusLevel;
  private int focusSteps;
  private int focusPenaltyUpdates;
  private long guidanceVersion;
  private long focusSwitches;

  private boolean assertFromScratch;
  private boolean assertUndo;
  private boolean assertExpectedStepScore;
  private boolean resetOnPendingMove;
  private LocalSearchPhaseScope<Solution_> repositoryPhaseScope;
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

  // Observation-only phase totals; guidance resets during adoption do not clear them.
  private long attemptedCandidates;
  private long doableCandidates;
  private long admissibleCandidates;
  private long[] penaltyUpdatesByLevel = new long[0];
  private long[] penalizedFeaturesByLevel = new long[0];
  private long[] committedMovesByFocusLevel = new long[0];
  private long[] committedMovesByState = new long[SearchState.values().length];
  private long[] committedMovesByReason = new long[AcceptanceReason.values().length];
  private long priorEligibleOriginProbes;
  private long priorEmittedOrigins;
  private long priorOrdinaryOrigins;
  private AcceptanceReason evaluatedAcceptanceReason;
  private AcceptanceReason selectedAcceptanceReason;
  private int selectedFocusLevel;
  private SearchState selectedState;

  @SuppressWarnings("unchecked")
  public GuidedLocalSearchDecider(
      String logIndentation,
      PhaseTermination<Solution_> termination,
      MoveRepository<Solution_> repository,
      GuidedLocalSearchFeatureProvider<Solution_, ?> provider,
      GuidedLocalSearchGuidanceMode guidanceMode,
      GuidedLocalSearchFeatureComposition featureComposition,
      boolean automaticListOwnershipEnabled,
      BigDecimal penaltyFactor,
      int targetScoreLevelIndex,
      List<GuidedLocalSearchLevelScaleConfig> levelScaleOverrides,
      int focusStepLimit,
      int focusPenaltyUpdateLimit,
      int maxPenaltyUpdatesPerStep,
      int excursionStepLimit,
      int excursionRepairStepLimit,
      GuidedLocalSearchSearchMode searchMode,
      int sampleSize,
      int maxUnproductiveRounds,
      boolean resetPenaltiesOnNewBest,
      GuidedLocalSearchSelectionContext<Solution_> selectionContext,
      ThreadFactory threadFactory,
      int moveThreadCount,
      int bufferSize) {
    this.logIndentation = logIndentation;
    this.termination = termination;
    this.repository = repository;
    this.provider = (GuidedLocalSearchFeatureProvider<Solution_, Object>) provider;
    this.guidanceMode = guidanceMode;
    this.featureComposition = featureComposition;
    this.automaticListOwnershipEnabled = automaticListOwnershipEnabled;
    this.selectionContext = selectionContext;
    this.penaltyFactor = penaltyFactor;
    fixedTarget = targetScoreLevelIndex;
    this.levelScaleOverrides = List.copyOf(levelScaleOverrides);
    this.focusStepLimit = focusStepLimit;
    this.focusPenaltyUpdateLimit = focusPenaltyUpdateLimit;
    this.maxPenaltyUpdatesPerStep = maxPenaltyUpdatesPerStep;
    this.excursionStepLimit = excursionStepLimit;
    this.excursionRepairStepLimit = excursionRepairStepLimit;
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
    resetObservationCounters(0);
    decisionRounds =
        penaltyUpdates = emptyRounds = recoveryMoves = transferredCalculationCount = 0L;
    diagnostics = null;
    focusLevel = -1;
    focusSwitches = 0;
    focusSteps = focusPenaltyUpdates = 0;
    penaltyTables.clear();
    learning = null;
    state = SearchState.NORMAL;
    epochStartScore = epochBestScore = null;
    excursionSteps = repairSteps = 0;
    excursionsStarted =
        excursionsRecovered = totalExcursionSteps = totalRepairSteps = retryExhaustions = 0;
    repairExhausted = false;
    controllerDiagnostics = null;
    if (selectionContext != null) selectionContext.clear();
    repository.solvingStarted(solverScope);
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    resetObservationCounters(phaseScope.getSolverScope().getScoreDefinition().getLevelsSize());
    if (selectionContext != null) selectionContext.clear();
    try {
      startRepositoryPhase(phaseScope);
      initializePhase(phaseScope);
    } catch (RuntimeException | Error failure) {
      solvingError(phaseScope.getSolverScope(), failure);
      throw failure;
    }
  }

  private void startRepositoryPhase(LocalSearchPhaseScope<Solution_> phaseScope) {
    if (repositoryPhaseScope != null) {
      throw new IllegalStateException("The GLS move repository already has an active phase.");
    }
    // A repository can acquire resources before its phase-start callback fails.
    repositoryPhaseScope = phaseScope;
    repository.phaseStarted(phaseScope);
  }

  private void endRepositoryPhase() {
    var phaseScope = repositoryPhaseScope;
    if (phaseScope == null) return;
    // Cleanup is attempted once even if the callback itself throws.
    repositoryPhaseScope = null;
    repository.phaseEnded(phaseScope);
  }

  private void initializePhase(LocalSearchPhaseScope<Solution_> phaseScope) {
    penaltyTables.clear();
    int levelCount = phaseScope.getSolverScope().getScoreDefinition().getLevelsSize();
    for (int level = 0; level < levelCount; level++) {
      penaltyTables.add(new GuidedLocalSearchPenaltyTable<>());
    }
    resetLearning();
    guidanceVersion = 0;
    hardLevelCount = phaseScope.getSolverScope().getScoreDefinition().getFeasibleLevelsSize();
    initializeFocus(phaseScope);
    focusSwitches = 0;
    decisionRounds =
        penaltyUpdates = emptyRounds = recoveryMoves = transferredCalculationCount = 0L;
    diagnostics = null;
    controllerDiagnostics = null;
    excursionsStarted =
        excursionsRecovered = totalExcursionSteps = totalRepairSteps = retryExhaustions = 0;
    resetOnPendingMove = false;
    featureResetVersion = 0;
    tracker = newTracker(phaseScope.getScoreDirector());
    publishOriginPriorities();
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
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      throw failure;
    }
  }

  private void resetObservationCounters(int levels) {
    attemptedCandidates = doableCandidates = admissibleCandidates = 0L;
    penaltyUpdatesByLevel = new long[levels];
    penalizedFeaturesByLevel = new long[levels];
    committedMovesByFocusLevel = new long[levels];
    committedMovesByState = new long[SearchState.values().length];
    committedMovesByReason = new long[AcceptanceReason.values().length];
    priorEligibleOriginProbes = priorEmittedOrigins = priorOrdinaryOrigins = 0L;
    evaluatedAcceptanceReason = selectedAcceptanceReason = null;
    selectedState = null;
    selectedFocusLevel = -1;
  }

  private void cacheAndClearOriginDiagnostics() {
    if (selectionContext == null) return;
    var origins = selectionContext.getDiagnostics();
    priorEligibleOriginProbes += origins.eligibleOriginProbes();
    priorEmittedOrigins += origins.emittedOrigins();
    priorOrdinaryOrigins += origins.ordinaryOrigins();
    selectionContext.clear();
  }

  private void resetLearning() {
    var overrides = new ArrayList<BigDecimal>(penaltyTables.size());
    for (int level = 0; level < penaltyTables.size(); level++) {
      BigDecimal override = null;
      for (var config : levelScaleOverrides) {
        if (config.getScoreLevelIndex() == level) override = config.getScale();
      }
      overrides.add(override);
    }
    learning = new GuidedLocalSearchLearning(overrides);
  }

  private GuidedLocalSearchFeatureTracker<Solution_, Object> newTracker(
      InnerScoreDirector<Solution_, ?> director) {
    var created =
        GuidedLocalSearchFeatureTracker.attachAutomatic(
            director,
            provider,
            snapshots(),
            tracker == null ? null : tracker.identityRegistry(),
            allLevels() ? penaltyTables.size() - 1 : fixedTarget,
            automaticEnabled(),
            automaticListOwnershipEnabled);
    created.updateLearning(learning.snapshot());
    return created;
  }

  private boolean allLevels() {
    return guidanceMode == GuidedLocalSearchGuidanceMode.ALL_LEVELS;
  }

  private boolean automaticEnabled() {
    return featureComposition != GuidedLocalSearchFeatureComposition.CUSTOM;
  }

  private GuidedLocalSearchPenaltyTable<Object> penalties() {
    return penaltyTables.get(focusLevel);
  }

  private List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> snapshots() {
    return penaltyTables.stream().map(GuidedLocalSearchPenaltyTable::snapshot).toList();
  }

  private GuidedLocalSearchScale scale() {
    return learning.snapshot().scales().get(focusLevel).dividedBy(4);
  }

  private GuidedLocalSearchNumber aggregate(
      GuidedLocalSearchFeatureTracker<Solution_, Object> featureTracker,
      int level,
      GuidedLocalSearchScale scale) {
    var guidance = featureTracker.aggregates();
    return guidance
        .automatic()
        .get(level)
        .multiply(scale.numerator())
        .add(guidance.custom().get(level).multiply(scale.denominator()));
  }

  private void updateTrackerPenalties() {
    tracker.updatePenalties(snapshots());
    guidanceVersion = Math.incrementExact(guidanceVersion);
    publishOriginPriorities();
  }

  private void pruneInactivePenaltyMetadata() {
    for (int level = 0; level < penaltyTables.size(); level++) {
      penaltyTables
          .get(level)
          .pruneInactive(tracker.automaticFeatures(), tracker.customFeatures(level));
    }
  }

  private void publishOriginPriorities() {
    if (selectionContext != null) selectionContext.publish(tracker.originPriorities(focusLevel));
  }

  private void initializeFocus(LocalSearchPhaseScope<Solution_> phaseScope) {
    var score = phaseScope.getLastCompletedStepScope().getScore();
    state = SearchState.NORMAL;
    excursionSteps = repairSteps = 0;
    excursionBecameInfeasible = repairExhausted = false;
    setFocus(allLevels() ? firstViolatedHardLevelOrLast(score) : fixedTarget, score);
  }

  private int firstViolatedHardLevelOrLast(InnerScore<?> score) {
    var levels = score.raw().toLevelNumbers();
    for (int level = 0; level < hardLevelCount; level++) {
      if (GuidedLocalSearchNumber.of(levels[level]).signum() < 0) return level;
    }
    return penaltyTables.size() - 1;
  }

  private void setFocus(int level, InnerScore<?> currentScore) {
    if (focusLevel != level) focusSwitches++;
    focusLevel = level;
    focusSteps = focusPenaltyUpdates = 0;
    epochStartScore = epochBestScore = currentScore;
    comparator = new GuidedLocalSearchScoreComparator(level, penaltyFactor);
    guidanceVersion = Math.incrementExact(guidanceVersion);
  }

  private boolean advanceStagnantFocus(InnerScore<?> currentScore) {
    if (state == SearchState.REPAIR) return false;
    int nextFocus = allLevels() ? Math.max(0, focusLevel - 1) : fixedTarget;
    if (state == SearchState.EXCURSION) {
      if (!allLevels() || nextFocus == focusLevel) return false;
      setFocus(nextFocus, currentScore);
      return true;
    }
    if (nextFocus < hardLevelCount) {
      state = SearchState.EXCURSION;
      excursionSteps = repairSteps = 0;
      excursionBecameInfeasible = !currentScore.raw().isFeasible();
      excursionsStarted++;
    } else if (!allLevels() || nextFocus == focusLevel) {
      return false;
    }
    setFocus(nextFocus, currentScore);
    return true;
  }

  private void finishFocusEpoch(InnerScore<?> currentScore) {
    if (comparePrefix(epochBestScore, epochStartScore, focusLevel + 1) > 0
        || !advanceStagnantFocus(currentScore)) {
      setFocus(focusLevel, currentScore);
    }
  }

  private void enterRepair(InnerScore<?> currentScore) {
    state = SearchState.REPAIR;
    repairSteps = 0;
    setFocus(allLevels() ? firstViolatedHardLevelOrLast(currentScore) : fixedTarget, currentScore);
  }

  private void recoverFeasibility(InnerScore<?> currentScore) {
    excursionsRecovered++;
    state = SearchState.NORMAL;
    excursionBecameInfeasible = false;
    setFocus(allLevels() ? penaltyTables.size() - 1 : fixedTarget, currentScore);
  }

  private static int comparePrefix(InnerScore<?> left, InnerScore<?> right, int levelCount) {
    var leftLevels = left.raw().toLevelNumbers();
    var rightLevels = right.raw().toLevelNumbers();
    for (int level = 0; level < levelCount; level++) {
      int comparison =
          GuidedLocalSearchNumber.of(leftLevels[level])
              .compareTo(GuidedLocalSearchNumber.of(rightLevels[level]));
      if (comparison != 0) return comparison;
    }
    return 0;
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
    boolean previousTemporaryState = scoreDirector.isAllChangesWillBeUndoneBeforeStepEnds();
    scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(true);
    try {
      if (pipeline != null) pipeline.startNextStep(stepScope.getStepIndex());
      if (terminated(stepScope)) {
        stepScope.setNoStepReason(NoStepReason.TERMINATED);
        return;
      }
      if (repairExhausted) {
        stepScope.setNoStepReason(NoStepReason.EXCURSION_REPAIR_EXHAUSTED);
        return;
      }
      var pending = stepScope.getPhaseScope().getSolverScope().consumePendingMove();
      if (pending != null) {
        resetOnPendingMove = pending.requiresReset();
        var score = scoreDirector.executeTemporaryMove(pending.move(), assertFromScratch);
        stepScope.getPhaseScope().addMoveEvaluationCount(pending.move(), 1L);
        stepScope.setSelectedMoveCount(1L);
        select(
            stepScope,
            new Candidate<>(
                pending.move(),
                score,
                GuidedLocalSearchNumber.ZERO,
                GuidedLocalSearchFeatureTracker.AutomaticDelta.EMPTY,
                true),
            AcceptanceReason.PENDING_MOVE);
        return;
      }
      InnerScore<?> currentScore = stepScope.getPhaseScope().getLastCompletedStepScope().getScore();
      tracker.markBaseline();
      var currentPenalty = aggregate(tracker, focusLevel, scale());
      if (assertFromScratch) tracker.assertFromScratch();
      int unproductiveRounds = 0;
      int decisionPenaltyUpdates = 0;
      int transitionsWithoutPenalty = 0;
      var replay = new GuidedLocalSearchCandidateReplay<Solution_>();
      while (!terminated(stepScope)) {
        var result = runRound(stepScope, currentScore, currentPenalty, replay);
        if (result.terminated()) {
          stepScope.setNoStepReason(NoStepReason.TERMINATED);
          return;
        }
        if (result.selected() != null) {
          select(stepScope, result.selected(), evaluatedAcceptanceReason);
          return;
        }
        if (!result.admissible()) {
          emptyRounds++;
          if (searchMode == GuidedLocalSearchSearchMode.SAMPLED
              && ++unproductiveRounds < maxUnproductiveRounds) continue;
          boolean canEscalate = allLevels() ? result.valid() : result.prefixAdmissible();
          if (canEscalate && transitionsWithoutPenalty < penaltyTables.size() + 2) {
            if (!awaitQuiescence(stepScope)) return;
            if (advanceStagnantFocus(currentScore)) {
              transitionsWithoutPenalty++;
              unproductiveRounds = 0;
              publishOriginPriorities();
              currentPenalty = aggregate(tracker, focusLevel, scale());
              continue;
            }
          }
          finishWithoutMove(
              stepScope,
              searchMode == GuidedLocalSearchSearchMode.EXHAUSTIVE
                  ? NoStepReason.NO_ADMISSIBLE_MOVE
                  : NoStepReason.SAMPLE_EXHAUSTED);
          return;
        }
        unproductiveRounds = 0;
        if (decisionPenaltyUpdates >= maxPenaltyUpdatesPerStep) {
          retryExhaustions++;
          finishWithoutMove(stepScope, NoStepReason.GUIDED_RETRY_EXHAUSTED);
          return;
        }
        if (!awaitQuiescence(stepScope)) return;
        // Workers are quiescent: publish learning before calculating this penalty batch.
        pruneInactivePenaltyMetadata();
        tracker.updateLearning(learning.publish(tracker.automaticFeatures().keySet(), snapshots()));
        guidanceVersion = Math.incrementExact(guidanceVersion);
        int incrementedFeatures =
            penalties()
                .incrementMaximumUtility(
                    tracker.automaticFeatures(focusLevel),
                    tracker.customFeaturesForPenaltyUpdate(focusLevel),
                    scale(),
                    stepScope.getWorkingRandom().acceptorUsage());
        if (incrementedFeatures == 0) {
          if (state != SearchState.REPAIR
              && allLevels()
              && focusLevel > 0
              && ++transitionsWithoutPenalty <= penaltyTables.size()) {
            if (state == SearchState.NORMAL) advanceStagnantFocus(currentScore);
            else setFocus(focusLevel - 1, currentScore);
            publishOriginPriorities();
            currentPenalty = aggregate(tracker, focusLevel, scale());
            continue;
          }
          finishWithoutMove(stepScope, NoStepReason.NO_PENALIZABLE_FEATURES);
          return;
        }
        penaltyUpdates++;
        penaltyUpdatesByLevel[focusLevel]++;
        penalizedFeaturesByLevel[focusLevel] += incrementedFeatures;
        decisionPenaltyUpdates++;
        focusPenaltyUpdates++;
        transitionsWithoutPenalty = 0;
        LOGGER.debug(
            "{}GLS penalty update ({}), step ({}), focus level ({}), penalized features ({}), state ({}).",
            logIndentation,
            penalties().version(),
            stepScope.getStepIndex(),
            focusLevel,
            incrementedFeatures,
            state);
        if (focusPenaltyUpdates >= focusPenaltyUpdateLimit) finishFocusEpoch(currentScore);
        updateTrackerPenalties();
        currentPenalty = aggregate(tracker, focusLevel, scale());
      }
      stepScope.setNoStepReason(NoStepReason.TERMINATED);
    } finally {
      if (pipeline != null && stepScope.getStep() == null) pipeline.cancelStep();
      scoreDirector.setAllChangesWillBeUndoneBeforeStepEnds(previousTemporaryState);
    }
  }

  private void finishWithoutMove(LocalSearchStepScope<Solution_> stepScope, NoStepReason reason) {
    stepScope.setNoStepReason(
        state == SearchState.REPAIR ? NoStepReason.EXCURSION_REPAIR_EXHAUSTED : reason);
  }

  private RoundResult<Solution_> runRound(
      LocalSearchStepScope<Solution_> stepScope,
      InnerScore<?> currentScore,
      GuidedLocalSearchNumber currentPenalty,
      GuidedLocalSearchCandidateReplay<Solution_> replay) {
    decisionRounds++;
    LOGGER.debug(
        "{}GLS round ({}), step ({}), focus level ({}), guidance version ({}), penalty version ({}), state ({}).",
        logIndentation,
        decisionRounds,
        stepScope.getStepIndex(),
        focusLevel,
        guidanceVersion,
        penalties().version(),
        state);
    var context =
        new RoundContext(
            stepScope.getStepIndex(),
            decisionRounds,
            featureResetVersion,
            guidanceVersion,
            focusLevel,
            scale(),
            snapshots(),
            learning.snapshot());
    var iterator =
        replay.round(
            repository.iterator(),
            move -> selectionContext == null || selectionContext.isOrdinaryCandidate(move));
    long limit = searchMode == GuidedLocalSearchSearchMode.SAMPLED ? sampleSize : Long.MAX_VALUE;
    long attempted = 0;
    int inPlay = 0;
    boolean admissible = false;
    boolean valid = false;
    boolean prefixAdmissible = false;
    boolean sourceExhausted = false;
    while (true) {
      if (terminated(stepScope))
        return new RoundResult<>(null, admissible, valid, prefixAdmissible, true);
      Candidate<Solution_> candidate;
      if (pipeline == null) {
        if (attempted >= limit || !iterator.hasNext()) break;
        var selection = iterator.next();
        attempted++;
        attemptedCandidates++;
        candidate = evaluateSequential(stepScope, selection, replay);
      } else {
        while (inPlay < bufferSize && attempted < limit && !sourceExhausted) {
          if (!iterator.hasNext()) {
            sourceExhausted = true;
            break;
          }
          var selection = iterator.next();
          pipeline.submit(
              nextMoveIndex,
              selection.move(),
              new CandidateContext(context, selection.ordinaryOrigin(), selection.retainResult()));
          nextMoveIndex = Math.incrementExact(nextMoveIndex);
          attempted++;
          attemptedCandidates++;
          inPlay++;
        }
        if (inPlay == 0) break;
        try {
          var result = pipeline.take();
          if (result == null)
            return new RoundResult<>(null, admissible, valid, prefixAdmissible, true);
          inPlay--;
          var candidateContext = (CandidateContext) result.context();
          if (candidateContext == null || !context.equals(candidateContext.round())) {
            throw new IllegalStateException(
                "Guided Local Search received a stale candidate generation.");
          }
          stepScope.getScoreDirector().incrementCalculationCount(result.calculationCount());
          transferredCalculationCount += result.calculationCount();
          if (!result.isMoveDoable()) continue;
          var preparedMove =
              PreparedMoveFilters.filter(result.move(), stepScope.getScoreDirector());
          if (preparedMove == null) continue;
          var metadata = (CandidatePenalty) result.metadata();
          if (metadata == null
              && result.score().isFullyAssigned()
              && !result.score().isStructurallyFlawed()) {
            throw new IllegalStateException(
                "Guided Local Search received a valid candidate without its feature evaluation.");
          }
          if (candidateContext.retainResult()
              && result.score().isFullyAssigned()
              && !result.score().isStructurallyFlawed()) {
            replay.retain(result.move(), candidateContext.ordinaryOrigin());
          }
          candidate =
              new Candidate<>(
                  preparedMove,
                  result.score(),
                  metadata == null ? GuidedLocalSearchNumber.ZERO : metadata.penalty(),
                  metadata == null
                      ? GuidedLocalSearchFeatureTracker.AutomaticDelta.EMPTY
                      : metadata.automaticDelta(),
                  candidateContext.ordinaryOrigin());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return new RoundResult<>(null, admissible, valid, prefixAdmissible, true);
        }
      }
      if (candidate == null) continue;
      doableCandidates++;
      stepScope.getPhaseScope().addMoveEvaluationCount(candidate.move(), 1L);
      if (!candidate.score().isStructurallyFlawed()) {
        stepScope.setSelectedMoveCount(stepScope.getSelectedMoveCount() + 1L);
      }
      if (!candidate.score().isFullyAssigned() || candidate.score().isStructurallyFlawed())
        continue;
      valid = true;
      learning.observe(
          candidate.automaticDelta(),
          candidate.score().raw().toLevelNumbers(),
          currentScore.raw().toLevelNumbers(),
          candidate.ordinaryOrigin());
      if (comparator.compareProtectedPrefix(candidate.score(), currentScore) < 0) continue;
      prefixAdmissible = true;
      if (state != SearchState.EXCURSION
          && (comparePrefix(candidate.score(), currentScore, hardLevelCount) < 0
              || (currentScore.raw().isFeasible() && !candidate.score().raw().isFeasible())))
        continue;
      admissible = true;
      admissibleCandidates++;
      boolean aspiration =
          compareOriginal(
                  candidate.score(), stepScope.getPhaseScope().getSolverScope().getBestScore())
              > 0;
      if (aspiration
          || comparator.compare(
                  candidate.score(),
                  candidate.penalty(),
                  currentScore,
                  currentPenalty,
                  context.scale().denominator())
              > 0) {
        evaluatedAcceptanceReason =
            aspiration
                ? AcceptanceReason.ORIGINAL_BEST_ASPIRATION
                : AcceptanceReason.GUIDED_IMPROVEMENT;
        return new RoundResult<>(candidate, true, true, true, false);
      }
    }
    return new RoundResult<>(null, admissible, valid, prefixAdmissible, false);
  }

  private Candidate<Solution_> evaluateSequential(
      LocalSearchStepScope<Solution_> stepScope,
      GuidedLocalSearchCandidateReplay.Selection<Solution_> selection,
      GuidedLocalSearchCandidateReplay<Solution_> replay) {
    var director = stepScope.getScoreDirector();
    var move = selection.move();
    int moveIndex = nextMoveIndex;
    nextMoveIndex = Math.incrementExact(nextMoveIndex);
    if (!(move instanceof PreparableMove<Solution_>)
        && move instanceof AbstractSelectorBasedMove<Solution_> selector
        && !selector.isMoveDoable(director)) return null;
    var holder = new GuidedLocalSearchNumber[] {GuidedLocalSearchNumber.ZERO};
    var automaticDelta =
        new GuidedLocalSearchFeatureTracker.AutomaticDelta[] {
          GuidedLocalSearchFeatureTracker.AutomaticDelta.EMPTY
        };
    BiConsumer<SolutionView<Solution_>, Move<Solution_>> finalStateConsumer =
        (view, preparedMove) -> {
          if (validCandidate(director)) {
            holder[0] = aggregate(tracker, focusLevel, scale());
            automaticDelta[0] = tracker.automaticDelta();
            if (assertFromScratch) tracker.assertFromScratch();
          }
        };
    InnerScore<?> score;
    if (move instanceof PreparableMove<Solution_> preparableMove) {
      var result =
          preparableMove.prepare(
              director,
              () -> {
                if (terminated(stepScope)) {
                  throw new CancellationException(
                      "Guided local search move preparation terminated.");
                }
              },
              assertFromScratch,
              finalStateConsumer);
      if (result.status() != PreparedMoveEvaluation.Status.EVALUATED) return null;
      move = result.move();
      score = result.score();
    } else {
      var evaluatedMove = move;
      score =
          director.executeTemporaryMove(
              move, view -> finalStateConsumer.accept(view, evaluatedMove), assertFromScratch);
    }
    var filteredMove = PreparedMoveFilters.filter(move, director);
    if (filteredMove == null) return null;
    if (selection.retainResult() && score.isFullyAssigned() && !score.isStructurallyFlawed()) {
      replay.retain(move, selection.ordinaryOrigin());
    }
    move = filteredMove;
    if (assertUndo) {
      director.assertExpectedUndoMoveScore(
          move,
          stepScope.getPhaseScope().getLastCompletedStepScope().getScore(),
          SolverLifecyclePoint.of(
              -1, stepScope.getPhaseScope().getPhaseIndex(), stepScope.getStepIndex(), moveIndex));
    }
    if (assertFromScratch) tracker.assertFromScratch();
    return new Candidate<>(move, score, holder[0], automaticDelta[0], selection.ordinaryOrigin());
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

  private void select(
      LocalSearchStepScope<Solution_> scope,
      Candidate<Solution_> candidate,
      AcceptanceReason acceptanceReason) {
    selectedFocusLevel = focusLevel;
    selectedState = state;
    selectedAcceptanceReason = acceptanceReason;
    scope.setStep(candidate.move());
    scope.setScore(candidate.score());
    scope.setAcceptedMoveCount(scope.getAcceptedMoveCount() + 1L);
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
    // Capture at selection; adoption and the following epoch transition may change focus/state.
    if (selectedAcceptanceReason != null) {
      committedMovesByFocusLevel[selectedFocusLevel]++;
      committedMovesByState[selectedState.ordinal()]++;
      committedMovesByReason[selectedAcceptanceReason.ordinal()]++;
      selectedAcceptanceReason = null;
    }
    repository.stepEnded(scope);
    boolean adopted = resetOnPendingMove;
    if (resetOnPendingMove || (resetPenaltiesOnNewBest && scope.getBestScoreImproved())) {
      if (awaitQuiescence(scope)) {
        if (resetOnPendingMove) penaltyTables.forEach(GuidedLocalSearchPenaltyTable::clear);
        else penaltyTables.forEach(GuidedLocalSearchPenaltyTable::clearCounts);
        if (resetOnPendingMove) {
          tracker.reset();
          resetLearning();
          tracker.updateLearning(learning.snapshot());
          featureResetVersion = Math.incrementExact(featureResetVersion);
          var phaseScope = scope.getPhaseScope();
          phaseScope.setLastCompletedStepScope(scope);
          initializeFocus(phaseScope);
          cacheAndClearOriginDiagnostics();
          endRepositoryPhase();
          startRepositoryPhase(phaseScope);
        }
        pruneInactivePenaltyMetadata();
        updateTrackerPenalties();
      }
      resetOnPendingMove = false;
    }
    if (!adopted) {
      focusSteps++;
      if (comparePrefix(scope.getScore(), epochBestScore, focusLevel + 1) > 0)
        epochBestScore = scope.getScore();
      boolean feasible = scope.getScore().raw().isFeasible();
      if (state == SearchState.EXCURSION) {
        excursionSteps++;
        totalExcursionSteps++;
        excursionBecameInfeasible |= !feasible;
      } else if (state == SearchState.REPAIR) {
        repairSteps++;
        totalRepairSteps++;
      }
      boolean transition =
          (state != SearchState.NORMAL && feasible && excursionBecameInfeasible)
              || (state == SearchState.EXCURSION && excursionSteps >= excursionStepLimit)
              || (state == SearchState.REPAIR
                  && (repairSteps >= excursionRepairStepLimit
                      || (allLevels()
                          && firstViolatedHardLevelOrLast(scope.getScore()) != focusLevel)))
              || (allLevels()
                  && state == SearchState.NORMAL
                  && feasible
                  && focusLevel < hardLevelCount)
              || focusSteps >= focusStepLimit;
      if (transition && awaitQuiescence(scope)) {
        if (state != SearchState.NORMAL
            && feasible
            && (excursionBecameInfeasible || excursionSteps >= excursionStepLimit)) {
          recoverFeasibility(scope.getScore());
        } else if (state == SearchState.EXCURSION && excursionSteps >= excursionStepLimit) {
          enterRepair(scope.getScore());
        } else if (state == SearchState.REPAIR) {
          if (repairSteps >= excursionRepairStepLimit) repairExhausted = true;
          else
            setFocus(
                allLevels() ? firstViolatedHardLevelOrLast(scope.getScore()) : fixedTarget,
                scope.getScore());
        } else if (allLevels()
            && state == SearchState.NORMAL
            && feasible
            && focusLevel < hardLevelCount) {
          setFocus(penaltyTables.size() - 1, scope.getScore());
        } else {
          finishFocusEpoch(scope.getScore());
        }
        publishOriginPriorities();
      }
    }
    if (selectionContext != null && awaitQuiescence(scope)) publishOriginPriorities();
    if (assertFromScratch) tracker.assertFromScratch();
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    Throwable failure = null;
    try {
      controllerDiagnostics = snapshotControllerDiagnostics();
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
    try {
      endRepositoryPhase();
    } catch (RuntimeException | Error error) {
      if (failure == null) failure = error;
      else if (failure != error) failure.addSuppressed(error);
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
    cacheAndClearOriginDiagnostics();
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
    try {
      endRepositoryPhase();
    } catch (RuntimeException | Error cleanup) {
      if (cleanup != failure) failure.addSuppressed(cleanup);
    }
    if (tracker != null) {
      try {
        tracker.close();
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
      tracker = null;
    }
    cacheAndClearOriginDiagnostics();
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
    return learning.snapshot().scales().get(level);
  }

  public ControllerDiagnostics getControllerDiagnostics() {
    return tracker == null && controllerDiagnostics != null
        ? controllerDiagnostics
        : snapshotControllerDiagnostics();
  }

  private ControllerDiagnostics snapshotControllerDiagnostics() {
    var active = tracker == null ? java.util.Set.of() : tracker.automaticFeatures().keySet();
    var allActive = new java.util.HashSet<Object>(active);
    if (tracker != null) {
      for (int level = 0; level < penaltyTables.size(); level++)
        allActive.addAll(tracker.customFeatures(level).keySet());
    }
    var penalized = new java.util.HashSet<Object>();
    penaltyTables.forEach(table -> penalized.addAll(table.snapshot().counts().keySet()));
    penalized.removeAll(allActive);
    var learned = learning == null ? null : learning.snapshot();
    var origins = selectionContext == null ? null : selectionContext.getDiagnostics();
    return new ControllerDiagnostics(
        guidanceMode,
        featureComposition,
        penaltyFactor,
        focusLevel,
        state,
        decisionRounds,
        penaltyUpdates,
        focusSwitches,
        excursionsStarted,
        excursionsRecovered,
        totalExcursionSteps,
        totalRepairSteps,
        retryExhaustions,
        maxPenaltyUpdatesPerStep,
        excursionStepLimit,
        excursionRepairStepLimit,
        learned == null ? List.of() : learned.scales(),
        learned == null ? List.of() : learned.calibrated(),
        learned == null ? 0 : learned.retainedFeatureCount(),
        active.size(),
        penalized.size(),
        attemptedCandidates,
        doableCandidates,
        admissibleCandidates,
        immutableCounts(penaltyUpdatesByLevel),
        immutableCounts(penalizedFeaturesByLevel),
        learned == null ? List.of() : learned.scaleObservationCounts(),
        immutableCounts(committedMovesByFocusLevel),
        immutableCounts(committedMovesByState),
        immutableCounts(committedMovesByReason),
        priorEligibleOriginProbes + (origins == null ? 0L : origins.eligibleOriginProbes()),
        priorEmittedOrigins + (origins == null ? 0L : origins.emittedOrigins()),
        priorOrdinaryOrigins + (origins == null ? 0L : origins.ordinaryOrigins()));
  }

  private static List<Long> immutableCounts(long[] counts) {
    var snapshot = new ArrayList<Long>(counts.length);
    for (long count : counts) snapshot.add(count);
    return List.copyOf(snapshot);
  }

  public enum AcceptanceReason {
    ORIGINAL_BEST_ASPIRATION,
    GUIDED_IMPROVEMENT,
    PENDING_MOVE
  }

  public enum SearchState {
    NORMAL,
    EXCURSION,
    REPAIR
  }

  /**
   * Candidate counts cover search rounds: attempts include submitted work canceled after selection,
   * while doable/admissible counts include only coordinator-consumed results. Pending adoptions are
   * reported separately in committedMovesByReason. Per-state and per-reason lists follow enum
   * order. scaleObservationCounts contains current published rolling-window sizes, not phase
   * totals.
   */
  public record ControllerDiagnostics(
      GuidedLocalSearchGuidanceMode guidanceMode,
      GuidedLocalSearchFeatureComposition featureComposition,
      BigDecimal penaltyFactor,
      int focusLevel,
      SearchState state,
      long decisionRounds,
      long penaltyUpdates,
      long focusSwitches,
      long excursionsStarted,
      long excursionsRecovered,
      long excursionSteps,
      long repairSteps,
      long retryExhaustions,
      int maxPenaltyUpdatesPerStep,
      int excursionStepLimit,
      int excursionRepairStepLimit,
      List<GuidedLocalSearchScale> automaticScales,
      List<Boolean> calibratedLevels,
      int retainedLearnedFeatures,
      int activeAutomaticFeatures,
      int dormantPenalizedFeatures,
      long attemptedCandidates,
      long doableCandidates,
      long admissibleCandidates,
      List<Long> penaltyUpdatesByLevel,
      List<Long> penalizedFeaturesByLevel,
      List<Integer> scaleObservationCounts,
      List<Long> committedMovesByFocusLevel,
      List<Long> committedMovesByState,
      List<Long> committedMovesByReason,
      long eligibleOriginProbes,
      long emittedOrigins,
      long ordinaryOrigins) {
    public ControllerDiagnostics {
      automaticScales = List.copyOf(automaticScales);
      calibratedLevels = List.copyOf(calibratedLevels);
      penaltyUpdatesByLevel = List.copyOf(penaltyUpdatesByLevel);
      penalizedFeaturesByLevel = List.copyOf(penalizedFeaturesByLevel);
      scaleObservationCounts = List.copyOf(scaleObservationCounts);
      committedMovesByFocusLevel = List.copyOf(committedMovesByFocusLevel);
      committedMovesByState = List.copyOf(committedMovesByState);
      committedMovesByReason = List.copyOf(committedMovesByReason);
    }
  }

  public record Statistics(
      long decisionRounds, long penaltyUpdates, long emptyRounds, long recoveryMoves) {}

  private record Candidate<Solution_>(
      Move<Solution_> move,
      InnerScore<?> score,
      GuidedLocalSearchNumber penalty,
      GuidedLocalSearchFeatureTracker.AutomaticDelta automaticDelta,
      boolean ordinaryOrigin) {}

  private record RoundResult<Solution_>(
      Candidate<Solution_> selected,
      boolean admissible,
      boolean valid,
      boolean prefixAdmissible,
      boolean terminated) {}

  private record RoundContext(
      int stepIndex,
      long roundIndex,
      long featureResetVersion,
      long guidanceVersion,
      int focusLevel,
      GuidedLocalSearchScale scale,
      List<GuidedLocalSearchPenaltyTable.Snapshot<Object>> snapshots,
      GuidedLocalSearchLearning.Snapshot learning) {}

  private record CandidateContext(RoundContext round, boolean ordinaryOrigin, boolean retainResult)
      implements MoveEvaluationPipeline.EvaluationContext {}

  private record CandidatePenalty(
      GuidedLocalSearchNumber penalty,
      GuidedLocalSearchFeatureTracker.AutomaticDelta automaticDelta)
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
      var context = ((CandidateContext) evaluationContext).round();
      if (resetVersion != context.featureResetVersion()) {
        workerTracker.reset();
        resetVersion = context.featureResetVersion();
        baselineStep = -1;
      }
      if (version != context.guidanceVersion()) {
        workerTracker.updatePenalties(context.snapshots());
        workerTracker.updateLearning(context.learning());
        version = context.guidanceVersion();
      }
      if (baselineStep != context.stepIndex()) {
        workerTracker.markBaseline();
        baselineStep = context.stepIndex();
      }
    }

    @Override
    public MoveEvaluationPipeline.EvaluationMetadata collect(
        SolutionView<Solution_> view,
        Move<Solution_> move,
        MoveEvaluationPipeline.EvaluationContext evaluationContext) {
      var context = ((CandidateContext) evaluationContext).round();
      if (!validCandidate(director)) return null;
      var penalty = aggregate(workerTracker, context.focusLevel(), context.scale());
      if (assertFromScratch) workerTracker.assertFromScratch();
      return new CandidatePenalty(penalty, workerTracker.automaticDelta());
    }

    @Override
    public void close() {
      workerTracker.close();
    }
  }
}
