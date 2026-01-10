package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.List;
import java.util.function.BiConsumer;

import ai.greycos.solver.core.api.solver.ProblemFactChange;
import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.AbstractSolver;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.impl.solver.termination.UniversalTermination;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

/**
 * Lightweight solver for partition threads with restricted API.
 *
 * <p>Executes configured phases on a partition; notifies parent of best solution changes.
 * No support for problem changes or early termination.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class PartitionSolver<Solution_> extends AbstractSolver<Solution_> {

  private final SolverScope<Solution_> solverScope;
  private final int partIndex;

  private BiConsumer<EventProducerId, Solution_> bestSolutionChangedListener;

  public PartitionSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      UniversalTermination<Solution_> termination,
      List<Phase<Solution_>> phaseList,
      SolverScope<Solution_> solverScope,
      int partIndex) {
    super(bestSolutionRecaller, termination, phaseList);
    this.solverScope = solverScope;
    this.partIndex = partIndex;
  }

  /**
   * Sets the listener to be notified when best solution changes.
   *
   * @param listener The listener
   */
  public void setBestSolutionChangedListener(BiConsumer<EventProducerId, Solution_> listener) {
    this.bestSolutionChangedListener = listener;
  }

  @Override
  public Solution_ solve(Solution_ initialSolution) {
    solverScope.initializeYielding();
    try {
      solverScope.setBestSolution(initialSolution);
      solvingStarted(solverScope);
      runPhases();
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
  @Deprecated(forRemoval = true)
  public boolean isEveryProblemFactChangeProcessed() {
    return false;
  }

  @Override
  @Deprecated(forRemoval = true)
  public boolean addProblemFactChange(@NonNull ProblemFactChange<Solution_> problemFactChange) {
    throw new UnsupportedOperationException(
        "The PartitionSolver does not support problem fact changes.");
  }

  @Override
  @Deprecated(forRemoval = true)
  public boolean addProblemFactChanges(
      @NonNull List<ProblemFactChange<Solution_>> problemFactChangeList) {
    throw new UnsupportedOperationException(
        "The PartitionSolver does not support problem fact changes.");
  }

  private void runPhases() {
    for (Phase<Solution_> phase : phaseList) {
      phase.solve(solverScope);

      Solution_ newBestSolution = solverScope.getBestSolution();
      if (newBestSolution != null && bestSolutionChangedListener != null) {
        bestSolutionChangedListener.accept(
            phase.getEventProducerIdSupplier().apply(0), newBestSolution);
      }

      solverScope.setWorkingSolutionFromBestSolution();

      if (globalTermination.isSolverTerminated(solverScope)) {
        break;
      }
    }
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
