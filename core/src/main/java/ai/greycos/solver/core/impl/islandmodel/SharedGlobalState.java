package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;

/**
 * Thread-safe shared state tracking global best solution across all islands.
 *
 * <p>Uses double-checked locking with volatile for minimal contention.
 * Fast path for failed updates, slow path only for potential improvements.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class SharedGlobalState<Solution_> {

  private volatile Solution_ bestSolution;
  private volatile Score<?> bestScore;
  private final Object lock = new Object();

  private final List<Consumer<Solution_>> observers = new CopyOnWriteArrayList<>();

  public boolean tryUpdate(Solution_ candidate, Score<?> candidateScore) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");

    Score<?> currentBest = bestScore;
    if (currentBest != null) {
      @SuppressWarnings("unchecked")
      int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
      if (comparison <= 0) {
        return false;
      }
    }

    synchronized (lock) {
      currentBest = bestScore;
      if (currentBest != null) {
        @SuppressWarnings("unchecked")
        int comparison = ((Score) candidateScore).compareTo((Score) currentBest);
        if (comparison <= 0) {
          return false;
        }
      }

      bestScore = candidateScore;
      bestSolution = candidate;
      notifyObservers(candidate);
      return true;
    }
  }

  public Solution_ getBestSolution() {
    return bestSolution;
  }

  public Score<?> getBestScore() {
    return bestScore;
  }

  public void addObserver(Consumer<Solution_> observer) {
    Objects.requireNonNull(observer, "Observer cannot be null");
    observers.add(observer);
  }

  public void removeObserver(Consumer<Solution_> observer) {
    observers.remove(observer);
  }

  public List<Consumer<Solution_>> getObservers() {
    return new ArrayList<>(observers);
  }

  private void notifyObservers(Solution_ solution) {
    for (Consumer<Solution_> observer : observers) {
      try {
        observer.accept(solution);
      } catch (Exception e) {
        System.err.println("Observer notification failed: " + e.getMessage());
      }
    }
  }

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
