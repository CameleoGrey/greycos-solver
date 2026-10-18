package greycos.solver.core.api.solver.event;

import java.time.Duration;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.ProblemSizeStatistics;
import greycos.solver.core.api.solver.SolverRunSummary;

/**
 * Delivered in a consumer thread at the end of the solving process and contains the final {@link
 * PlanningSolution best solution} found. Built-in events also provide statistics for the entire
 * run. The statistics methods default to throwing {@link UnsupportedOperationException} for
 * compatibility with custom events and lambdas.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@FunctionalInterface
public interface FinalBestSolutionEvent<Solution_> extends SolverRunSummary {
  /**
   * @return the {@link PlanningSolution best solution} found by the solver
   */
  Solution_ solution();

  @Override
  default Duration solvingDuration() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default long scoreCalculationCount() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default long scoreCalculationSpeed() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default long moveEvaluationCount() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default long moveEvaluationSpeed() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default boolean isSolutionInitialized() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }

  @Override
  default ProblemSizeStatistics problemSizeStatistics() {
    throw new UnsupportedOperationException(
        "This final solution event does not provide run statistics.");
  }
}
