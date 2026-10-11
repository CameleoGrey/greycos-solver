package greycos.solver.core.impl.solver.termination;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.Nullable;

/**
 * A coordinator's termination expression over committed aggregate work. This object is never an
 * episode termination or cooling source. Its persistent progress survives episode and model
 * restarts, while ordinary episode termination keeps its own lifecycle and fixed horizon.
 *
 * <p>Record summaries in logical source order, only after all admitted work reaches a safe
 * boundary. Move and score limits are predicates at that boundary: already admitted work may
 * overshoot them. Completed-step grants use {@link #remainingStepCount()} before dispatch; unused
 * grants become available only when the entire round commits.
 */
public final class ResumableSearchBudget<Solution_> {

  public static final long UNBOUNDED = Long.MAX_VALUE;

  private final String scopeKey;
  private final Origin origin;
  private final Clock clock;
  private final PersistentState persistentState;
  private final Progress progress;
  private final Progress phaseProgress;
  private final @Nullable String phaseStateKey;
  private final Node expression;
  private final List<CountNode> countNodes = new ArrayList<>();

  public ResumableSearchBudget(
      String scopeKey,
      @Nullable TerminationConfig config,
      HeuristicConfigPolicy<Solution_> configPolicy,
      PersistentState state,
      Clock clock,
      long scopeStartMillis) {
    this(
        scopeKey,
        null,
        Origin.PHASE,
        config == null
            ? null
            : TerminationFactory.<Solution_>create(config).buildTermination(configPolicy),
        null,
        state,
        clock,
        scopeStartMillis);
  }

  private ResumableSearchBudget(
      String scopeKey,
      @Nullable String phaseScopeKey,
      Origin origin,
      @Nullable Termination<Solution_> definition,
      @Nullable SolverScope<Solution_> ownerScope,
      PersistentState state,
      Clock clock,
      long scopeStartMillis) {
    this.scopeKey = Objects.requireNonNull(scopeKey);
    this.origin = origin;
    this.clock = Objects.requireNonNull(clock);
    persistentState = Objects.requireNonNull(state);
    progress =
        Objects.requireNonNull(state)
            .scopes
            .computeIfAbsent(
                scopeKey,
                ignored -> {
                  var created = new Progress(origin, scopeStartMillis);
                  if (origin == Origin.SOLVER) {
                    created.work = new Work(0, 0, state.problemChangeScoreCalculations);
                  }
                  return created;
                });
    if (progress.origin != origin) {
      throw new IllegalArgumentException(
          "Resumable budget scope (%s) cannot change origin from %s to %s."
              .formatted(scopeKey, progress.origin, origin));
    }
    phaseStateKey = phaseScopeKey == null ? null : scopeKey + "/inherited-phase/" + phaseScopeKey;
    phaseProgress =
        phaseScopeKey == null
            ? progress
            : state.scopes.computeIfAbsent(
                phaseStateKey, ignored -> new Progress(Origin.PHASE, clock.millis()));
    expression =
        definition == null
            ? (snapshot, now) -> UNBOUNDED
            : compile(definition, ownerScope, "termination");
  }

  /**
   * Bind the actual inherited solver graph, including cancellation plumbing. The ordinary owner's
   * cumulative work is captured once per invocation through a deduplicated model-revision source.
   * Runtime descendant work must use separate source identities, without re-reporting this work.
   */
  public static <Solution_> ResumableSearchBudget<Solution_> forSolver(
      String scopeKey,
      SolverTermination<Solution_> definition,
      SolverScope<Solution_> ownerScope,
      PersistentState state,
      Clock clock,
      long scopeStartMillis) {
    return forSolver(scopeKey, scopeKey, definition, ownerScope, state, clock, scopeStartMillis);
  }

  /**
   * Solver work and time leaves retain solve-lifetime origins. Inherited phase-only step leaves use
   * the active phase key; distinct sequential phases get independent step allowances, while
   * re-entering the same phase after a model revision retains its previous consumption.
   */
  public static <Solution_> ResumableSearchBudget<Solution_> forSolver(
      String scopeKey,
      String phaseScopeKey,
      SolverTermination<Solution_> definition,
      SolverScope<Solution_> ownerScope,
      PersistentState state,
      Clock clock,
      long scopeStartMillis) {
    var budget =
        new ResumableSearchBudget<>(
            scopeKey,
            Objects.requireNonNull(phaseScopeKey),
            Origin.SOLVER,
            definition,
            ownerScope,
            state,
            clock,
            scopeStartMillis);
    budget.refreshOrdinaryWork(ownerScope);
    synchronized (budget.progress) {
      if (!budget.progress.archiveInitialized) {
        budget.progress.bestScore = ownerScope.getBestScore();
        budget.progress.archiveInitialized = true;
        var bestTime = ownerScope.getBestSolutionTimeMillis();
        if (bestTime != null) {
          budget.progress.lastImprovementMillis = Math.max(scopeStartMillis, bestTime);
        }
      } else if (budget.progress.bestScore == null) {
        budget.progress.bestScore = ownerScope.getBestScore();
      }
    }
    synchronized (budget.phaseProgress) {
      if (budget.phaseProgress.bestScore == null) {
        budget.phaseProgress.bestScore = ownerScope.getBestScore();
        budget.phaseProgress.archiveInitialized = true;
      }
    }
    return budget;
  }

  private static String ordinarySourceKey(SolverScope<?> ownerScope, long revision) {
    return "ordinary/" + ownerScope.getWorkerPath() + "/model-" + revision;
  }

  /**
   * Commit current raw ordinary-owner work through the same source used at solver-budget entry.
   * Coordinator scoring must use this method instead of an additional source for the same work.
   */
  public Work refreshOrdinaryWork(SolverScope<Solution_> ownerScope) {
    if (origin != Origin.SOLVER) {
      throw new IllegalStateException("Ordinary solver work requires a solver-origin budget.");
    }
    return recordSource(
        ordinarySourceKey(ownerScope, persistentState.modelRevision),
        0,
        ownerScope.getMoveEvaluationCount(),
        ownerScope.getScoreCalculationCount());
  }

  /**
   * Release a definitively closed nested episode scope after its final work reaches every ancestor.
   * Ancestor totals are independent and remain consumed. The runtime must never use this retired
   * instance again, or retire a solve-lifetime/root phase scope that must survive model revisions.
   */
  public void retireScope() {
    if (origin == Origin.SOLVER) {
      throw new IllegalStateException(
          "A solve-lifetime solver budget cannot be retired as an episode.");
    }
    persistentState.scopes.remove(scopeKey, progress);
    if (phaseStateKey != null) {
      persistentState.scopes.remove(phaseStateKey, phaseProgress);
    }
  }

  /** Add all work, including initialization, verification, failed probes and episode restarts. */
  public void recordDelta(long completedSteps, long moveEvaluations, long scoreCalculations) {
    recordDelta(new Work(completedSteps, moveEvaluations, scoreCalculations));
  }

  public void recordDelta(Work delta) {
    synchronized (progress) {
      synchronized (phaseProgress) {
        var total = progress.work.plus(Objects.requireNonNull(delta));
        if (phaseProgress != progress) {
          phaseProgress.work = phaseProgress.work.plus(delta);
        }
        progress.work = total;
      }
    }
  }

  /**
   * Record a cumulative snapshot exactly once. Source identities include the episode and model
   * revision whenever a source's counters can restart. Scope identities never include them.
   *
   * @return newly committed work, useful for propagation to an enclosing coordinator
   */
  public Work recordSource(
      String sourceKey, long completedSteps, long moveEvaluations, long scoreCalculations) {
    Objects.requireNonNull(sourceKey);
    var next = new Work(completedSteps, moveEvaluations, scoreCalculations);
    synchronized (progress) {
      synchronized (phaseProgress) {
        var previous = progress.sources.getOrDefault(sourceKey, Work.ZERO);
        var delta = next.minus(previous);
        var total = progress.work.plus(delta);
        if (phaseProgress != progress) {
          var phasePrevious = phaseProgress.sources.getOrDefault(sourceKey, Work.ZERO);
          var phaseTotal = phaseProgress.work.plus(next.minus(phasePrevious));
          phaseProgress.sources.put(sourceKey, next);
          phaseProgress.work = phaseTotal;
        }
        progress.sources.put(sourceKey, next);
        progress.work = total;
        return delta;
      }
    }
  }

  /**
   * Drop an episode's deduplication baseline after its final summary commits. Aggregate work stays
   * consumed. The coordinator must guarantee this retired source never publishes again; retaining
   * historical source identities here would otherwise grow memory with every episode restart.
   */
  public void retireSource(String sourceKey) {
    Objects.requireNonNull(sourceKey);
    synchronized (progress) {
      synchronized (phaseProgress) {
        progress.sources.remove(sourceKey);
        phaseProgress.sources.remove(sourceKey);
        progress.searchStepSources.remove(sourceKey);
        phaseProgress.searchStepSources.remove(sourceKey);
      }
    }
  }

  /**
   * A strict archive improvement resets idle progress at this summary's committed work position.
   * Equal scores, local improvements below the archive and repeated summaries do not reset it.
   * Partition scores must first be merged and scored in this budget's problem scope.
   */
  public boolean bestScoreImproved(InnerScore<?> score, long timestampMillis) {
    Objects.requireNonNull(score);
    synchronized (progress) {
      synchronized (phaseProgress) {
        if (phaseProgress != progress) {
          improve(phaseProgress, score, timestampMillis);
        }
        return improve(progress, score, timestampMillis);
      }
    }
  }

  private static boolean improve(Progress progress, InnerScore<?> score, long timestampMillis) {
    if (progress.bestScore != null && compare(score, progress.bestScore) <= 0) {
      return false;
    }
    progress.bestScore = score;
    progress.archiveInitialized = true;
    progress.lastImprovementStep = progress.work.completedSteps();
    progress.lastImprovementSearchStep = progress.searchSteps;
    progress.lastImprovementMillis = Math.max(progress.lastImprovementMillis, timestampMillis);
    return true;
  }

  /** Install a rescored model-revision baseline without resetting any work or idle budget. */
  public void rebaseArchive(InnerScore<?> score) {
    synchronized (progress) {
      synchronized (phaseProgress) {
        progress.bestScore = Objects.requireNonNull(score);
        progress.archiveInitialized = true;
        phaseProgress.bestScore = score;
        phaseProgress.archiveInitialized = true;
      }
    }
  }

  /**
   * Bind absolute best-score and feasibility predicates to the enclosing native problem archive.
   * This does not compare scores across problem scopes or change the member's idle history. A null
   * archive keeps these predicates false until a valid parent snapshot is available.
   */
  public void setPredicateArchive(@Nullable InnerScore<?> score) {
    synchronized (progress) {
      progress.predicateArchiveBound = true;
      progress.predicateArchive = score;
    }
  }

  /**
   * Use enclosing strict-improvement time for a legacy island's shared idle-time leaf. Member work
   * and unimproved-step counts retain their own progress; no termination tree is rewritten.
   */
  public void setUnimprovedTimeOrigin(long timestampMillis) {
    synchronized (progress) {
      progress.unimprovedTimeOrigin = Math.max(progress.startMillis, timestampMillis);
    }
  }

  /** Legacy idle conditions are unavailable outside search, including when their limit is zero. */
  public void setIdleApplicable(boolean applicable) {
    synchronized (progress) {
      progress.idleApplicable = applicable;
    }
  }

  /**
   * Bind unimproved-step progress to search steps only, excluding construction while preserving
   * those construction steps in ordinary completed-step limits. Use the same episode source key as
   * {@link #recordSource}; publish zero at initialization to establish the binding before search.
   */
  public long recordSearchStepSource(String sourceKey, long cumulativeSearchSteps) {
    Objects.requireNonNull(sourceKey);
    synchronized (progress) {
      synchronized (phaseProgress) {
        long delta = searchStepDelta(progress, sourceKey, cumulativeSearchSteps);
        long total = Math.addExact(progress.searchSteps, delta);
        if (phaseProgress != progress) {
          long phaseDelta = searchStepDelta(phaseProgress, sourceKey, cumulativeSearchSteps);
          long phaseTotal = Math.addExact(phaseProgress.searchSteps, phaseDelta);
          phaseProgress.searchStepSources.put(sourceKey, cumulativeSearchSteps);
          phaseProgress.searchSteps = phaseTotal;
          phaseProgress.searchStepsBound = true;
        }
        progress.searchStepSources.put(sourceKey, cumulativeSearchSteps);
        progress.searchSteps = total;
        progress.searchStepsBound = true;
        return delta;
      }
    }
  }

  private static long searchStepDelta(Progress progress, String sourceKey, long count) {
    long previous = progress.searchStepSources.getOrDefault(sourceKey, 0L);
    if (count < previous) {
      throw new IllegalArgumentException(
          "Search-step source counters must be nonnegative and cumulative.");
    }
    return count - previous;
  }

  public Work consumption() {
    synchronized (progress) {
      return progress.work;
    }
  }

  /**
   * Exact completed-step frontier with every non-step predicate frozen at this committed boundary.
   * A step leaf contributes max(0, limit-used); other leaves contribute zero if true, infinity if
   * false; OR takes the minimum and AND the maximum. Infinity never means an unlimited grant:
   * callers still apply the configured quantum.
   */
  public long remainingStepCount() {
    return expression.frontier(snapshot(), clock.millis());
  }

  public boolean isTerminated() {
    return remainingStepCount() == 0;
  }

  public Report report() {
    var snapshot = snapshot();
    long now = clock.millis();
    var limits =
        countNodes.stream()
            .map(
                node -> {
                  long consumed = node.consumed(snapshot, now);
                  return new Limit(
                      node.path,
                      node.phaseOnly ? Origin.PHASE : origin,
                      node.metric,
                      node.limit,
                      consumed,
                      Math.max(0, consumed - node.limit));
                })
            .toList();
    long remaining = expression.frontier(snapshot, now);
    return new Report(scopeKey, origin, snapshot.work, limits, remaining, remaining == 0);
  }

  private Snapshot snapshot() {
    synchronized (progress) {
      synchronized (phaseProgress) {
        return new Snapshot(
            progress.work,
            progress.startMillis,
            progress.unimprovedTimeOrigin == null
                ? progress.lastImprovementMillis
                : progress.unimprovedTimeOrigin,
            progress.unimprovedSteps(),
            progress.predicateArchiveBound ? progress.predicateArchive : progress.bestScore,
            phaseProgress.work,
            phaseProgress.unimprovedSteps(),
            progress.idleApplicable);
      }
    }
  }

  private Node compile(
      Termination<Solution_> definition, @Nullable SolverScope<Solution_> ownerScope, String path) {
    if (definition instanceof SolverBridgePhaseTermination<Solution_> bridge) {
      return compile(bridge.solverTermination, ownerScope, path + ".solver");
    }
    if (definition.getClass() == PhaseToSolverTerminationBridge.class) {
      return compile(
          ((PhaseToSolverTerminationBridge<Solution_>) definition).getSolverTermination(),
          ownerScope,
          path + ".solver");
    }
    if (definition instanceof AbstractCompositeTermination<Solution_> composite) {
      var children = new ArrayList<Node>(composite.terminationList.size());
      for (int i = 0; i < composite.terminationList.size(); i++) {
        children.add(compile(composite.terminationList.get(i), ownerScope, path + "[" + i + "]"));
      }
      return new CompositeNode(
          definition instanceof AndCompositeTermination, List.copyOf(children));
    }
    if (definition instanceof StepCountTermination<Solution_> steps) {
      return count(path, Metric.COMPLETED_STEPS, steps.getStepCountLimit());
    }
    if (definition instanceof UnimprovedStepCountTermination<Solution_> idleSteps) {
      return count(path, Metric.UNIMPROVED_STEPS, idleSteps.getUnimprovedStepCountLimit());
    }
    if (definition instanceof MoveCountTermination<Solution_> moves) {
      return count(path, Metric.MOVE_EVALUATIONS, moves.getMoveCountLimit());
    }
    if (definition instanceof ScoreCalculationCountTermination<Solution_> scores) {
      return count(path, Metric.SCORE_CALCULATIONS, scores.getScoreCalculationCountLimit());
    }
    if (definition instanceof TimeMillisSpentTermination<Solution_> time) {
      return count(path, Metric.ELAPSED_MILLIS, time.getTimeMillisSpentLimit());
    }
    if (definition instanceof UnimprovedTimeMillisSpentTermination<Solution_> idleTime) {
      return count(path, Metric.UNIMPROVED_MILLIS, idleTime.getUnimprovedTimeMillisSpentLimit());
    }
    if (definition instanceof BestScoreTermination<Solution_> best) {
      Score<?> target = best.getBestScoreLimit();
      return (snapshot, now) ->
          snapshot.bestScore != null
                  && snapshot.bestScore.isFullyAssigned()
                  && compareScores(snapshot.bestScore.raw(), target) >= 0
              ? 0
              : UNBOUNDED;
    }
    if (definition instanceof BestScoreFeasibleTermination<Solution_>) {
      return (snapshot, now) ->
          snapshot.bestScore != null
                  && snapshot.bestScore.isFullyAssigned()
                  && snapshot.bestScore.raw().isFeasible()
              ? 0
              : UNBOUNDED;
    }
    if (definition instanceof BasicPlumbingTermination<Solution_>
        || definition instanceof ChildThreadPlumbingTermination<Solution_>) {
      var scope = Objects.requireNonNull(ownerScope, "Solver plumbing requires its owning scope.");
      return (snapshot, now) -> definition.isSolverTerminated(scope) ? 0 : UNBOUNDED;
    }
    throw new IllegalArgumentException(
        "Unsupported resumable aggregate termination (%s) at (%s.%s). "
                .formatted(definition.getClass().getSimpleName(), scopeKey, path)
            + "Use supported work, time, archive-score or feasibility limits, or configure this "
            + "termination only on an independently managed local episode.");
  }

  private Node count(String path, Metric metric, long limit) {
    boolean phaseOnly =
        origin == Origin.SOLVER
            && (metric == Metric.COMPLETED_STEPS || metric == Metric.UNIMPROVED_STEPS);
    var node = new CountNode(path, metric, limit, phaseOnly);
    countNodes.add(node);
    return node;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareScores(Score<?> left, Score<?> right) {
    return ((Score) left).compareTo((Score) right);
  }

  public enum Origin {
    SOLVER,
    PHASE
  }

  public enum Metric {
    COMPLETED_STEPS,
    UNIMPROVED_STEPS,
    MOVE_EVALUATIONS,
    SCORE_CALCULATIONS,
    ELAPSED_MILLIS,
    UNIMPROVED_MILLIS;

    private long consumed(Snapshot snapshot, long now) {
      return switch (this) {
        case COMPLETED_STEPS -> snapshot.work.completedSteps;
        case UNIMPROVED_STEPS -> snapshot.unimprovedSteps;
        case MOVE_EVALUATIONS -> snapshot.work.moveEvaluations;
        case SCORE_CALCULATIONS -> snapshot.work.scoreCalculations;
        case ELAPSED_MILLIS -> Math.max(0, now - snapshot.startMillis);
        case UNIMPROVED_MILLIS -> Math.max(0, now - snapshot.lastImprovementMillis);
      };
    }
  }

  /** Complete cumulative work in this scope, including every descendant and retired episode. */
  public record Work(long completedSteps, long moveEvaluations, long scoreCalculations) {
    public static final Work ZERO = new Work(0, 0, 0);

    public Work {
      if (completedSteps < 0 || moveEvaluations < 0 || scoreCalculations < 0) {
        throw new IllegalArgumentException(
            "Resumable work counters must be nonnegative and cumulative; use a new source key "
                + "after an episode or model restart.");
      }
    }

    public Work plus(Work other) {
      return new Work(
          Math.addExact(completedSteps, other.completedSteps),
          Math.addExact(moveEvaluations, other.moveEvaluations),
          Math.addExact(scoreCalculations, other.scoreCalculations));
    }

    private Work minus(Work other) {
      return new Work(
          completedSteps - other.completedSteps,
          moveEvaluations - other.moveEvaluations,
          scoreCalculations - other.scoreCalculations);
    }
  }

  public record Limit(
      String path, Origin origin, Metric metric, long limit, long consumed, long overshoot) {}

  public record Report(
      String scopeKey,
      Origin origin,
      Work consumed,
      List<Limit> limits,
      long remainingStepCount,
      boolean terminated) {}

  /** Store once per top-level solve; share with children and retain across problem-change loops. */
  public static final class PersistentState {
    private final Map<String, Progress> scopes = new ConcurrentHashMap<>();
    private volatile long modelRevision;
    private volatile long problemChangeScoreCalculations;

    /**
     * Preserve ordinary work before replacing its director or resetting counters for a model
     * revision, including when no resumable phase has run yet. Completed-step allowances remain
     * phase-local and are not copied from the ordinary phase.
     */
    public synchronized void captureOrdinaryWork(SolverScope<?> owner) {
      var progress =
          scopes.computeIfAbsent(
              "solver",
              ignored -> {
                long startMillis =
                    Objects.requireNonNull(
                        owner.getStartingSystemTimeMillis(), "The solver must have started.");
                var created = new Progress(Origin.SOLVER, startMillis);
                created.work = new Work(0, 0, problemChangeScoreCalculations);
                return created;
              });
      if (progress.origin != Origin.SOLVER) {
        throw new IllegalStateException("The solver budget scope must have solver origin.");
      }
      var sourceKey = ordinarySourceKey(owner, modelRevision);
      var next = new Work(0, owner.getMoveEvaluationCount(), owner.getScoreCalculationCount());
      synchronized (progress) {
        var previous = progress.sources.getOrDefault(sourceKey, Work.ZERO);
        var total = progress.work.plus(next.minus(previous));
        progress.sources.put(sourceKey, next);
        progress.work = total;
        var bestScore = owner.getBestScore();
        if (bestScore != null) {
          var bestTime = owner.getBestSolutionTimeMillis();
          improve(progress, bestScore, bestTime == null ? progress.startMillis : bestTime);
        }
      }
    }

    /**
     * Solve-wide logical scoring, including ordinary work since the last coordinator commit. This
     * view is for the root solver predicate only; local phases and episodes keep raw counters.
     */
    public long solverScoreCalculationCount(SolverScope<?> owner) {
      var progress = scopes.get("solver");
      long current = owner.getScoreCalculationCount();
      if (progress == null) {
        return Math.addExact(current, problemChangeScoreCalculations);
      }
      synchronized (progress) {
        var previous =
            progress.sources.getOrDefault(ordinarySourceKey(owner, modelRevision), Work.ZERO);
        long delta = current - previous.scoreCalculations;
        requireMonotonicOrdinaryCounter(delta);
        return Math.addExact(progress.work.scoreCalculations, delta);
      }
    }

    /** Root solver move view; descendant moves have already entered committed aggregate work. */
    public long solverMoveEvaluationCount(SolverScope<?> owner) {
      var progress = scopes.get("solver");
      long current = owner.getMoveEvaluationCount();
      if (progress == null) {
        return current;
      }
      synchronized (progress) {
        var previous =
            progress.sources.getOrDefault(ordinarySourceKey(owner, modelRevision), Work.ZERO);
        long delta = current - previous.moveEvaluations;
        requireMonotonicOrdinaryCounter(delta);
        return Math.addExact(progress.work.moveEvaluations, delta);
      }
    }

    private static void requireMonotonicOrdinaryCounter(long delta) {
      if (delta < 0) {
        throw new IllegalStateException(
            "Ordinary solver counters reset without advancing the resumable model revision.");
      }
    }

    /**
     * Credit preparation and rescoring performed between model revisions before ordinary solver
     * counters reset. Call once with old-model workers quiescent; this work belongs to the solver,
     * and must not consume any phase's local allowance.
     */
    public synchronized void recordProblemChangeScoreCalculations(long count) {
      var delta = new Work(0, 0, count);
      long next = Math.addExact(problemChangeScoreCalculations, count);
      for (var progress : scopes.values()) {
        if (progress.origin == Origin.SOLVER) {
          synchronized (progress) {
            progress.work = progress.work.plus(delta);
          }
        }
      }
      problemChangeScoreCalculations = next;
    }

    /** Called with all old-model workers quiescent, before any new-model archive is installed. */
    public synchronized void modelChanged(long revision) {
      if (revision < modelRevision) {
        throw new IllegalArgumentException("Resumable model revisions must not decrease.");
      }
      if (revision == modelRevision) {
        return;
      }
      modelRevision = revision;
      for (var progress : scopes.values()) {
        synchronized (progress) {
          progress.bestScore = null;
          progress.predicateArchive = null;
          progress.sources.clear();
          progress.searchStepSources.clear();
        }
      }
    }

    public long modelRevision() {
      return modelRevision;
    }
  }

  private static final class Progress {
    private final Origin origin;
    private final long startMillis;
    private final Map<String, Work> sources = new HashMap<>();
    private final Map<String, Long> searchStepSources = new HashMap<>();
    private Work work = Work.ZERO;
    private long lastImprovementMillis;
    private long lastImprovementStep;
    private @Nullable InnerScore<?> bestScore;
    private boolean archiveInitialized;
    private @Nullable InnerScore<?> predicateArchive;
    private boolean predicateArchiveBound;
    private @Nullable Long unimprovedTimeOrigin;
    private boolean idleApplicable = true;
    private boolean searchStepsBound;
    private long searchSteps;
    private long lastImprovementSearchStep;

    private Progress(Origin origin, long startMillis) {
      this.origin = origin;
      this.startMillis = startMillis;
      lastImprovementMillis = startMillis;
    }

    private long unimprovedSteps() {
      return searchStepsBound
          ? searchSteps - lastImprovementSearchStep
          : work.completedSteps - lastImprovementStep;
    }
  }

  private record Snapshot(
      Work work,
      long startMillis,
      long lastImprovementMillis,
      long unimprovedSteps,
      @Nullable InnerScore<?> bestScore,
      Work phaseWork,
      long phaseUnimprovedSteps,
      boolean idleApplicable) {}

  @FunctionalInterface
  private interface Node {
    long frontier(Snapshot snapshot, long now);
  }

  private record CountNode(String path, Metric metric, long limit, boolean phaseOnly)
      implements Node {
    private long consumed(Snapshot snapshot, long now) {
      if (phaseOnly) {
        return metric == Metric.COMPLETED_STEPS
            ? snapshot.phaseWork.completedSteps
            : snapshot.phaseUnimprovedSteps;
      }
      return metric.consumed(snapshot, now);
    }

    @Override
    public long frontier(Snapshot snapshot, long now) {
      if (!snapshot.idleApplicable
          && (metric == Metric.UNIMPROVED_STEPS || metric == Metric.UNIMPROVED_MILLIS)) {
        return UNBOUNDED;
      }
      long remaining = Math.max(0, limit - consumed(snapshot, now));
      return metric == Metric.COMPLETED_STEPS || metric == Metric.UNIMPROVED_STEPS
          ? remaining
          : remaining == 0 ? 0 : UNBOUNDED;
    }
  }

  private record CompositeNode(boolean and, List<Node> children) implements Node {
    @Override
    public long frontier(Snapshot snapshot, long now) {
      if (children.isEmpty()) {
        return UNBOUNDED;
      }
      long frontier = and ? 0 : UNBOUNDED;
      for (var child : children) {
        long next = child.frontier(snapshot, now);
        frontier = and ? Math.max(frontier, next) : Math.min(frontier, next);
      }
      return frontier;
    }
  }
}
