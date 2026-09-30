package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.OriginCandidates;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.SnapshotSelectionRecorder;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.preview.api.move.Move;

/**
 * Merges the currently admitted nearby batches in configured global order. Each origin receives its
 * own score feedback and decides independently whether to widen after the complete merged batch.
 * Sorting caches supply authoritative ordinals; custom sorters are never rerun on subsets.
 */
final class GloballyOrderedConstructionIterator<Solution_>
    implements ProgressiveConstructionIterator<Solution_> {

  private Iterator<OriginCandidates<Solution_>> origins;
  private final int initialSelectionSize;
  private final List<OriginState<Solution_>> states = new ArrayList<>();
  private final Map<Move<Solution_>, ArrayDeque<ProgressiveConstructionIterator<Solution_>>>
      owners = new IdentityHashMap<>();
  private Iterator<Entry<Solution_>> current = Collections.emptyIterator();
  private boolean initialized;
  private boolean closed;
  private long completedWideningCount;

  GloballyOrderedConstructionIterator(
      Iterator<OriginCandidates<Solution_>> origins, int initialSelectionSize) {
    this.origins = origins;
    this.initialSelectionSize = initialSelectionSize;
  }

  @Override
  public boolean hasNext() {
    if (closed) {
      return false;
    }
    if (!initialized) {
      initialized = true;
      var entries = new ArrayList<Entry<Solution_>>();
      try {
        while (origins.hasNext()) {
          var origin = origins.next();
          var cursor =
              origin.adaptive()
                  ? new AdaptiveConstructionIterator<>(
                      origin.rankedMoves(),
                      initialSelectionSize,
                      origin.tailMoves(),
                      ConstructionHeuristicNearbyRanking::isMeaningfulAssignment)
                  : new UnrestrictedIterator<>(origin.rankedMoves());
          var state = new OriginState<>(origin, cursor, states.size());
          states.add(state);
          drain(state, entries);
        }
        origins = Collections.emptyIterator();
        open(entries);
      } catch (RuntimeException | Error failure) {
        try {
          close();
        } catch (RuntimeException | Error cleanupFailure) {
          if (cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
        throw failure;
      }
    }
    return current.hasNext();
  }

  private void drain(OriginState<Solution_> state, List<Entry<Solution_>> entries) {
    while (state.cursor.hasNext()) {
      var move = state.cursor.next();
      var ranks =
          state.origin.sourceRanks() == null
              ? List.of(state.ordinal, state.emittedOrdinal++)
              : List.copyOf(state.origin.sourceRanks().apply(move));
      var recorder = state.origin.selectionRecorder();
      Runnable recording;
      if (recorder instanceof SnapshotSelectionRecorder<Solution_> snapshot) {
        recording = snapshot.snapshot(move);
      } else {
        recording = recorder == null ? () -> {} : () -> recorder.accept(move);
      }
      entries.add(new Entry<>(move, state.cursor, ranks, recording));
    }
  }

  private void open(List<Entry<Solution_>> entries) {
    entries.sort((left, right) -> compareRanks(left.ranks(), right.ranks()));
    current = entries.iterator();
  }

  private static int compareRanks(List<Integer> left, List<Integer> right) {
    for (var i = 0; i < Math.min(left.size(), right.size()); i++) {
      var comparison = Integer.compare(left.get(i), right.get(i));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

  @Override
  public Move<Solution_> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    var entry = current.next();
    entry.recording().run();
    owners.computeIfAbsent(entry.move(), ignored -> new ArrayDeque<>()).add(entry.owner());
    return entry.move();
  }

  @Override
  public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    if (closed) {
      return;
    }
    var moveOwners = owners.get(moveScope.getMove());
    if (moveOwners == null || moveOwners.isEmpty()) {
      throw new IllegalStateException(
          "The construction score refers to a move (%s) outside the current sorted batch."
              .formatted(moveScope.getMove()));
    }
    moveOwners.removeFirst().recordScore(moveScope);
    if (moveOwners.isEmpty()) {
      owners.remove(moveScope.getMove());
    }
  }

  @Override
  public boolean advanceBatch() {
    if (closed) {
      return false;
    }
    if (hasNext()) {
      throw new IllegalStateException("The current construction batch still has unconsumed moves.");
    }
    // Non-doable moves receive no feedback. The decider has drained all submitted scores before
    // opening a new batch, so any remaining owner entries belong to those skipped moves.
    owners.clear();
    var entries = new ArrayList<Entry<Solution_>>();
    var iterator = states.iterator();
    while (iterator.hasNext()) {
      var state = iterator.next();
      if (state.cursor.advanceBatch()) {
        drain(state, entries);
      } else {
        completedWideningCount += state.cursor.getWideningCount();
        state.cursor.close();
        iterator.remove();
      }
    }
    open(entries);
    return current.hasNext();
  }

  @Override
  public long getWideningCount() {
    return completedWideningCount
        + states.stream().mapToLong(state -> state.cursor.getWideningCount()).sum();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    Throwable failure = null;
    for (var state : states) {
      completedWideningCount += state.cursor.getWideningCount();
      try {
        state.cursor.close();
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) {
          failure = cleanupFailure;
        } else {
          if (cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
          }
        }
      }
    }
    states.clear();
    owners.clear();
    origins = Collections.emptyIterator();
    current = Collections.emptyIterator();
    if (failure instanceof RuntimeException runtimeException) {
      throw runtimeException;
    } else if (failure instanceof Error error) {
      throw error;
    }
  }

  private record Entry<Solution_>(
      Move<Solution_> move,
      ProgressiveConstructionIterator<Solution_> owner,
      List<Integer> ranks,
      Runnable recording) {}

  private static final class OriginState<Solution_> {
    private final OriginCandidates<Solution_> origin;
    private final ProgressiveConstructionIterator<Solution_> cursor;
    private final int ordinal;
    private int emittedOrdinal;

    private OriginState(
        OriginCandidates<Solution_> origin,
        ProgressiveConstructionIterator<Solution_> cursor,
        int ordinal) {
      this.origin = origin;
      this.cursor = cursor;
      this.ordinal = ordinal;
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
}
