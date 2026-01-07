package ai.greycos.solver.core.impl.islandmodel;

import java.util.List;

import ai.greycos.solver.core.api.solver.ProblemFactChange;
import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.impl.solver.AbstractSolver;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class IslandSolver<Solution_> extends AbstractSolver<Solution_> {

  IslandSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> globalTermination) {
    super(bestSolutionRecaller, globalTermination, List.of());
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    solverScope.setWorkingSolutionFromBestSolution();
    bestSolutionRecaller.solvingStarted(solverScope);
    globalTermination.solvingStarted(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    bestSolutionRecaller.solvingEnded(solverScope);
    globalTermination.solvingEnded(solverScope);
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

  @Override
  @Deprecated(forRemoval = true)
  public boolean isEveryProblemFactChangeProcessed() {
    return false;
  }

  @Override
  @Deprecated(forRemoval = true)
  public boolean addProblemFactChange(@NonNull ProblemFactChange<Solution_> problemFactChange) {
    throw new UnsupportedOperationException("IslandSolver does not support problem fact changes.");
  }

  @Override
  @Deprecated(forRemoval = true)
  public boolean addProblemFactChanges(
      @NonNull List<ProblemFactChange<Solution_>> problemFactChangeList) {
    throw new UnsupportedOperationException("IslandSolver does not support problem fact changes.");
  }
}
