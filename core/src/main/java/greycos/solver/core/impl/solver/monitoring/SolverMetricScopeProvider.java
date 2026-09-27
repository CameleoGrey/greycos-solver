package greycos.solver.core.impl.solver.monitoring;

import greycos.solver.core.impl.solver.scope.SolverScope;

/** Internal access to the scope whose metrics a solver owns. */
public interface SolverMetricScopeProvider<Solution_> {
  SolverScope<Solution_> getSolverScope();
}
