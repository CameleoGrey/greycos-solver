package greycos.solver.core.impl.islandmodel;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.scope.SolverScope;

/** Bounded publication mailbox. Only the enclosing solve thread drains it and calls listeners. */
public final class GlobalBestPropagator<Solution_>
    implements Consumer<SharedGlobalState.BestSolutionSnapshot<Solution_>> {

  private final SharedGlobalState<Solution_> globalState;
  private final SolverScope<Solution_> mainSolverScope;
  private final SolverEventSupport<Solution_> solverEventSupport;
  private final EventProducerId eventProducerId;
  private final int capacity;
  private final ArrayDeque<Runnable> publications = new ArrayDeque<>();
  private final IslandCoordinatorSignal signal = new IslandCoordinatorSignal();
  private boolean closed;
  private InnerScore<?> lastKnownBestScore;

  public GlobalBestPropagator(
      SharedGlobalState<Solution_> globalState,
      SolverScope<Solution_> mainSolverScope,
      SolverEventSupport<Solution_> solverEventSupport,
      EventProducerId eventProducerId) {
    this(globalState, mainSolverScope, solverEventSupport, eventProducerId, 1);
  }

  public GlobalBestPropagator(
      SharedGlobalState<Solution_> globalState,
      SolverScope<Solution_> mainSolverScope,
      SolverEventSupport<Solution_> solverEventSupport,
      EventProducerId eventProducerId,
      int capacity) {
    this.globalState = Objects.requireNonNull(globalState);
    this.mainSolverScope = Objects.requireNonNull(mainSolverScope);
    this.solverEventSupport = Objects.requireNonNull(solverEventSupport);
    this.eventProducerId = Objects.requireNonNull(eventProducerId);
    if (capacity < 1) {
      throw new IllegalArgumentException(
          "Publication capacity (%d) must be positive.".formatted(capacity));
    }
    this.capacity = capacity;
    lastKnownBestScore = mainSolverScope.getBestScore();
  }

  public void start() {
    globalState.setPublicationObserver(this);
  }

  IslandCoordinatorSignal getSignal() {
    return signal;
  }

  public void stop() {
    // Release publishers before acquiring the shared-state lock: one may hold it while enqueuing.
    synchronized (publications) {
      closed = true;
      publications.clear();
      publications.notifyAll();
    }
    signal.signal();
    globalState.setPublicationObserver(null);
  }

  @Override
  public void accept(SharedGlobalState.BestSolutionSnapshot<Solution_> snapshot) {
    Objects.requireNonNull(snapshot);
    enqueue(() -> propagate(snapshot));
  }

  public void enqueue(Runnable publication) {
    Objects.requireNonNull(publication);
    boolean enqueued = false;
    synchronized (publications) {
      while (!closed && publications.size() == capacity) {
        try {
          publications.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while publishing island progress.", e);
        }
      }
      if (!closed) {
        publications.addLast(publication);
        enqueued = true;
      }
    }
    if (enqueued) {
      signal.signal();
    }
  }

  /** Drain one bounded batch so continuous publications cannot starve failure detection. */
  public int drain() {
    for (int i = 0; i < capacity; i++) {
      Runnable publication;
      synchronized (publications) {
        publication = publications.pollFirst();
        publications.notifyAll();
      }
      if (publication == null) {
        return i;
      }
      publication.run();
    }
    return capacity;
  }

  private void propagate(SharedGlobalState.BestSolutionSnapshot<Solution_> snapshot) {
    var score = snapshot.getInnerScore();
    if (lastKnownBestScore != null && compareScores(score, lastKnownBestScore) <= 0) {
      return;
    }
    var clonedSolution = mainSolverScope.getScoreDirector().cloneSolution(snapshot.getSolution());
    if (score.isFullyAssigned() && !mainSolverScope.isBestSolutionInitialized()) {
      mainSolverScope.setStartingInitializedScore(score.raw());
    }
    mainSolverScope.setBestSolution(clonedSolution);
    mainSolverScope.setBestScore(score);
    mainSolverScope.setBestAcceptorMigrationState(snapshot.getAcceptorState());
    mainSolverScope.setBestSolutionTimeMillis(snapshot.getTimestampMillis());
    lastKnownBestScore = score;
    if (score.isFullyAssigned()) {
      solverEventSupport.fireBestSolutionChanged(mainSolverScope, eventProducerId, clonedSolution);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compareScores(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }
}
