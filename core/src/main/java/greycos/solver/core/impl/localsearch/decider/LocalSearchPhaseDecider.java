package greycos.solver.core.impl.localsearch.decider;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** The decision and lifecycle contract shared by local-search algorithms. */
public interface LocalSearchPhaseDecider<Solution_> {

  void enableAssertions(EnvironmentMode environmentMode);

  void solvingStarted(SolverScope<Solution_> solverScope);

  void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope);

  void stepStarted(LocalSearchStepScope<Solution_> stepScope);

  void decideNextStep(LocalSearchStepScope<Solution_> stepScope);

  void stepEnded(LocalSearchStepScope<Solution_> stepScope);

  void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope);

  void solvingEnded(SolverScope<Solution_> solverScope);

  void solvingError(SolverScope<Solution_> solverScope, Throwable failure);

  default long getUncreditedCalculationCount() {
    return 0L;
  }
}
