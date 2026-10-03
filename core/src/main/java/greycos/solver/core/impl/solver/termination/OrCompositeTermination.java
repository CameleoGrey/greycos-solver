package greycos.solver.core.impl.solver.termination;

import java.util.List;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class OrCompositeTermination<Solution_> extends AbstractCompositeTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  public OrCompositeTermination(List<Termination<Solution_>> terminationList) {
    super(terminationList);
  }

  @SafeVarargs
  public OrCompositeTermination(Termination<Solution_>... terminations) {
    super(terminations);
  }

  /**
   * @param solverScope never null
   * @return true if any one of the terminations is terminated.
   */
  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> solverScope) {
    return TerminationEvaluation.isSolverTerminated(this, solverScope);
  }

  /**
   * @return true if any one of the supported terminations is terminated.
   */
  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.isPhaseTerminated(this, phaseScope, false);
  }

  /**
   * Calculates the maximum timeGradient of all Terminations. Not supported timeGradients (-1.0) are
   * ignored. If no applicable termination supports a gradient, returns -1.0.
   *
   * @return the maximum timeGradient of the terminations.
   */
  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> solverScope) {
    return TerminationEvaluation.calculateSolverTimeGradient(this, solverScope);
  }

  /**
   * Calculates the maximum timeGradient of all Terminations. Not supported timeGradients (-1.0) are
   * ignored. If no applicable termination supports a gradient, returns -1.0.
   *
   * @return the maximum timeGradient of the supported terminations.
   */
  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return TerminationEvaluation.calculatePhaseTimeGradient(this, phaseScope, false);
  }

  @Override
  public OrCompositeTermination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return new OrCompositeTermination<>(
        createChildThreadTerminationList(solverScope, childThreadType));
  }

  @Override
  public String toString() {
    return "Or(" + terminationList + ")";
  }
}
