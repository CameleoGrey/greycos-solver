package greycos.solver.core.impl.solver.termination;

import java.util.IdentityHashMap;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.Nullable;

/**
 * One island's cumulative work and search history, including its descendant solver scopes. Members
 * publish their own counters; the ledger never reads another thread's score director.
 */
final class IslandWorkQuota<Solution_> {

  private final Map<SolverScope<Solution_>, Member> members = new IdentityHashMap<>();
  private final Map<PhaseTermination<Solution_>, PhaseTermination<Solution_>> searchTerminations =
      new IdentityHashMap<>();
  private final SearchScope searchScope;
  private Snapshot snapshot = new Snapshot(0, 0, 0);
  private @Nullable InnerScore<?> bestScore;
  private boolean started;

  IslandWorkQuota(SolverScope<Solution_> ownerScope, long phaseStartMillis) {
    searchScope = new SearchScope(ownerScope, phaseStartMillis);
  }

  synchronized Member register(SolverScope<Solution_> scope) {
    return members.computeIfAbsent(scope, ignored -> new Member());
  }

  synchronized Snapshot snapshot() {
    return snapshot;
  }

  synchronized void start(@Nullable InnerScore<?> score) {
    recordBest(score);
    if (started) {
      return;
    }
    started = true;
    for (var termination : searchTerminations.values()) {
      termination.phaseStarted(searchScope);
    }
  }

  synchronized void recordBest(@Nullable InnerScore<?> score) {
    if (score != null
        && (bestScore == null || IslandTerminationBudget.compare(score, bestScore) > 0)) {
      bestScore = score;
      searchScope.setBestSolutionStepIndex(searchScope.lastStep.getStepIndex());
    }
  }

  synchronized void stepStarted(Member member, AbstractStepScope<Solution_> step, boolean search) {
    if (member.lastStartedStep == step) {
      return;
    }
    member.lastStartedStep = step;
    if (search) {
      var searchStep = searchScope.nextStep();
      for (var termination : searchTerminations.values()) {
        termination.stepStarted(searchStep);
      }
    }
  }

  synchronized void stepEnded(
      Member member,
      AbstractStepScope<Solution_> step,
      boolean search,
      @Nullable InnerScore<?> score) {
    if (member.lastEndedStep == step) {
      return;
    }
    member.lastEndedStep = step;
    snapshot =
        new Snapshot(
            add(snapshot.completedSteps(), 1),
            snapshot.moveEvaluationCount(),
            snapshot.scoreCalculationCount());
    if (search) {
      searchScope.completeStep();
    }
    recordBest(score);
    if (search) {
      for (var termination : searchTerminations.values()) {
        termination.stepEnded(searchScope.lastStep);
      }
    }
  }

  synchronized void bestScoreImproved(@Nullable InnerScore<?> score, boolean search) {
    recordBest(score);
    if (search) {
      for (var termination : searchTerminations.values()) {
        termination.bestScoreImproved(searchScope.lastStep);
      }
    }
  }

  synchronized Snapshot publish(Member member, long moves, long calculations) {
    // Republishing a cumulative member value, including at completion, contributes no work twice.
    long nextMoves = Math.max(member.moves, moves);
    long nextCalculations = Math.max(member.calculations, calculations);
    if (nextMoves == member.moves && nextCalculations == member.calculations) {
      return snapshot;
    }
    snapshot =
        new Snapshot(
            snapshot.completedSteps(),
            add(snapshot.moveEvaluationCount(), nextMoves - member.moves),
            add(snapshot.scoreCalculationCount(), nextCalculations - member.calculations));
    member.moves = nextMoves;
    member.calculations = nextCalculations;
    return snapshot;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  synchronized IslandTerminationBudget.Node<Solution_> bindSearchTermination(
      PhaseTermination<Solution_> definition) {
    var termination =
        searchTerminations.computeIfAbsent(
            definition,
            ignored -> {
              PhaseTermination<Solution_> copy = definition;
              if (definition instanceof DiminishedReturnsTermination diminished) {
                copy =
                    new DiminishedReturnsTermination<>(
                        diminished.getSlidingWindowNanos()
                            / DiminishedReturnsTermination.NANOS_PER_MILLISECOND,
                        diminished.getMinimumImprovementRatio());
              }
              if (started) {
                copy.phaseStarted(searchScope);
              }
              return copy;
            });
    return new IslandTerminationBudget.Node<>() {
      @Override
      public boolean applicable(boolean search) {
        return search;
      }

      @Override
      public boolean hasApplicableLimit(boolean search) {
        return search;
      }

      @Override
      public boolean isTerminated() {
        synchronized (IslandWorkQuota.this) {
          return termination.isPhaseTerminated(searchScope);
        }
      }

      @Override
      public double gradient() {
        synchronized (IslandWorkQuota.this) {
          return termination.calculatePhaseTimeGradient(searchScope);
        }
      }

      @Override
      public boolean supportsRepairAttempts() {
        return !(termination instanceof DiminishedReturnsTermination);
      }
    };
  }

  private static long add(long current, long addition) {
    return addition > Long.MAX_VALUE - current ? Long.MAX_VALUE : current + addition;
  }

  record Snapshot(long completedSteps, long moveEvaluationCount, long scoreCalculationCount) {}

  final class Member {
    private long moves;
    private long calculations;
    private @Nullable AbstractStepScope<Solution_> lastStartedStep;
    private @Nullable AbstractStepScope<Solution_> lastEndedStep;
  }

  /** Synthetic history only; it does not publish public phase or step events. */
  private final class SearchScope extends AbstractPhaseScope<Solution_> {
    private int completedSteps;
    private SearchStep lastStep = new SearchStep(this, -1);

    private SearchScope(SolverScope<Solution_> ownerScope, long phaseStartMillis) {
      super(ownerScope, 0);
      startingSystemTimeMillis = phaseStartMillis;
    }

    private SearchStep nextStep() {
      var step = new SearchStep(this, completedSteps);
      step.setScore(bestScore);
      return step;
    }

    private void completeStep() {
      lastStep = nextStep();
      if (completedSteps != Integer.MAX_VALUE) {
        completedSteps++;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Score_ extends Score<Score_>> InnerScore<Score_> getBestScore() {
      return (InnerScore<Score_>) bestScore;
    }

    @Override
    public AbstractStepScope<Solution_> getLastCompletedStepScope() {
      return lastStep;
    }

    @Override
    public int getNextStepIndex() {
      return completedSteps;
    }
  }

  private final class SearchStep extends AbstractStepScope<Solution_> {
    private final SearchScope scope;

    private SearchStep(SearchScope scope, int index) {
      super(index);
      this.scope = scope;
    }

    @Override
    public AbstractPhaseScope<Solution_> getPhaseScope() {
      return scope;
    }
  }
}
