package ai.greycos.solver.core.impl.solver.termination;

import ai.greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

/**
 * A Termination determines when a {@link Solver} or {@link Phase} should stop.
 *
 * <p>The {@link #isSolverTerminated(SolverScope)} method is called to determine if the solver
 * should stop. The {@link #isPhaseTerminated(AbstractPhaseScope)} method is called to determine if
 * the current phase should stop.
 *
 * <p>Implementations are expected to be immutable.
 *
 * <p>A Termination can be combined with other Terminations using {@link AndCompositeTermination} or
 * {@link OrCompositeTermination}.
 */
public interface Termination<Solution_> {

  /**
   * @param solverScope never null
   * @return true if the solver should terminate
   */
  boolean isSolverTerminated(SolverScope<Solution_> solverScope);

  /**
   * @param phaseScope never null
   * @return true if the phase should terminate
   */
  boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope);

  /**
   * @param solverScope never null
   * @return the time gradient (0.0 to 1.0) of how long the solver has been running, where 0.0 means
   *     just started and 1.0 means the termination will happen immediately
   */
  double calculateSolverTimeGradient(SolverScope<Solution_> solverScope);

  /**
   * @param phaseScope never null
   * @return the time gradient (0.0 to 1.0) of how long the phase has been running, where 0.0 means
   *     just started and 1.0 means the termination will happen immediately
   */
  double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope);

  /**
   * Called when a child thread is created to solve a partition of the problem.
   *
   * <p>Returns a termination for the child thread that is appropriate for the child thread type.
   * For example, a time-based termination might return a different termination for child threads to
   * coordinate termination across threads.
   *
   * @param solverScope never null
   * @param childThreadType never null
   * @return never null, a termination for the child thread
   */
  default Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> solverScope, ChildThreadType childThreadType) {
    return this;
  }
}
