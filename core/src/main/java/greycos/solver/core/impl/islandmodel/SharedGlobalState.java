package greycos.solver.core.impl.islandmodel;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.score.director.InnerScore;

/**
 * Thread-safe shared state for island model, tracking global best solution across all islands. Uses
 * double-checked locking with volatile for thread-safe updates with minimal contention.
 */
public class SharedGlobalState<Solution_> {

  public static final class BestSolutionSnapshot<Solution_> {
    private final Solution_ solution;
    private final InnerScore<?> score;
    private final long timestampMillis;
    private final long version;
    private final AcceptorMigrationState acceptorState;

    private BestSolutionSnapshot(
        Solution_ solution,
        InnerScore<?> score,
        long timestampMillis,
        long version,
        AcceptorMigrationState acceptorState) {
      this.solution = Objects.requireNonNull(solution, "Best solution cannot be null");
      this.score = Objects.requireNonNull(score, "Best score cannot be null");
      this.timestampMillis = timestampMillis;
      this.version = version;
      this.acceptorState = Objects.requireNonNull(acceptorState);
    }

    public AcceptorMigrationState getAcceptorState() {
      return acceptorState;
    }

    public Solution_ getSolution() {
      return solution;
    }

    public Score<?> getScore() {
      return score.raw();
    }

    public InnerScore<?> getInnerScore() {
      return score;
    }

    public long getTimestampMillis() {
      return timestampMillis;
    }

    public long getVersion() {
      return version;
    }
  }

  private volatile BestSolutionSnapshot<Solution_> bestSnapshot;
  private volatile boolean closed;
  private final Object lock = new Object();
  private Clock clock = Clock.systemUTC();
  private Consumer<BestSolutionSnapshot<Solution_>> progressObserver;
  private Consumer<BestSolutionSnapshot<Solution_>> publicationObserver;
  private SharedGlobalState<Solution_> enclosingGlobalState;

  private final List<Consumer<BestSolutionSnapshot<Solution_>>> observers =
      new CopyOnWriteArrayList<>();

  public boolean tryUpdate(Solution_ candidate, InnerScore<?> candidateScore) {
    return tryUpdate(candidate, candidateScore, AcceptorMigrationState.Empty.INSTANCE);
  }

  public boolean tryUpdate(
      Solution_ candidate, InnerScore<?> candidateScore, AcceptorMigrationState acceptorState) {
    Objects.requireNonNull(candidate, "Candidate solution cannot be null");
    Objects.requireNonNull(candidateScore, "Candidate score cannot be null");
    Objects.requireNonNull(acceptorState);
    if (closed || candidateScore.isStructurallyFlawed()) {
      return false;
    }

    var currentSnapshot = bestSnapshot;
    if (currentSnapshot != null) {
      int comparison = compareScores(candidateScore, currentSnapshot.getInnerScore());
      if (comparison <= 0) {
        return false;
      }
    }

    BestSolutionSnapshot<Solution_> updatedSnapshot;
    synchronized (lock) {
      if (closed) {
        return false;
      }
      currentSnapshot = bestSnapshot;
      if (currentSnapshot != null) {
        int comparison = compareScores(candidateScore, currentSnapshot.getInnerScore());
        if (comparison <= 0) {
          return false;
        }
      }

      updatedSnapshot =
          new BestSolutionSnapshot<>(
              candidate,
              candidateScore,
              clock.millis(),
              currentSnapshot == null ? 1L : currentSnapshot.getVersion() + 1L,
              acceptorState);
      // Internal termination history must observe every improvement in publication order.
      // External observers remain outside this lock and may arrive out of order.
      if (progressObserver != null) {
        progressObserver.accept(updatedSnapshot);
      }
      bestSnapshot = updatedSnapshot;
      // Nested islands solve the same problem. Forward the immutable accepted best directly;
      // reading or modifying the enclosing solver scope would cross thread ownership boundaries.
      // Always acquire state locks from child to parent, before local mailbox backpressure.
      if (enclosingGlobalState != null) {
        enclosingGlobalState.tryUpdate(candidate, candidateScore, acceptorState);
      }
      // Queue accepted publications in the same order as the shared termination history.
      if (publicationObserver != null) {
        publicationObserver.accept(updatedSnapshot);
      }
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

  public InnerScore<?> getBestInnerScore() {
    var snapshot = bestSnapshot;
    return snapshot == null ? null : snapshot.getInnerScore();
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
    reset(Clock.systemUTC(), null);
  }

  void reset(Clock clock, Consumer<BestSolutionSnapshot<Solution_>> progressObserver) {
    reset(clock, progressObserver, null);
  }

  void reset(
      Clock clock,
      Consumer<BestSolutionSnapshot<Solution_>> progressObserver,
      SharedGlobalState<Solution_> enclosingGlobalState) {
    if (enclosingGlobalState == this) {
      throw new IllegalArgumentException("An island state cannot enclose itself.");
    }
    synchronized (lock) {
      this.clock = Objects.requireNonNull(clock);
      this.progressObserver = progressObserver;
      this.enclosingGlobalState = enclosingGlobalState;
      bestSnapshot = null;
      closed = false;
    }
  }

  void setPublicationObserver(Consumer<BestSolutionSnapshot<Solution_>> observer) {
    synchronized (lock) {
      publicationObserver = observer;
    }
  }

  void close() {
    closed = true;
    synchronized (lock) {
      progressObserver = null;
      publicationObserver = null;
      enclosingGlobalState = null;
    }
  }

  void clearProgressObserver() {
    synchronized (lock) {
      progressObserver = null;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareScores(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
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
