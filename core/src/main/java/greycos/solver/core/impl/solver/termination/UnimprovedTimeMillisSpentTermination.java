package greycos.solver.core.impl.solver.termination;

import java.time.Clock;

import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class UnimprovedTimeMillisSpentTermination<Solution_>
    extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final long unimprovedTimeMillisSpentLimit;
  private final Clock clock;

  private boolean solverCounterStarted = false;
  private boolean phaseCounterStarted = false;
  private boolean currentPhaseSendsBestSolutionEvents = false;
  private long solverStartedTimeMillis = -1L;
  private long phaseStartedTimeMillis = -1L;

  public UnimprovedTimeMillisSpentTermination(long unimprovedTimeMillisSpentLimit) {
    this(unimprovedTimeMillisSpentLimit, Clock.systemUTC());
  }

  UnimprovedTimeMillisSpentTermination(long unimprovedTimeMillisSpentLimit, Clock clock) {
    this.unimprovedTimeMillisSpentLimit = unimprovedTimeMillisSpentLimit;
    if (unimprovedTimeMillisSpentLimit < 0L) {
      throw new IllegalArgumentException(
          "The unimprovedTimeMillisSpentLimit (%d) cannot be negative."
              .formatted(unimprovedTimeMillisSpentLimit));
    }
    this.clock = clock;
  }

  public long getUnimprovedTimeMillisSpentLimit() {
    return unimprovedTimeMillisSpentLimit;
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    solverCounterStarted = false;
    phaseCounterStarted = false;
    currentPhaseSendsBestSolutionEvents = false;
    solverStartedTimeMillis = -1L;
    phaseStartedTimeMillis = -1L;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    /*
     * Construction heuristics and similar phases only trigger best solution events at the end.
     * This means that these phases only provide a meaningful result at their end.
     * Unimproved time spent termination is not useful for these phases,
     * as it would terminate the solver prematurely,
     * skipping any useful phases that follow it, such as local search.
     * We avoid that by never terminating during these phases,
     * and resetting the counter to zero when the next phase starts.
     */
    currentPhaseSendsBestSolutionEvents = phaseScope.isPhaseSendingBestSolutionEvents();
    phaseCounterStarted = false;
    if (!currentPhaseSendsBestSolutionEvents) {
      solverCounterStarted = false;
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (!currentPhaseSendsBestSolutionEvents) {
      return;
    }
    // Initialization must not consume a fresh idle budget. Global history remains continuous
    // across successive improvement phases; a phase-local budget always starts over.
    if (!solverCounterStarted || !phaseCounterStarted) {
      var now = clock.millis();
      if (!solverCounterStarted) {
        solverStartedTimeMillis = now;
        solverCounterStarted = true;
      }
      if (!phaseCounterStarted) {
        phaseStartedTimeMillis = now;
        phaseCounterStarted = true;
      }
    }
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    if (!canEvaluate(solverCounterStarted)) {
      return false;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return true;
    }
    long bestSolutionTimeMillis = solverScope.getBestSolutionTimeMillis();
    return isTerminated(bestSolutionTimeMillis, solverStartedTimeMillis);
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    if (!canEvaluate(phaseCounterStarted)) {
      return false;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return true;
    }
    var bestSolutionTimeMillis = phaseScope.getPhaseBestSolutionTimeMillis();
    return isTerminated(bestSolutionTimeMillis, phaseStartedTimeMillis);
  }

  private boolean canEvaluate(boolean counterStarted) {
    return currentPhaseSendsBestSolutionEvents
        && (counterStarted || unimprovedTimeMillisSpentLimit == 0L);
  }

  private boolean isTerminated(long bestSolutionTimeMillis, long counterStartTimeMillis) {
    return getUnimprovedTimeMillisSpent(bestSolutionTimeMillis, counterStartTimeMillis)
        >= unimprovedTimeMillisSpentLimit;
  }

  private long getUnimprovedTimeMillisSpent(
      long bestSolutionTimeMillis, long counterStartTimeMillis) {
    var now = clock.millis();
    return now - Math.max(bestSolutionTimeMillis, counterStartTimeMillis);
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    if (!canEvaluate(solverCounterStarted)) {
      return 0.0;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return 1.0;
    }
    long bestSolutionTimeMillis = solverScope.getBestSolutionTimeMillis();
    return calculateTimeGradient(bestSolutionTimeMillis, solverStartedTimeMillis);
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    if (!canEvaluate(phaseCounterStarted)) {
      return 0.0;
    }
    if (unimprovedTimeMillisSpentLimit == 0L) {
      return 1.0;
    }
    var bestSolutionTimeMillis = phaseScope.getPhaseBestSolutionTimeMillis();
    return calculateTimeGradient(bestSolutionTimeMillis, phaseStartedTimeMillis);
  }

  private double calculateTimeGradient(long bestSolutionTimeMillis, long counterStartTimeMillis) {
    return TerminationGradient.ratio(
        getUnimprovedTimeMillisSpent(bestSolutionTimeMillis, counterStartTimeMillis),
        unimprovedTimeMillisSpentLimit);
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return new UnimprovedTimeMillisSpentTermination<>(unimprovedTimeMillisSpentLimit);
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
