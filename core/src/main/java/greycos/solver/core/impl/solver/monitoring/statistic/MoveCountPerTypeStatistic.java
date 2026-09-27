package greycos.solver.core.impl.solver.monitoring.statistic;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSupport;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;

public class MoveCountPerTypeStatistic<Solution_> implements SolverStatistic<Solution_> {

  private final Map<Solver<Solution_>, PhaseLifecycleListenerAdapter<Solution_>>
      solverToPhaseLifecycleListenerMap = Collections.synchronizedMap(new WeakHashMap<>());

  @Override
  public void unregister(Solver<Solution_> solver) {
    var listener = solverToPhaseLifecycleListenerMap.remove(solver);
    if (listener != null) {
      ((AbstractSolver<Solution_>) solver).removePhaseLifecycleListener(listener);
      ((MoveCountPerTypeStatisticListener<Solution_>)
              ((SolverMetricSupport.GuardedPhaseListener<Solution_>) listener).delegate())
          .unregister(solver);
    }
  }

  @Override
  public void register(Solver<Solution_> solver) {
    var defaultSolver = (AbstractSolver<Solution_>) solver;
    var listener = new MoveCountPerTypeStatistic.MoveCountPerTypeStatisticListener<Solution_>();
    var guardedListener =
        SolverMetricSupport.guardedPhaseListener(SolverMetricSupport.scope(solver), listener);
    solverToPhaseLifecycleListenerMap.put(solver, guardedListener);
    defaultSolver.addPhaseLifecycleListener(guardedListener);
  }

  private static class MoveCountPerTypeStatisticListener<Solution_>
      extends PhaseLifecycleListenerAdapter<Solution_> {
    private final Map<Tags, Map<String, AtomicLong>> tagsToMoveCountMap = new ConcurrentHashMap<>();

    @Override
    public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
      // The metric must be collected when the phase ends instead of when the solver ends
      // because there is no guarantee this listener will run the phase event before the
      // StatisticRegistry listener
      var moveCountPerType = phaseScope.getSolverScope().getReportedMoveCountsByType();
      var tags = phaseScope.getSolverScope().getMonitoringTags();
      moveCountPerType.forEach(
          (type, count) -> {
            var key = SolverMetric.MOVE_COUNT_PER_TYPE.getMeterId() + "." + type;
            var counters = tagsToMoveCountMap.computeIfAbsent(tags, ignored -> new HashMap<>());
            var counter =
                counters.computeIfAbsent(
                    key,
                    ignored -> {
                      var value = new AtomicLong();
                      SolverMetricUtil.rebindGauge(key, tags, value);
                      return value;
                    });
            counter.set(count);
          });
    }

    void unregister(Solver<Solution_> solver) {
      tagsToMoveCountMap.forEach(
          (tags, counts) ->
              counts
                  .keySet()
                  .forEach(
                      meter ->
                          Metrics.globalRegistry.remove(
                              new Meter.Id(meter, tags, null, null, Meter.Type.GAUGE))));
      tagsToMoveCountMap.clear();
    }
  }
}
