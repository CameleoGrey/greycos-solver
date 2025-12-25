package ai.greycos.solver.core.impl.partitionedsearch;

import java.util.List;
import java.util.function.BiConsumer;

import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.termination.Termination;

import org.jspecify.annotations.NullMarked;

/**
 * Lightweight solver instance that executes configured phases on a partition.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li>Extends AbstractSolver but with restricted API
 *   <li>No support for problem fact changes or early termination
 *   <li>Dedicated SolverScope for child thread context
 *   <li>Terminates when parent phase termination is signaled
 * </ul>
 *
 * @param <Solution_> solution type, class with {@link
 *     ai.greycos.solver.core.api.domain.solution.PlanningSolution} annotation
 */
@NullMarked
public class PartitionSolver<Solution_> {

  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final Termination<Solution_> termination;
  private final List<Phase<Solution_>> phaseList;
  private final SolverScope<Solution_> solverScope;
  private final int partIndex;

  private BiConsumer<EventProducerId, Solution_> bestSolutionChangedListener;

  public PartitionSolver(
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      Termination<Solution_> termination,
      List<Phase<Solution_>> phaseList,
      SolverScope<Solution_> solverScope,
      int partIndex) {
    this.bestSolutionRecaller = bestSolutionRecaller;
    this.termination = termination;
    this.phaseList = phaseList;
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

  /**
   * Solves the partition.
   *
   * @param initialSolution The initial solution for this partition
   */
  public void solve(Solution_ initialSolution) {
    solverScope.getScoreDirector().setWorkingSolution(initialSolution);

    // Initialize best solution
    Solution_ bestSolution = solverScope.getScoreDirector().cloneWorkingSolution();
    solverScope.setBestSolution(bestSolution);
    var score = solverScope.calculateScore();
    solverScope.setBestScore(score);

    // Run phases
    runPhases();

    // Clean up
    solverScope.getScoreDirector().close();
  }

  private void runPhases() {
    for (Phase<Solution_> phase : phaseList) {
      phase.solve(solverScope);

      // After each phase, update best solution
      Solution_ newBestSolution = solverScope.getBestSolution();
      if (newBestSolution != null && bestSolutionChangedListener != null) {
        bestSolutionChangedListener.accept(
            phase.getEventProducerIdSupplier().apply(0), newBestSolution);
      }

      // Set working solution from best for next phase
      solverScope.setWorkingSolutionFromBestSolution();

      // Check termination - PhaseTermination has phase methods
      // According to spec, partitions should terminate when parent phase termination is signaled
      if (termination.isSolverTerminated(solverScope)) {
        break;
      }
    }
  }

  /**
   * Gets score calculation count.
   *
   * @return The calculation count
   */
  public long getScoreCalculationCount() {
    return solverScope.getScoreCalculationCount();
  }

  /**
   * Gets the solver scope.
   *
   * @return The solver scope
   */
  public SolverScope<Solution_> getSolverScope() {
    return solverScope;
  }

  /**
   * Gets the partition index.
   *
   * @return The partition index
   */
  public int getPartIndex() {
    return partIndex;
  }

  /**
   * Called before solving starts. Subclasses can override for custom behavior.
   *
   * @param solverScope solver scope
   */
  protected void solvingStarted(SolverScope<Solution_> solverScope) {
    // Default implementation - can be overridden by subclasses
  }

  /**
   * Called after solving ends. Subclasses can override for custom behavior.
   *
   * @param solverScope solver scope
   */
  protected void solvingEnded(SolverScope<Solution_> solverScope) {
    // Default implementation - can be overridden by subclasses
  }
}
