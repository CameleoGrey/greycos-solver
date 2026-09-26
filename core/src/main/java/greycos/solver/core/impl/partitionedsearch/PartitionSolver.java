package greycos.solver.core.impl.partitionedsearch;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.change.ProblemChange;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.phase.Phase;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.UniversalTermination;

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
      EnvironmentMode environmentMode,
      ScoreDirectorFactory<Solution_, ?> scoreDirectorFactory,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> termination,
      List<Phase<Solution_>> phaseList,
      SolverScope<Solution_> solverScope,
      int partIndex) {
    super(environmentMode, scoreDirectorFactory, bestSolutionRecaller, termination, phaseList);
    this.solverScope = solverScope;
    this.partIndex = partIndex;
    // Child phases must notify the child solver, not the parent solver.
    this.solverScope.setSolver(this);
  }

  @Override
  public Solution_ solve(Solution_ initialSolution) {
    solverScope.transferWorkingRandomOwnershipToCurrentThread();
    solverScope.initializeYielding();
    try {
      solverScope.setBestSolution(initialSolution);
      solvingStarted(solverScope);
      runPhases(solverScope);
      solvingEnded(solverScope);
      return solverScope.getBestSolution();
    } catch (Exception failure) {
      try {
        solvingError(solverScope, failure);
      } catch (Exception cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
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
