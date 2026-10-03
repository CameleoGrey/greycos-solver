package greycos.solver.core.impl.solver.termination;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Queue;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.ScoreArithmetic;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.util.Pair;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<Solution_>
    extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final long unimprovedTimeMillisSpentLimit;
  private final Score<?> unimprovedScoreDifferenceThreshold;
  private final Clock clock;

  private final Queue<Pair<Long, InnerScore<?>>> solverBestScoreHistory = new ArrayDeque<>();
  private final Queue<Pair<Long, InnerScore<?>>> phaseBestScoreHistory = new ArrayDeque<>();
  // safeTimeMillis is until when we're safe from termination.
  private long solverSafeTimeMillis = -1L;
  private long phaseSafeTimeMillis = -1L;
  private boolean solverCounterStarted = false;
  private boolean phaseCounterStarted = false;
  private boolean currentPhaseSendsBestSolutionEvents = false;

  long getUnimprovedTimeMillisSpentLimit() {
    return unimprovedTimeMillisSpentLimit;
  }

  Score<?> getUnimprovedScoreDifferenceThreshold() {
    return unimprovedScoreDifferenceThreshold;
  }

  public UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination(
      long unimprovedTimeMillisSpentLimit, Score<?> unimprovedScoreDifferenceThreshold) {
    this(unimprovedTimeMillisSpentLimit, unimprovedScoreDifferenceThreshold, Clock.systemUTC());
  }

  UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination(
      long unimprovedTimeMillisSpentLimit,
      Score<?> unimprovedScoreDifferenceThreshold,
      Clock clock) {
    this.unimprovedTimeMillisSpentLimit = unimprovedTimeMillisSpentLimit;
    if (unimprovedTimeMillisSpentLimit < 0L) {
      throw new IllegalArgumentException(
          "The unimprovedTimeMillisSpentLimit (%d) cannot be negative."
              .formatted(unimprovedTimeMillisSpentLimit));
    }
    this.unimprovedScoreDifferenceThreshold = unimprovedScoreDifferenceThreshold;
    this.clock = clock;
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    resetState();
  }

  void resetState() {
    solverBestScoreHistory.clear();
    phaseBestScoreHistory.clear();
    solverSafeTimeMillis = -1L;
    phaseSafeTimeMillis = -1L;
    solverCounterStarted = false;
    phaseCounterStarted = false;
    currentPhaseSendsBestSolutionEvents = false;
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    resetState();
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    phaseBestScoreHistory.clear();
    phaseCounterStarted = false;
    phaseSafeTimeMillis = -1L;
    // Construction and similar phases only publish useful best solutions at their end.
    currentPhaseSendsBestSolutionEvents = phaseScope.isPhaseSendingBestSolutionEvents();
    if (!currentPhaseSendsBestSolutionEvents) {
      solverBestScoreHistory.clear();
      solverCounterStarted = false;
      solverSafeTimeMillis = -1L;
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (!currentPhaseSendsBestSolutionEvents || (solverCounterStarted && phaseCounterStarted)) {
      return;
    }
    // Setup does not consume a fresh idle budget. Keep global history across consecutive
    // improvement phases, while every phase gets its own initial best and deadline.
    var now = clock.millis();
    var bestScore = stepScope.getPhaseScope().getBestScore();
    if (!solverCounterStarted) {
      solverSafeTimeMillis = now + unimprovedTimeMillisSpentLimit;
      solverCounterStarted = true;
      seedHistory(solverBestScoreHistory, now, bestScore);
    }
    if (!phaseCounterStarted) {
      phaseSafeTimeMillis = now + unimprovedTimeMillisSpentLimit;
      phaseCounterStarted = true;
      seedHistory(phaseBestScoreHistory, now, bestScore);
    }
  }

  private void seedHistory(
      Queue<Pair<Long, InnerScore<?>>> history, long now, InnerScore<?> bestScore) {
    if (bestScore != null && bestScore.isFullyAssigned()) {
      history.add(new Pair<>(now, bestScore));
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    phaseBestScoreHistory.clear();
    phaseCounterStarted = false;
    phaseSafeTimeMillis = -1L;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (stepScope.getBestScoreImproved()) {
      bestScoreImproved(stepScope);
    }
  }

  @Override
  public void bestScoreImproved(AbstractStepScope<Solution_> stepScope) {
    if (!currentPhaseSendsBestSolutionEvents) {
      return;
    }
    var solverScope = stepScope.getPhaseScope().getSolverScope();
    var bestScore = solverScope.getBestScore();
    if (bestScore == null || !bestScore.isFullyAssigned()) {
      return;
    }
    var bestSolutionTimeMillis = solverScope.getBestSolutionTimeMillis();
    if (solverCounterStarted) {
      solverSafeTimeMillis =
          recordImprovement(
              solverBestScoreHistory, solverSafeTimeMillis, bestSolutionTimeMillis, bestScore);
    }
    if (phaseCounterStarted) {
      phaseSafeTimeMillis =
          recordImprovement(
              phaseBestScoreHistory, phaseSafeTimeMillis, bestSolutionTimeMillis, bestScore);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private long recordImprovement(
      Queue<Pair<Long, InnerScore<?>>> history,
      long safeTimeMillis,
      long bestSolutionTimeMillis,
      InnerScore<?> bestScore) {
    var bestScoreValue = (Score) bestScore.raw();
    while (!history.isEmpty()) {
      var previous = history.element();
      var timeLimitNotYetReached =
          previous.key() + unimprovedTimeMillisSpentLimit >= bestSolutionTimeMillis;
      var scoreImprovedOverThreshold =
          ScoreArithmetic.differenceAtLeast(
              bestScoreValue, previous.value().raw(), unimprovedScoreDifferenceThreshold);
      if (!scoreImprovedOverThreshold || !timeLimitNotYetReached) {
        break;
      }
      history.remove();
      safeTimeMillis = bestSolutionTimeMillis + unimprovedTimeMillisSpentLimit;
    }
    history.add(new Pair<>(bestSolutionTimeMillis, bestScore));
    return safeTimeMillis;
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return isTerminated(solverCounterStarted, solverSafeTimeMillis);
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return isTerminated(phaseCounterStarted, phaseSafeTimeMillis);
  }

  private boolean isTerminated(boolean counterStarted, long safeTimeMillis) {
    if (!canEvaluate(counterStarted)) {
      return false;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return true;
    }
    // It's possible that there is already an improving move in the forager
    // that will end up pushing the safeTimeMillis further
    // but that doesn't change the fact that the best score didn't improve enough in the specified
    // time interval.
    // It just looks weird because it terminates even though the final step is a high enough score
    // improvement.
    var now = clock.millis();
    return now > safeTimeMillis;
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return calculateTimeGradient(solverCounterStarted, solverSafeTimeMillis);
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return calculateTimeGradient(phaseCounterStarted, phaseSafeTimeMillis);
  }

  private double calculateTimeGradient(boolean counterStarted, long safeTimeMillis) {
    if (!canEvaluate(counterStarted)) {
      return 0.0;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return 1.0;
    }
    var now = clock.millis();
    var unimprovedTimeMillisSpent = now - (safeTimeMillis - unimprovedTimeMillisSpentLimit);
    return TerminationGradient.ratio(unimprovedTimeMillisSpent, unimprovedTimeMillisSpentLimit);
  }

  private boolean canEvaluate(boolean counterStarted) {
    return currentPhaseSendsBestSolutionEvents
        && (counterStarted || unimprovedTimeMillisSpentLimit == 0L);
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return new UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination<>(
        unimprovedTimeMillisSpentLimit, unimprovedScoreDifferenceThreshold);
  }

  @Override
  public boolean isApplicableTo(Class<? extends AbstractPhaseScope> phaseScopeClass) {
    return !(phaseScopeClass == ConstructionHeuristicPhaseScope.class
        || phaseScopeClass == CustomPhaseScope.class);
  }

  @Override
  public String toString() {
    return "UnimprovedTimeMillisSpent(" + unimprovedTimeMillisSpentLimit + ")";
  }
}
