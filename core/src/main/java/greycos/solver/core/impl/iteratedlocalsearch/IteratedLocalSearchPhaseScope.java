package greycos.solver.core.impl.iteratedlocalsearch;

import greycos.solver.core.impl.move.SolutionAssignmentDiagnostics;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.random.RandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Phase-wide counters and working-score history, never reset at episode boundaries. */
public final class IteratedLocalSearchPhaseScope<Solution_> extends AbstractPhaseScope<Solution_> {
  private IteratedLocalSearchStepScope<Solution_> lastCompletedStepScope;
  private RandomSource controllerRandom;
  long completedIterations;
  long acceptedIterations;
  long rejectedIterations;
  long failedPerturbations;
  long noChangeCount;
  long interruptedIterations;
  long migrantRestarts;
  long episodes;
  long initialEpisodes;
  long perturbationAttempts;
  long episodeAttempts;
  long speculativeSelectionAttempts;
  long snapshotNanos;
  long restorationNanos;
  long resourceSetupNanos;
  SolutionAssignmentDiagnostics assignmentDiagnostics;
  boolean evaluationResourcesStarted;
  boolean perturbationSolvingStarted;
  boolean perturbationPhaseStarted;
  int strengthIndex;
  int strength;
  String completionReason;

  public IteratedLocalSearchPhaseScope(SolverScope<Solution_> solverScope, int phaseIndex) {
    super(solverScope, phaseIndex);
    lastCompletedStepScope =
        new IteratedLocalSearchStepScope<>(
            this, -1, IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
  }

  @Override
  public IteratedLocalSearchStepScope<Solution_> getLastCompletedStepScope() {
    return lastCompletedStepScope;
  }

  public void setLastCompletedStepScope(IteratedLocalSearchStepScope<Solution_> stepScope) {
    lastCompletedStepScope = stepScope;
  }

  public void initializeControllerRandom() {
    controllerRandom =
        RandomSource.seeded(solverScope.getWorkingRandom().factoryUsage().nextLong());
  }

  @Override
  public RandomSource getWorkingRandom() {
    return controllerRandom == null ? super.getWorkingRandom() : controllerRandom;
  }

  public long getCompletedIterations() {
    return completedIterations;
  }

  public long getAcceptedIterations() {
    return acceptedIterations;
  }

  public long getRejectedIterations() {
    return rejectedIterations;
  }

  public long getFailedPerturbations() {
    return failedPerturbations;
  }

  public long getNoChangeCount() {
    return noChangeCount;
  }

  public long getInterruptedIterations() {
    return interruptedIterations;
  }

  public long getMigrantRestarts() {
    return migrantRestarts;
  }

  public long getEpisodes() {
    return episodes;
  }

  public int getStrengthIndex() {
    return strengthIndex;
  }

  public int getStrength() {
    return strength;
  }

  public String getCompletionReason() {
    return completionReason;
  }
}
