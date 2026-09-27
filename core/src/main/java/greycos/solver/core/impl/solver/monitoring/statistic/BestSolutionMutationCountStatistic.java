package greycos.solver.core.impl.solver.monitoring.statistic;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.SolverEventListener;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.solution.mutation.MutationCounter;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSupport;

import org.jspecify.annotations.NonNull;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;

public class BestSolutionMutationCountStatistic<Solution_> implements SolverStatistic<Solution_> {

  private final Map<Solver<Solution_>, SolverEventListener<Solution_>> solverToEventListenerMap =
      Collections.synchronizedMap(new WeakHashMap<>());

  @Override
  public void unregister(Solver<Solution_> solver) {
    SolverEventListener<Solution_> listener = solverToEventListenerMap.remove(solver);
    if (listener != null) {
      solver.removeEventListener(listener);
    }
  }

  @Override
  public void register(Solver<Solution_> solver) {
    AbstractSolver<Solution_> defaultSolver = (AbstractSolver<Solution_>) solver;
    ScoreDirectorFactory<Solution_, ?> scoreDirectorFactory =
        defaultSolver.getScoreDirectorFactory();
    SolutionDescriptor<Solution_> solutionDescriptor = scoreDirectorFactory.getSolutionDescriptor();
    MutationCounter<Solution_> mutationCounter = new MutationCounter<>(solutionDescriptor);
    var tags = SolverMetricSupport.scope(solver).getMonitoringTags();
    Metrics.globalRegistry.removeByPreFilterId(
        new Meter.Id(
            SolverMetric.BEST_SOLUTION_MUTATION.getMeterId(), tags, null, null, Meter.Type.GAUGE));
    BestSolutionMutationCountStatisticListener<Solution_> listener =
        Metrics.gauge(
            SolverMetric.BEST_SOLUTION_MUTATION.getMeterId(),
            tags,
            new BestSolutionMutationCountStatisticListener<>(mutationCounter),
            BestSolutionMutationCountStatisticListener::getMutationCount);
    var guardedListener =
        SolverMetricSupport.guardedEventListener(SolverMetricSupport.scope(solver), listener);
    solverToEventListenerMap.put(solver, guardedListener);
    solver.addEventListener(guardedListener);
  }

  private static class BestSolutionMutationCountStatisticListener<Solution_>
      implements SolverEventListener<Solution_> {
    final MutationCounter<Solution_> mutationCounter;
    int mutationCount = 0;
    Solution_ oldBestSolution = null;

    public BestSolutionMutationCountStatisticListener(MutationCounter<Solution_> mutationCounter) {
      this.mutationCounter = mutationCounter;
    }

    public int getMutationCount() {
      return mutationCount;
    }

    @Override
    public void bestSolutionChanged(@NonNull BestSolutionChangedEvent<Solution_> event) {
      Solution_ newBestSolution = event.getNewBestSolution();
      if (oldBestSolution == null) {
        mutationCount = 0;
      } else {
        mutationCount = mutationCounter.countMutations(oldBestSolution, newBestSolution);
      }
      oldBestSolution = newBestSolution;
    }
  }
}
