package greycos.solver.core.impl.solver.termination;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.Nullable;

/**
 * One enclosing island phase's termination definition and shared progress. Each island binds the
 * same expression to its own work counters; elapsed time and strict global improvements are shared.
 */
public final class IslandTerminationBudget<Solution_> {

  private final Clock clock;
  private final long phaseStartMillis;
  private final Function<IslandSequenceTermination<Solution_>, Node<Solution_>> nodeFactory;
  private final List<ThresholdMonitor> thresholdMonitors = new ArrayList<>();
  private volatile Progress progress;
  private @Nullable InnerScore<?> startingScore;

  public IslandTerminationBudget(
      TerminationConfig config,
      HeuristicConfigPolicy<Solution_> configPolicy,
      Clock clock,
      long phaseStartMillis) {
    this.clock = Objects.requireNonNull(clock);
    this.phaseStartMillis = phaseStartMillis;
    progress = new Progress(null, phaseStartMillis, -1, null);
    var definition =
        TerminationFactory.<Solution_>create(Objects.requireNonNull(config))
            .buildTermination(Objects.requireNonNull(configPolicy));
    nodeFactory = definition == null ? ignored -> new NeverNode<>() : compile(definition);
  }

  public IslandSequenceTermination<Solution_> createIslandTermination(
      SolverScope<Solution_> scope) {
    return new IslandSequenceTermination<>(this, Objects.requireNonNull(scope));
  }

  /**
   * Called in accepted global-publication order. External observers and local migrant adoption must
   * not call this method: only a strict improvement of the shared best extends global idle time.
   */
  public synchronized void bestScoreImproved(
      InnerScore<?> score, long timestampMillis, long version) {
    Objects.requireNonNull(score);
    var previous = progress;
    if (version <= previous.version()
        || previous.score() != null && compare(score, previous.score()) <= 0) {
      return;
    }
    if (startingScore == null) {
      startingScore = score;
    }
    var next = new Progress(score, timestampMillis, version, previous.searchStartMillis());
    for (var monitor : thresholdMonitors) {
      monitor.improved(next);
    }
    progress = next;
  }

  synchronized void searchStarted() {
    if (progress.searchStartMillis() != null) {
      return;
    }
    var previous = progress;
    var next =
        new Progress(
            previous.score(), previous.bestTimeMillis(), previous.version(), clock.millis());
    for (var monitor : thresholdMonitors) {
      monitor.start(next);
    }
    progress = next;
  }

  Clock clock() {
    return clock;
  }

  long phaseStartMillis() {
    return phaseStartMillis;
  }

  Progress progress() {
    return progress;
  }

  boolean hasSharedHistory() {
    return !thresholdMonitors.isEmpty();
  }

  Node<Solution_> bind(IslandSequenceTermination<Solution_> island) {
    return nodeFactory.apply(island);
  }

  ProgressScope newProgressScope() {
    return new ProgressScope();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Function<IslandSequenceTermination<Solution_>, Node<Solution_>> compile(
      Termination<Solution_> definition) {
    if (definition instanceof AbstractCompositeTermination<Solution_> composite) {
      var factories = composite.terminationList.stream().map(this::compile).toList();
      return island ->
          new CompositeNode<>(
              definition instanceof OrCompositeTermination,
              factories.stream().map(factory -> factory.apply(island)).toList());
    }
    if (definition instanceof TimeMillisSpentTermination<Solution_> spent) {
      return ignored ->
          new Node<>() {
            @Override
            public boolean isTerminated() {
              return clock.millis() - phaseStartMillis >= spent.getTimeMillisSpentLimit();
            }

            @Override
            public double gradient() {
              return ratio(clock.millis() - phaseStartMillis, spent.getTimeMillisSpentLimit());
            }
          };
    }
    if (definition instanceof UnimprovedTimeMillisSpentTermination<Solution_> idle) {
      return island ->
          new Node<>() {
            @Override
            public boolean isTerminated() {
              var snapshot = island.globalProgress();
              return snapshot.searchStartMillis() != null
                  && idleMillis(snapshot) >= idle.getUnimprovedTimeMillisSpentLimit();
            }

            @Override
            public double gradient() {
              var snapshot = island.globalProgress();
              return snapshot.searchStartMillis() == null
                  ? 0.0
                  : ratio(idleMillis(snapshot), idle.getUnimprovedTimeMillisSpentLimit());
            }
          };
    }
    if (definition
        instanceof
        UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<Solution_> threshold) {
      var monitor = new ThresholdMonitor(threshold);
      thresholdMonitors.add(monitor);
      return ignored -> monitor;
    }
    if (definition instanceof BestScoreTermination
        || definition instanceof BestScoreFeasibleTermination) {
      return island ->
          new LeafNode<>((PhaseTermination<Solution_>) definition, island.globalScope(), false);
    }
    if (definition instanceof StepCountTermination
        || definition instanceof MoveCountTermination
        || definition instanceof ScoreCalculationCountTermination) {
      return island ->
          new LeafNode<>((PhaseTermination<Solution_>) definition, island.workScope(), false);
    }
    if (definition instanceof UnimprovedStepCountTermination) {
      return island ->
          new LeafNode<>((PhaseTermination<Solution_>) definition, island.searchScope(), true);
    }
    if (definition instanceof DiminishedReturnsTermination diminished) {
      return island -> {
        DiminishedReturnsTermination<Solution_, ?> copy =
            new DiminishedReturnsTermination<>(
                diminished.getSlidingWindowNanos()
                    / DiminishedReturnsTermination.NANOS_PER_MILLISECOND,
                diminished.getMinimumImprovementRatio());
        var node = new LeafNode<Solution_>(copy, island.searchScope(), true);
        island.addStatefulTermination(copy);
        return node;
      };
    }
    throw new IllegalArgumentException(
        "Unsupported outer island termination: " + definition.getClass().getSimpleName());
  }

  private long idleMillis(Progress snapshot) {
    return clock.millis() - Math.max(snapshot.bestTimeMillis(), snapshot.searchStartMillis());
  }

  static double ratio(long amount, long limit) {
    if (limit == 0) {
      return amount >= 0 ? 1.0 : 0.0;
    }
    return Math.max(0.0, Math.min(1.0, amount / (double) limit));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static int compare(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }

  record Progress(
      @Nullable InnerScore<?> score,
      long bestTimeMillis,
      long version,
      @Nullable Long searchStartMillis) {}

  interface Node<Solution_> {
    boolean isTerminated();

    double gradient();

    default boolean applicable(boolean search) {
      return true;
    }

    default boolean supportsRepairAttempts() {
      return true;
    }
  }

  private static final class NeverNode<Solution_> implements Node<Solution_> {
    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public double gradient() {
      return 0.0;
    }
  }

  private static final class CompositeNode<Solution_> implements Node<Solution_> {
    private final boolean or;
    private final List<Node<Solution_>> children;
    private boolean search;

    private CompositeNode(boolean or, List<Node<Solution_>> children) {
      this.or = or;
      this.children = children;
    }

    @Override
    public boolean applicable(boolean search) {
      this.search = search;
      boolean applicable = false;
      for (var child : children) {
        applicable |= child.applicable(search);
      }
      return applicable;
    }

    @Override
    public boolean isTerminated() {
      for (var child : children) {
        if (child.applicable(search) && child.isTerminated() == or) {
          return or;
        }
      }
      return !or;
    }

    @Override
    public double gradient() {
      double result = or ? 0.0 : 1.0;
      for (var child : children) {
        if (child.applicable(search)) {
          var next = child.gradient();
          if (next >= 0.0) {
            result = or ? Math.max(result, next) : Math.min(result, next);
          }
        }
      }
      return result;
    }

    @Override
    public boolean supportsRepairAttempts() {
      return children.stream().allMatch(Node::supportsRepairAttempts);
    }
  }

  private static final class LeafNode<Solution_> implements Node<Solution_> {
    private final PhaseTermination<Solution_> termination;
    private final AbstractPhaseScope<Solution_> scope;
    private final boolean searchOnly;

    private LeafNode(
        PhaseTermination<Solution_> termination,
        AbstractPhaseScope<Solution_> scope,
        boolean searchOnly) {
      this.termination = termination;
      this.scope = scope;
      this.searchOnly = searchOnly;
    }

    @Override
    public boolean applicable(boolean search) {
      return !searchOnly || search;
    }

    @Override
    public boolean isTerminated() {
      return termination.isPhaseTerminated(scope);
    }

    @Override
    public double gradient() {
      var result = termination.calculatePhaseTimeGradient(scope);
      return Double.isNaN(result) ? 1.0 : result;
    }

    @Override
    public boolean supportsRepairAttempts() {
      return !(termination instanceof DiminishedReturnsTermination);
    }
  }

  /** The delegate and its history are accessed only under the enclosing budget's monitor. */
  private final class ThresholdMonitor implements Node<Solution_> {
    private final UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<Solution_>
        termination;
    private final ProgressScope scope = newProgressScope();
    private boolean started;

    private ThresholdMonitor(
        UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<Solution_> definition) {
      termination =
          new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(
              definition.getUnimprovedTimeMillisSpentLimit(),
              definition.getUnimprovedScoreDifferenceThreshold(),
              clock);
    }

    private void start(Progress snapshot) {
      scope.update(snapshot);
      scope.startAt(snapshot.searchStartMillis());
      termination.solvingStarted(scope.getSolverScope());
      termination.phaseStarted(scope);
      started = true;
    }

    private void improved(Progress snapshot) {
      if (started) {
        scope.update(snapshot);
        termination.bestScoreImproved(scope.getLastCompletedStepScope());
      }
    }

    @Override
    public boolean isTerminated() {
      synchronized (IslandTerminationBudget.this) {
        return started && termination.isPhaseTerminated(scope);
      }
    }

    @Override
    public double gradient() {
      synchronized (IslandTerminationBudget.this) {
        return started ? termination.calculatePhaseTimeGradient(scope) : 0.0;
      }
    }
  }

  /** A private progress view, never an agent's mutable solver scope. */
  final class ProgressScope extends AbstractPhaseScope<Solution_> {
    private final ProgressSolverScope progressSolverScope;
    private final ProgressStep step = new ProgressStep(this);

    private ProgressScope() {
      this(new ProgressSolverScope());
    }

    private ProgressScope(ProgressSolverScope solverScope) {
      super(solverScope, 0);
      progressSolverScope = solverScope;
      startingSystemTimeMillis = phaseStartMillis;
    }

    void update(Progress snapshot) {
      progressSolverScope.snapshot = snapshot;
    }

    void startAt(long timestampMillis) {
      startingSystemTimeMillis = timestampMillis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Score_ extends Score<Score_>> InnerScore<Score_> getStartingScore() {
      return (InnerScore<Score_>) IslandTerminationBudget.this.startingScore;
    }

    @Override
    public AbstractStepScope<Solution_> getLastCompletedStepScope() {
      return step;
    }
  }

  private final class ProgressSolverScope extends SolverScope<Solution_> {
    private Progress snapshot = progress;

    private ProgressSolverScope() {
      super(clock);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Score_ extends Score<Score_>> InnerScore<Score_> getBestScore() {
      return (InnerScore<Score_>) snapshot.score();
    }

    @Override
    public boolean isBestSolutionInitialized() {
      return snapshot.score() != null && snapshot.score().isFullyAssigned();
    }

    @Override
    public Long getBestSolutionTimeMillis() {
      return snapshot.bestTimeMillis();
    }
  }

  private final class ProgressStep extends AbstractStepScope<Solution_> {
    private final AbstractPhaseScope<Solution_> scope;

    private ProgressStep(AbstractPhaseScope<Solution_> scope) {
      super(-1);
      this.scope = scope;
    }

    @Override
    public AbstractPhaseScope<Solution_> getPhaseScope() {
      return scope;
    }
  }
}
