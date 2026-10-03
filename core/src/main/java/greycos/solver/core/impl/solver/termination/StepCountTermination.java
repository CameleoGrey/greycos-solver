package greycos.solver.core.impl.solver.termination;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class StepCountTermination<Solution_> extends AbstractPhaseTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final int stepCountLimit;

  public StepCountTermination(int stepCountLimit) {
    this.stepCountLimit = stepCountLimit;
    if (stepCountLimit < 0) {
      throw new IllegalArgumentException(
          "The stepCountLimit (" + stepCountLimit + ") cannot be negative.");
    }
  }

  public int getStepCountLimit() {
    return stepCountLimit;
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    int nextStepIndex = phaseScope.getNextStepIndex();
    return nextStepIndex >= stepCountLimit;
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    int nextStepIndex = phaseScope.getNextStepIndex();
    return TerminationGradient.ratio(nextStepIndex, stepCountLimit);
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return false; // Phase termination doesn't terminate solver
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return -1.0; // This phase termination does not provide a solver time gradient.
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return new StepCountTermination<>(stepCountLimit);
  }

  @Override
  public String toString() {
    return "StepCount(" + stepCountLimit + ")";
  }
}
