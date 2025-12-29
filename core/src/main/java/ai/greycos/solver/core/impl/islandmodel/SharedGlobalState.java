package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Thread-safe shared state for island model, tracking the global best solution across all islands.
 * Uses volatile + synchronized for thread-safe updates with minimal contention.
 *
 * <p>Updates only occur when a better solution is found, so contention is low.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class SharedGlobalState<Solution_> {

  private volatile Solution_ bestSolution;
  private volatile Score<?> bestScore;
  private final Object lock = new Object();

  // Solver scope for solution cloning (set once and reused)
  private volatile SolverScope<Solution_> solverScope;

  // Observers for best solution changes (for Greycos event system integration)
  private final List<Consumer<Solution_>> observers = new CopyOnWriteArrayList<>();

  /**
   * Attempts to update the global best solution if the candidate is better. Thread-safe: uses
   * synchronized block to ensure atomicity of update.
   *
   * @param candidate the candidate solution, never null
   * @param candidateScore the score of the candidate solution, never null
   * @return true if the global best was updated, false otherwise
   */
  public boolean tryUpdate(Solution_ candidate, Score<?> candidateScore) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");

    synchronized (lock) {
      // If no best exists yet, accept candidate
      if (bestScore == null) {
        bestSolution = deepClone(candidate);
        bestScore = candidateScore;
        notifyObservers(bestSolution);
        return true;
      }

      // Only update if candidate is better (higher score is better in Greycos)
      // Use raw comparison to avoid type casting issues
      int comparisonResult = ((Score) candidateScore).compareTo((Score) bestScore);
      if (comparisonResult > 0) {
        bestSolution = deepClone(candidate);
        bestScore = candidateScore;
        notifyObservers(bestSolution);
        return true;
      }

      return false;
    }
  }

  /**
   * Sets the solver scope for solution cloning. Must be called before any tryUpdate operations.
   *
   * @param solverScope the solver scope to use for cloning
   */
  public void setSolverScope(SolverScope<Solution_> solverScope) {
    Objects.requireNonNull(solverScope, "Solver scope cannot be null");
    this.solverScope = solverScope;
  }

  /**
   * Returns the current global best solution. Volatile read ensures visibility across threads.
   *
   * @return the global best solution, or null if no solution has been set yet
   */
  public Solution_ getBestSolution() {
    return bestSolution;
  }

  /**
   * Returns the score of the current global best solution. Volatile read ensures visibility across
   * threads.
   *
   * @return the global best score, or null if no solution has been set yet
   */
  public Score<?> getBestScore() {
    return bestScore;
  }

  /**
   * Adds an observer to be notified when the global best solution changes. Observers are called
   * from within the synchronized block, so they should be fast.
   *
   * @param observer the observer to add, never null
   */
  public void addObserver(Consumer<Solution_> observer) {
    Objects.requireNonNull(observer, "Observer cannot be null");
    observers.add(observer);
  }

  /**
   * Removes an observer from notification list.
   *
   * @param observer the observer to remove
   */
  public void removeObserver(Consumer<Solution_> observer) {
    observers.remove(observer);
  }

  /**
   * Returns the current list of observers.
   *
   * @return an unmodifiable list of observers
   */
  public List<Consumer<Solution_>> getObservers() {
    return new ArrayList<>(observers);
  }

  /**
   * Notifies all observers of a new best solution. Called from within synchronized block.
   *
   * @param solution the new best solution
   */
  private void notifyObservers(Solution_ solution) {
    for (Consumer<Solution_> observer : observers) {
      try {
        observer.accept(solution);
      } catch (Exception e) {
        // Log but don't propagate to avoid breaking other observers
        System.err.println("Observer notification failed: " + e.getMessage());
      }
    }
  }

  /**
   * Performs a deep clone of a solution using Greycos's solution cloning infrastructure.
   *
   * @param solution the solution to clone
   * @return a deep clone of the solution
   */
  @SuppressWarnings("unchecked")
  private Solution_ deepClone(Solution_ solution) {
    if (solution == null) {
      return null;
    }
    if (solverScope == null) {
      throw new IllegalStateException(
          "Solver scope not set. Call setSolverScope() before cloning.");
    }
    // Use Greycos's solution cloner from the score director
    return solverScope.getScoreDirector().cloneSolution(solution);
  }

  /** Resets the global state to initial conditions. Primarily used for testing. */
  public void reset() {
    synchronized (lock) {
      bestSolution = null;
      bestScore = null;
    }
  }

  @Override
  public String toString() {
    return "SharedGlobalState{"
        + "bestScore="
        + bestScore
        + ", hasBestSolution="
        + (bestSolution != null)
        + ", observerCount="
        + observers.size()
        + '}';
  }
}
