package greycos.solver.core.impl.solver.monitoring.statistic;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSupport;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;

public class MemoryUseStatistic<Solution_> implements SolverStatistic<Solution_> {

  @Override
  public void unregister(Solver<Solution_> solver) {
    // Intentionally Empty: JVM memory is not bound to a particular solver
  }

  @Override
  public void register(Solver<Solution_> solver) {
    var scope = SolverMetricSupport.scope(solver);
    if ("root".equals(scope.getMetricSource())) {
      new JvmMemoryMetrics(scope.getMonitoringTags()).bindTo(Metrics.globalRegistry);
    }
  }
}
