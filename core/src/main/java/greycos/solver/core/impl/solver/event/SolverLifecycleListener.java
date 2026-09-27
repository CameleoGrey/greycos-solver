package greycos.solver.core.impl.solver.event;

import java.util.EventListener;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see SolverLifecycleListenerAdapter
 */
public interface SolverLifecycleListener<Solution_> extends EventListener {

  void solvingStarted(SolverScope<Solution_> solverScope);

  void solvingEnded(SolverScope<Solution_> solverScope);

  /**
   * Invoked when the {@link greycos.solver.core.api.solver.Solver} run fails, including with an
   * {@link Error}. The solver's error dispatch continues through its registered solver listeners
   * and phases even if cleanup fails; distinct cleanup failures are suppressed on the original
   * failure, which remains the primary cause. The normal {@link #solvingEnded(SolverScope)}
   * lifecycle is not completed on this path. For internal purposes only.
   */
  default void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    // no-op
  }
}
