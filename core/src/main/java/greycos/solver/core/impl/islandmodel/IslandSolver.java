package greycos.solver.core.impl.islandmodel;

import java.util.List;

import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/** Minimal solver implementation for island agents. Does not support solve() or problem changes. */
@NullMarked
final class IslandSolver<Solution_> extends AbstractSolver<Solution_> {

  IslandSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> globalTermination,
      List<Phase<Solution_>> phaseList) {
    super(bestSolutionRecaller, globalTermination, phaseList);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    solverScope.setWorkingSolutionFromBestSolution();
    bestSolutionRecaller.solvingStarted(solverScope);
    globalTermination.solvingStarted(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      bestSolutionRecaller.solvingEnded(solverScope);
      globalTermination.solvingEnded(solverScope);
    } finally {
      solverScope.getScoreDirector().close();
    }
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
    try {
      super.solvingError(solverScope, exception);
    } finally {
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
