package greycos.solver.core.impl.solver.monitoring.statistic;

import greycos.solver.core.api.solver.Solver;

public interface SolverStatistic<Solution_> {
  void unregister(Solver<Solution_> solver);

  void register(Solver<Solution_> solver);
}
