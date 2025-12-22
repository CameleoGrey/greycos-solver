package ai.greycos.solver.core.impl.solver.termination;

import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

/**
 * A bridge that allows a phase termination to access the solver termination. This is used to
 * coordinate termination between phases and the solver.
 */
public class PhaseToSolverTerminationBridge<Solution_> implements Termination<Solution_> {

  private final Termination<Solution_> solverTermination;

  public PhaseToSolverTerminationBridge(Termination<Solution_> solverTermination) {
    this.solverTermination = solverTermination;
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return solverTermination.isSolverTerminated(solverScope);
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return solverTermination.isSolverTerminated(phaseScope.getSolverScope());
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return solverTermination.calculateSolverTimeGradient(solverScope);
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return solverTermination.calculateSolverTimeGradient(phaseScope.getSolverScope());
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    if (childThreadType == ChildThreadType.PART_THREAD) {
      // Remove of the bridge (which is nested if there's a phase termination), PhaseConfig will add
      // it again
      return solverTermination.createChildThreadTermination(solverScope, childThreadType);
    } else {
      throw new IllegalStateException(
          "The childThreadType (" + childThreadType + ") is not implemented.");
    }
  }

  @Override
  public String toString() {
    return "PhaseToSolverTerminationBridge(" + solverTermination + ")";
  }
}
