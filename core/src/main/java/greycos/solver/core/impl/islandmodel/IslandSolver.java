package greycos.solver.core.impl.islandmodel;

import java.util.List;

import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.monitoring.SolverMetricScopeProvider;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/** Minimal solver implementation for island agents. Does not support solve() or problem changes. */
@NullMarked
final class IslandSolver<Solution_> extends AbstractSolver<Solution_>
    implements SolverMetricScopeProvider<Solution_> {

  private SolverScope<Solution_> solverScope;
  private boolean metricsFinished;

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
                    .filter(IslandSolver::isIslandMetric)
                    .forEach(metric -> metric.register(this)));
    publishWork(scope);
  }

  private static boolean isIslandMetric(SolverMetric metric) {
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

  private void publishWork(SolverScope<Solution_> scope) {
    if (!scope.getMetricSource().equals("root")) {
      scope.publishMetricSample(SolverMetricSamples.captureFinal(scope, scope.getMetricSource()));
    }
  }

  private void finishMetrics(SolverScope<Solution_> scope) {
    if (metricsFinished) return;
    metricsFinished = true;
    Throwable failure = null;
    try {
      publishWork(scope);
    } catch (RuntimeException | Error publicationFailure) {
      failure = publicationFailure;
      throw publicationFailure;
    } finally {
      try {
        scope
            .getMetricRun()
            .cleanup(
                () ->
                    scope.getSolverMetricSet().stream()
                        .filter(IslandSolver::isIslandMetric)
                        .forEach(metric -> metric.unregister(this)));
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) throw cleanupFailure;
        if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> scope) {
    Throwable failure = null;
    try {
      super.solvingEnded(scope);
    } catch (RuntimeException | Error solveFailure) {
      failure = solveFailure;
      throw solveFailure;
    } finally {
      try {
        finishMetrics(scope);
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) throw cleanupFailure;
        if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
      }
    }
  }

  @Override
  public void solvingError(SolverScope<Solution_> scope, Throwable failure) {
    try {
      super.solvingError(scope, failure);
    } finally {
      try {
        finishMetrics(scope);
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
      }
    }
  }

  IslandSolver(
      EnvironmentMode environmentMode,
      ScoreDirectorFactory<Solution_, ?> scoreDirectorFactory,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> globalTermination,
      List<Phase<Solution_>> phaseList) {
    super(
        environmentMode, scoreDirectorFactory, bestSolutionRecaller, globalTermination, phaseList);
  }

  @Override
  public void runPhases(SolverScope<Solution_> solverScope) {
    super.runPhases(solverScope);
  }

  @Override
  protected void restoreWorkingSolutionForNextPhase(SolverScope<Solution_> solverScope) {
    var pending = solverScope.consumePendingMove();
    super.restoreWorkingSolutionForNextPhase(solverScope);
    // A migration queued at the final step still targets the old entity instances.
    // Rebase its captured target assignments onto the replacement working clone.
    if (pending != null
        && pending.move() instanceof SolutionSyncMove<Solution_> syncMove
        && pending.score() != null
        && improvesBestScore(pending.score(), solverScope.getBestScore())) {
      solverScope.setPendingMoveIfBetter(
          syncMove.rebase(solverScope.getScoreDirector()),
          pending.score(),
          pending.requiresReset());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static boolean improvesBestScore(InnerScore<?> candidate, InnerScore<?> best) {
    return ((InnerScore) candidate).compareTo((InnerScore) best) > 0;
  }

  @Override
  public Solution_ solve(Solution_ initialSolution) {
    throw new UnsupportedOperationException("IslandSolver does not support solve().");
  }

  @Override
  public boolean isSolving() {
    return false;
  }

  @Override
  public boolean isTerminateEarly() {
    return false;
  }

  @Override
  public boolean terminateEarly() {
    return false;
  }

  @Override
  public boolean isEveryProblemChangeProcessed() {
    return true;
  }

  @Override
  public void addProblemChange(@NonNull ProblemChange<Solution_> problemChange) {
    throw new UnsupportedOperationException("IslandSolver does not support problem changes.");
  }

  @Override
  public void addProblemChanges(@NonNull List<ProblemChange<Solution_>> problemChangeList) {
    throw new UnsupportedOperationException("IslandSolver does not support problem changes.");
  }
}
