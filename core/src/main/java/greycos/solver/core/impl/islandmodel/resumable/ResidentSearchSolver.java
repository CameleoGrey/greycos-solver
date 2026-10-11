package greycos.solver.core.impl.islandmodel.resumable;

import java.util.List;

import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.islandmodel.SolutionSyncMove;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricScopeProvider;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.UniversalTermination;

import io.micrometer.core.instrument.Metrics;

/** Owns one resident trajectory's solver lifecycle, random delegates and scoring context. */
final class ResidentSearchSolver<Solution_> extends AbstractSolver<Solution_>
    implements SolverMetricScopeProvider<Solution_> {

  private SolverScope<Solution_> solverScope;
  private boolean metricsFinished;

  ResidentSearchSolver(
      EnvironmentMode mode,
      ScoreDirectorFactory<Solution_, ?> factory,
      BestSolutionRecaller<Solution_> recaller,
      UniversalTermination<Solution_> termination,
      List<Phase<Solution_>> phases) {
    super(mode, factory, recaller, termination, phases);
  }

  @Override
  public SolverScope<Solution_> getSolverScope() {
    return solverScope;
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> scope) {
    solverScope = scope;
    super.solvingStarted(scope);
    scope
        .getMetricRun()
        .publish(
            () ->
                scope.getSolverMetricSet().stream()
                    .filter(ResidentSearchSolver::isSessionMetric)
                    .forEach(metric -> metric.register(this)));
  }

  private static boolean isSessionMetric(SolverMetric metric) {
    return switch (metric) {
      case SOLVE_DURATION,
          ERROR_COUNT,
          MEMORY_USE,
          PROBLEM_ENTITY_COUNT,
          PROBLEM_VARIABLE_COUNT,
          PROBLEM_VALUE_COUNT,
          PROBLEM_SIZE_LOG ->
          false;
      default -> true;
    };
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> scope) {
    solverScope = scope;
    Throwable failure = null;
    try {
      super.solvingEnded(scope);
    } catch (RuntimeException | Error original) {
      failure = original;
      throw original;
    } finally {
      try {
        finishMetrics(scope);
      } catch (RuntimeException | Error cleanup) {
        if (failure == null) throw cleanup;
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
    }
  }

  @Override
  public void solvingError(SolverScope<Solution_> scope, Throwable failure) {
    // Startup can fail before solvingStarted has adopted the scope.
    solverScope = scope;
    try {
      super.solvingError(scope, failure);
    } finally {
      try {
        finishMetrics(scope);
      } catch (RuntimeException | Error cleanup) {
        if (cleanup != failure) failure.addSuppressed(cleanup);
      }
    }
  }

  private void finishMetrics(SolverScope<Solution_> scope) {
    if (metricsFinished) return;
    metricsFinished = true;
    scope.getMetricRun().seal();
    scope
        .getMetricRun()
        .cleanup(
            () -> {
              Throwable failure = null;
              for (var metric : scope.getSolverMetricSet()) {
                if (!isSessionMetric(metric)) continue;
                try {
                  metric.unregister(this);
                } catch (RuntimeException | Error cleanup) {
                  if (failure == null) failure = cleanup;
                  else if (cleanup != failure) failure.addSuppressed(cleanup);
                }
              }
              // Phase-owned gauges have no registration listener to remove. Their episode tags must
              // not
              // survive a restart, including additional constraint and move-type tags on derived
              // meters.
              var tags = scope.getMonitoringTags();
              if (tags.stream().anyMatch(tag -> tag.getKey().equals("resumable.session"))) {
                for (var meter : Metrics.globalRegistry.getMeters()) {
                  if (tags.stream()
                      .allMatch(tag -> tag.getValue().equals(meter.getId().getTag(tag.getKey())))) {
                    try {
                      Metrics.globalRegistry.remove(meter);
                    } catch (RuntimeException | Error cleanup) {
                      if (failure == null) failure = cleanup;
                      else if (cleanup != failure) failure.addSuppressed(cleanup);
                    }
                  }
                }
              }
              if (failure instanceof Error error) throw error;
              if (failure instanceof RuntimeException exception) throw exception;
            });
  }

  /** Carries a legacy migration queued by the final step onto the next phase's working clone. */
  void restoreWorkingForNextPhase(SolverScope<Solution_> scope) {
    var pending = scope.consumePendingMove();
    super.restoreWorkingSolutionForNextPhase(scope);
    if (pending != null
        && pending.move() instanceof SolutionSyncMove<Solution_> syncMove
        && pending.score() != null
        && improvesBestScore(pending.score(), scope.getBestScore())) {
      scope.setPendingMoveIfBetter(
          syncMove.rebase(scope.getScoreDirector()), pending.score(), pending.requiresReset());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static boolean improvesBestScore(InnerScore<?> candidate, InnerScore<?> best) {
    return ((InnerScore) candidate).compareTo((InnerScore) best) > 0;
  }

  @Override
  public Solution_ solve(Solution_ problem) {
    throw new UnsupportedOperationException("Resident trajectories are advanced by their owner.");
  }

  @Override
  public boolean isSolving() {
    return true;
  }

  @Override
  public boolean terminateEarly() {
    throw new UnsupportedOperationException("Cancel the enclosing solver.");
  }

  @Override
  public boolean isTerminateEarly() {
    return false;
  }

  @Override
  public void addProblemChange(ProblemChange<Solution_> change) {
    throw new UnsupportedOperationException("Apply problem changes to the enclosing solver.");
  }

  @Override
  public void addProblemChanges(List<ProblemChange<Solution_>> changes) {
    throw new UnsupportedOperationException("Apply problem changes to the enclosing solver.");
  }

  @Override
  public boolean isEveryProblemChangeProcessed() {
    return true;
  }
}
