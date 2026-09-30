package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

/**
 * Ranks the current construction candidates without borrowing local-search selector restrictions or
 * keeping a phase-wide distance matrix. Original-order sources rank one origin at a time; sorted
 * sources merge independently adaptive origins within each round. The construction decider remains
 * responsible for the global forager.
 */
public final class ConstructionHeuristicNearbyMoveSelector<Solution_>
    extends AbstractMoveSelector<Solution_> {

  interface CandidateSource<Solution_> {
    Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector);

    default void recordSelection(Move<Solution_> move) {}

    default void resetPlacement() {}

    default boolean isGloballyOrdered() {
      return false;
    }

    default ProgressiveConstructionIterator<Solution_> placementIterator(
        ScoreDirector<Solution_> scoreDirector, int initialSelectionSize) {
      return placementIterator(scoreDirector, initialSelectionSize, false);
    }

    default ProgressiveConstructionIterator<Solution_> placementIterator(
        ScoreDirector<Solution_> scoreDirector,
        int initialSelectionSize,
        boolean preserveCandidateOrder) {
      return placementIterator(
          scoreDirector,
          initialSelectionSize,
          preserveCandidateOrder,
          java.util.function.UnaryOperator.identity());
    }

    default ProgressiveConstructionIterator<Solution_> placementIterator(
        ScoreDirector<Solution_> scoreDirector,
        int initialSelectionSize,
        boolean preserveCandidateOrder,
        java.util.function.UnaryOperator<OriginCandidates<Solution_>> decorator) {
      var sourceOrigins = iterator(scoreDirector);
      Iterator<OriginCandidates<Solution_>> origins =
          new Iterator<>() {
            @Override
            public boolean hasNext() {
              return sourceOrigins.hasNext();
            }

            @Override
            public OriginCandidates<Solution_> next() {
              var origin = sourceOrigins.next();
              if (preserveCandidateOrder) {
                origin =
                    new OriginCandidates<>(
                        origin.rankedMoves(),
                        origin.tailMoves(),
                        origin.adaptive(),
                        origin.sourceOrder(),
                        true,
                        origin.neighborhoodSorter(),
                        origin.containsCandidate(),
                        origin.sourceRanks(),
                        origin.selectionRecorder());
              }
              return decorator.apply(origin);
            }
          };
      return iteratorForOrigins(origins, initialSelectionSize, isGloballyOrdered());
    }
  }

  interface SnapshotSelectionRecorder<Solution_>
      extends java.util.function.Consumer<Move<Solution_>> {
    Runnable snapshot(Move<Solution_> move);
  }

  record OriginCandidates<Solution_>(
      Iterator<Move<Solution_>> rankedMoves,
      Iterator<Move<Solution_>> tailMoves,
      boolean adaptive,
      Comparator<Move<Solution_>> sourceOrder,
      boolean preserveCandidateOrder,
      java.util.function.Consumer<java.util.List<Move<Solution_>>> neighborhoodSorter,
      java.util.function.Predicate<Move<Solution_>> containsCandidate,
      java.util.function.Function<Move<Solution_>, java.util.List<Integer>> sourceRanks,
      java.util.function.Consumer<Move<Solution_>> selectionRecorder) {
    OriginCandidates(
        Iterator<Move<Solution_>> rankedMoves,
        Iterator<Move<Solution_>> tailMoves,
        boolean adaptive) {
      this(rankedMoves, tailMoves, adaptive, null, false, null, move -> true, null, null);
    }

    OriginCandidates(
        Iterator<Move<Solution_>> rankedMoves,
        Iterator<Move<Solution_>> tailMoves,
        boolean adaptive,
        Comparator<Move<Solution_>> sourceOrder,
        boolean preserveCandidateOrder) {
      this(
          rankedMoves,
          tailMoves,
          adaptive,
          sourceOrder,
          preserveCandidateOrder,
          null,
          move -> true,
          null,
          null);
    }

    OriginCandidates(
        Iterator<Move<Solution_>> rankedMoves,
        Iterator<Move<Solution_>> tailMoves,
        boolean adaptive,
        Comparator<Move<Solution_>> sourceOrder,
        boolean preserveCandidateOrder,
        java.util.function.Consumer<java.util.List<Move<Solution_>>> neighborhoodSorter) {
      this(
          rankedMoves,
          tailMoves,
          adaptive,
          sourceOrder,
          preserveCandidateOrder,
          neighborhoodSorter,
          move -> true,
          null,
          null);
    }

    OriginCandidates(
        Iterator<Move<Solution_>> rankedMoves,
        Iterator<Move<Solution_>> tailMoves,
        boolean adaptive,
        Comparator<Move<Solution_>> sourceOrder,
        boolean preserveCandidateOrder,
        java.util.function.Consumer<java.util.List<Move<Solution_>>> neighborhoodSorter,
        java.util.function.Predicate<Move<Solution_>> containsCandidate) {
      this(
          rankedMoves,
          tailMoves,
          adaptive,
          sourceOrder,
          preserveCandidateOrder,
          neighborhoodSorter,
          containsCandidate,
          null,
          null);
    }
  }

  private final MoveSelector<Solution_> delegate;
  private final CandidateSource<Solution_> candidateSource;
  private final int initialSelectionSize;
  private ScoreDirector<Solution_> scoreDirector;

  ConstructionHeuristicNearbyMoveSelector(
      MoveSelector<Solution_> delegate,
      CandidateSource<Solution_> candidateSource,
      int initialSelectionSize) {
    this.delegate = delegate;
    this.candidateSource = candidateSource;
    this.initialSelectionSize = initialSelectionSize;
    phaseLifecycleSupport.addEventListener(delegate);
  }

  public MoveSelector<Solution_> getDelegate() {
    return delegate;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    candidateSource.resetPlacement();
    super.phaseStarted(phaseScope);
    scoreDirector = phaseScope.getScoreDirector();
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseEnded(phaseScope);
    } finally {
      candidateSource.resetPlacement();
      scoreDirector = null;
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      super.solvingEnded(solverScope);
    } finally {
      candidateSource.resetPlacement();
      scoreDirector = null;
    }
  }

  @Override
  public boolean isNeverEnding() {
    return false;
  }

  @Override
  public long getSize() {
    return delegate.getSize();
  }

  @Override
  public boolean supportsPhaseAndSolverCaching() {
    return false;
  }

  @Override
  public ProgressiveConstructionIterator<Solution_> iterator() {
    candidateSource.resetPlacement();
    final ProgressiveConstructionIterator<Solution_> cursor;
    try {
      cursor = candidateSource.placementIterator(scoreDirector, initialSelectionSize);
    } catch (RuntimeException | Error failure) {
      candidateSource.resetPlacement();
      throw failure;
    }
    return new ProgressiveConstructionIterator<>() {
      @Override
      public boolean hasNext() {
        try {
          return cursor.hasNext();
        } catch (RuntimeException | Error failure) {
          cleanupAfterFailure(failure);
          throw failure;
        }
      }

      @Override
      public Move<Solution_> next() {
        try {
          return cursor.next();
        } catch (RuntimeException | Error failure) {
          cleanupAfterFailure(failure);
          throw failure;
        }
      }

      @Override
      public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {
        cursor.recordScore(moveScope);
      }

      @Override
      public boolean advanceBatch() {
        try {
          return cursor.advanceBatch();
        } catch (RuntimeException | Error failure) {
          cleanupAfterFailure(failure);
          throw failure;
        }
      }

      @Override
      public long getWideningCount() {
        return cursor.getWideningCount();
      }

      private void cleanupAfterFailure(Throwable failure) {
        try {
          close();
        } catch (RuntimeException | Error cleanupFailure) {
          if (cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
      }

      @Override
      public void close() {
        try {
          cursor.close();
        } finally {
          candidateSource.resetPlacement();
        }
      }
    };
  }

  private static <Solution_> ProgressiveConstructionIterator<Solution_> iteratorForOrigins(
      Iterator<OriginCandidates<Solution_>> origins,
      int initialSelectionSize,
      boolean globallyOrdered) {
    return globallyOrdered
        ? new GloballyOrderedConstructionIterator<>(origins, initialSelectionSize)
        : new SequentialOriginIterator<>(origins, initialSelectionSize);
  }

  private static final class SequentialOriginIterator<Solution_>
      implements ProgressiveConstructionIterator<Solution_> {

    private Iterator<OriginCandidates<Solution_>> origins;
    private final int initialSelectionSize;
    private ProgressiveConstructionIterator<Solution_> current;
    private boolean started;
    private long completedWideningCount;

    private SequentialOriginIterator(
        Iterator<OriginCandidates<Solution_>> origins, int initialSelectionSize) {
      this.origins = origins;
      this.initialSelectionSize = initialSelectionSize;
    }

    @Override
    public boolean hasNext() {
      if (!started) {
        started = true;
        startNextOrigin();
      }
      return current != null && current.hasNext();
    }

    @Override
    public Move<Solution_> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return current.next();
    }

    @Override
    public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {
      if (current != null) {
        current.recordScore(moveScope);
      }
    }

    @Override
    public boolean advanceBatch() {
      if (!started) {
        return hasNext();
      }
      if (current != null && current.advanceBatch()) {
        return true;
      }
      return startNextOrigin();
    }

    private boolean startNextOrigin() {
      if (current != null) {
        completedWideningCount += current.getWideningCount();
        current.close();
        current = null;
      }
      while (origins.hasNext()) {
        var origin = origins.next();
        var rankedMoves =
            origin.preserveCandidateOrder()
                        && (origin.sourceOrder() != null || origin.sourceRanks() != null)
                    || origin.neighborhoodSorter() != null
                ? ConstructionHeuristicNearbyRanking.neighborhoodOrdered(
                    origin.rankedMoves(),
                    initialSelectionSize,
                    origin.sourceOrder(),
                    origin.neighborhoodSorter(),
                    origin.sourceRanks())
                : origin.rankedMoves();
        current =
            origin.adaptive()
                ? new AdaptiveConstructionIterator<>(
                    rankedMoves,
                    initialSelectionSize,
                    origin.tailMoves(),
                    ConstructionHeuristicNearbyRanking::isMeaningfulAssignment)
                : new UnrestrictedIterator<>(rankedMoves);
        if (current.hasNext()) {
          return true;
        }
        // A source without assigned candidates can still have an optional no-change tail.
        if (current.advanceBatch()) {
          return true;
        }
        current.close();
        completedWideningCount += current.getWideningCount();
        current = null;
      }
      return false;
    }

    @Override
    public void close() {
      if (current != null) {
        completedWideningCount += current.getWideningCount();
        current.close();
        current = null;
      }
      origins = Collections.emptyIterator();
    }

    @Override
    public long getWideningCount() {
      return completedWideningCount + (current == null ? 0 : current.getWideningCount());
    }
  }

  private static final class UnrestrictedIterator<Solution_>
      implements ProgressiveConstructionIterator<Solution_> {
    private Iterator<Move<Solution_>> moves;

    private UnrestrictedIterator(Iterator<Move<Solution_>> moves) {
      this.moves = moves;
    }

    @Override
    public boolean hasNext() {
      return moves.hasNext();
    }

    @Override
    public Move<Solution_> next() {
      return moves.next();
    }

    @Override
    public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {}

    @Override
    public boolean advanceBatch() {
      return false;
    }

    @Override
    public void close() {
      moves = Collections.emptyIterator();
    }
  }

  @Override
  public String toString() {
    return "ConstructionHeuristicNearby(" + delegate + ")";
  }
}
