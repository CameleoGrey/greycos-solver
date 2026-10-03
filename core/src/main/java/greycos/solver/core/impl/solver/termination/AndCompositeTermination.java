package greycos.solver.core.impl.solver.termination;

import java.util.List;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class AndCompositeTermination<Solution_> extends AbstractCompositeTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  public AndCompositeTermination(List<Termination<Solution_>> terminationList) {
    super(terminationList);
  }

  @SafeVarargs
  public AndCompositeTermination(Termination<Solution_>... terminations) {
    super(terminations);
  }

  /**
   * @return true if all terminations are terminated.
   */
  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return TerminationEvaluation.isSolverTerminated(this, solverScope);
  }

  /**
   * @return true if every condition is applicable and terminated.
   */
  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.isPhaseTerminated(this, phaseScope, false);
  }

  /**
   * Calculates the minimum timeGradient of all Terminations. Not supported timeGradients (-1.0) are
   * ignored. If no applicable termination supports a gradient, returns -1.0.
   *
   * @return the minimum timeGradient of the Terminations.
   */
  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return TerminationEvaluation.calculateSolverTimeGradient(this, solverScope);
  }

  /**
   * Calculates the minimum timeGradient of all Terminations. Not supported timeGradients (-1.0) are
   * ignored. If no applicable termination supports a gradient, returns -1.0.
   *
   * @return the minimum timeGradient of the Terminations.
   */
  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.calculatePhaseTimeGradient(this, phaseScope, false);
  }

  @Override
  public AndCompositeTermination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return new AndCompositeTermination<>(
        createChildThreadTerminationList(solverScope, childThreadType));
  }

  @Override
  public String toString() {
    return "And(" + terminationList + ")";
  }
}
