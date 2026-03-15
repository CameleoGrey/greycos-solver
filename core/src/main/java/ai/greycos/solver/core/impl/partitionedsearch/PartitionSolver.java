package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.AbstractSolver;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * Lightweight solver for partition threads with restricted API.
 *
 * <p>Executes configured phases on a partition; notifies parent of best solution changes. No
 * support for problem changes or early termination.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class PartitionSolver<Solution_> extends AbstractSolver<Solution_> {

  private final SolverScope<Solution_> solverScope;
  private final int partIndex;

  public PartitionSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> termination,
      List<Phase<Solution_>> phaseList,
      SolverScope<Solution_> solverScope,
      int partIndex) {
    super(bestSolutionRecaller, termination, phaseList);
    this.solverScope = solverScope;
    this.partIndex = partIndex;
    // Child phases must notify the child solver, not the parent solver.
    this.solverScope.setSolver(this);
  }

  @Override
  public Solution_ solve(Solution_ initialSolution) {
    solverScope.initializeYielding();
    try {
      solverScope.setBestSolution(initialSolution);
      solvingStarted(solverScope);
      runPhases(solverScope);
      solvingEnded(solverScope);
      return solverScope.getBestSolution();
    } finally {
      solverScope.destroyYielding();
    }
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
    return false;
  }

  @Override
  public void addProblemChange(@NonNull ProblemChange<Solution_> problemChange) {
    throw new UnsupportedOperationException(
        "The PartitionSolver does not support problem changes.");
  }

  @Override
  public void addProblemChanges(@NonNull List<ProblemChange<Solution_>> problemChangeList) {
    throw new UnsupportedOperationException(
        "The PartitionSolver does not support problem changes.");
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    solverScope.getScoreDirector().close();
  }

  public long getScoreCalculationCount() {
    return solverScope.getScoreCalculationCount();
  }

  public SolverScope<Solution_> getSolverScope() {
    return solverScope;
  }

  public int getPartIndex() {
    return partIndex;
  }
}
