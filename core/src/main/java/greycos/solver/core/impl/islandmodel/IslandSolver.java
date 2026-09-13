package greycos.solver.core.impl.islandmodel;

import java.util.List;

import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/** Minimal solver implementation for island agents. Does not support solve() or problem changes. */
@NullMarked
final class IslandSolver<Solution_> extends AbstractSolver<Solution_> {

  private boolean scoreDirectorClosed;

  IslandSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> globalTermination,
      List<Phase<Solution_>> phaseList) {
    super(bestSolutionRecaller, globalTermination, phaseList);
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
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    withScoreDirectorCleanup(solverScope, () -> super.solvingEnded(solverScope));
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    withScoreDirectorCleanup(solverScope, () -> super.solvingError(solverScope, exception));
  }

  private void withScoreDirectorCleanup(SolverScope<Solution_> solverScope, Runnable cleanup) {
    try {
      cleanup.run();
    } catch (RuntimeException | Error failure) {
      try {
        closeScoreDirector(solverScope);
      } catch (RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    closeScoreDirector(solverScope);
  }

  private void closeScoreDirector(SolverScope<Solution_> solverScope) {
    if (!scoreDirectorClosed) {
      scoreDirectorClosed = true;
      solverScope.getScoreDirector().close();
    }
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
