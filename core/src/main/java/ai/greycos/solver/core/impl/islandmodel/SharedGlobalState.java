package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.score.Score;

/**
 * Thread-safe shared state for island model, tracking global best solution across all islands. Uses
 * double-checked locking with volatile for thread-safe updates with minimal contention.
 */
public class SharedGlobalState<Solution_> {

  public static final class BestSolutionSnapshot<Solution_> {
    private final Solution_ solution;
    private final Score<?> score;

    private BestSolutionSnapshot(Solution_ solution, Score<?> score) {
      this.solution = Objects.requireNonNull(solution, "Best solution cannot be null");
      this.score = Objects.requireNonNull(score, "Best score cannot be null");
    }

    public Solution_ getSolution() {
      return solution;
    }

    public Score<?> getScore() {
      return score;
    }
  }

  private volatile BestSolutionSnapshot<Solution_> bestSnapshot;
  private final Object lock = new Object();

  private final List<Consumer<BestSolutionSnapshot<Solution_>>> observers =
      new CopyOnWriteArrayList<>();

  public boolean tryUpdate(Solution_ candidate, Score<?> candidateScore) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");

    var currentSnapshot = bestSnapshot;
    if (currentSnapshot != null) {
      int comparison = compareScores(candidateScore, currentSnapshot.getScore());
      if (comparison <= 0) {
        return false;
      }
    }

    BestSolutionSnapshot<Solution_> updatedSnapshot;
    synchronized (lock) {
      currentSnapshot = bestSnapshot;
      if (currentSnapshot != null) {
        int comparison = compareScores(candidateScore, currentSnapshot.getScore());
        if (comparison <= 0) {
          return false;
        }
      }

      updatedSnapshot = new BestSolutionSnapshot<>(candidate, candidateScore);
      bestSnapshot = updatedSnapshot;
    }
    notifyObservers(updatedSnapshot);
    return true;
  }

  public Solution_ getBestSolution() {
    var snapshot = bestSnapshot;
    return snapshot == null ? null : snapshot.getSolution();
  }

  public Score<?> getBestScore() {
    var snapshot = bestSnapshot;
    return snapshot == null ? null : snapshot.getScore();
  }

  public BestSolutionSnapshot<Solution_> getBestSnapshot() {
    return bestSnapshot;
  }

  public void addObserver(Consumer<BestSolutionSnapshot<Solution_>> observer) {
    Objects.requireNonNull(observer, "Observer cannot be null");
    observers.add(observer);
  }

  public void removeObserver(Consumer<BestSolutionSnapshot<Solution_>> observer) {
    observers.remove(observer);
  }

  public List<Consumer<BestSolutionSnapshot<Solution_>>> getObservers() {
    return new ArrayList<>(observers);
  }

  private void notifyObservers(BestSolutionSnapshot<Solution_> snapshot) {
    for (Consumer<BestSolutionSnapshot<Solution_>> observer : observers) {
      try {
        observer.accept(snapshot);
      } catch (Exception e) {
        System.err.println("Observer notification failed: " + e.getMessage());
      }
    }
  }

  public void reset() {
    synchronized (lock) {
      bestSnapshot = null;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareScores(Score<?> left, Score<?> right) {
    return ((Score) left).compareTo((Score) right);
  }

  @Override
  public String toString() {
    var snapshot = bestSnapshot;
    return "SharedGlobalState{"
        + "bestScore="
        + (snapshot == null ? null : snapshot.getScore())
        + ", hasBestSolution="
        + (snapshot != null)
        + ", observerCount="
        + observers.size()
        + '}';
  }
}
