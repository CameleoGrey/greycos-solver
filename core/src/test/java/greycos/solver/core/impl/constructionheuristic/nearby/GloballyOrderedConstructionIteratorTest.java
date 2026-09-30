package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.OriginCandidates;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.SnapshotSelectionRecorder;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class GloballyOrderedConstructionIteratorTest {

  @Test
  void delayedFeedbackRoutesToIndependentOriginsBeforeTheirOptionalTails() {
    var acceptable = moves(4);
    var worsening = moves(4);
    var orders = new IdentityHashMap<Move<Object>, Integer>();
    for (var i = 0; i < 4; i++) {
      orders.put(acceptable.get(i), i * 2);
      orders.put(worsening.get(i), i * 2 + 1);
    }
    Move<Object> optional = SelectorBasedNoChangeMove.getInstance();
    orders.put(optional, 10);
    var cursor =
        new GloballyOrderedConstructionIterator<>(
            List.of(
                    origin(acceptable.iterator(), List.of(optional).iterator(), orders, null),
                    origin(worsening.iterator(), List.of(optional).iterator(), orders, null))
                .iterator(),
            1);
    var step = step();
    assertThat(cursor.next()).isSameAs(acceptable.getFirst());
    assertThat(cursor.next()).isSameAs(worsening.getFirst());
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.hasNext()).isFalse();
    // Feedback may arrive after every candidate in the merged batch has been emitted.
    record(cursor, step, worsening.getFirst(), HardSoftScore.ofHard(-1));
    record(cursor, step, acceptable.getFirst(), HardSoftScore.ZERO);
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.next()).isSameAs(worsening.get(1));
    assertThat(cursor.next()).isSameAs(optional);
    assertThat(cursor.hasNext()).isFalse();
    record(cursor, step, worsening.get(1), HardSoftScore.ofHard(-1));
    record(cursor, step, optional, HardSoftScore.ZERO);
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.next()).isSameAs(worsening.get(2));
    assertThat(cursor.next()).isSameAs(worsening.get(3));
    record(cursor, step, worsening.get(2), HardSoftScore.ofHard(-1));
    record(cursor, step, worsening.get(3), HardSoftScore.ofHard(-1));
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.next()).isSameAs(optional);
    record(cursor, step, optional, HardSoftScore.ZERO);
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(cursor.getWideningCount()).isEqualTo(2);
    cursor.close();
    assertThat(cursor.getWideningCount()).isEqualTo(2);
  }

  @Test
  void earlyCloseReleasesEveryOriginAndIsIdempotent() {
    var first = new ClosingIterator(moves(10).iterator(), null);
    var second = new ClosingIterator(moves(10).iterator(), null);
    var cursor =
        new GloballyOrderedConstructionIterator<>(
            List.of(
                    origin(first, Collections.emptyIterator(), Map.of(), null),
                    origin(second, Collections.emptyIterator(), Map.of(), null))
                .iterator(),
            1);
    assertThat(cursor.hasNext()).isTrue();
    cursor.next();
    cursor.close();
    cursor.close();
    assertThat(first.closeCalls).isEqualTo(1);
    assertThat(second.closeCalls).isEqualTo(1);
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void cleanupContinuesAfterAnErrorAndSuppressesLaterFailures() {
    var error = new AssertionError("first close");
    var first = new ClosingIterator(moves(2).iterator(), error);
    var second = new ClosingIterator(moves(2).iterator(), new AssertionError("second close"));
    var cursor =
        new GloballyOrderedConstructionIterator<>(
            List.of(
                    origin(first, Collections.emptyIterator(), Map.of(), null),
                    origin(second, Collections.emptyIterator(), Map.of(), null))
                .iterator(),
            1);
    assertThat(cursor.hasNext()).isTrue();
    assertThatThrownBy(cursor::close)
        .isSameAs(error)
        .satisfies(failure -> assertThat(failure.getSuppressed()).hasSize(1));
    assertThat(first.closeCalls).isEqualTo(1);
    assertThat(second.closeCalls).isEqualTo(1);
    assertThat(cursor.hasNext()).isFalse();
  }

  @Test
  void cleanupDoesNotSelfSuppressAThrowableSharedByTwoOrigins() {
    var error = new AssertionError("shared close failure");
    var first = new ClosingIterator(moves(2).iterator(), error);
    var second = new ClosingIterator(moves(2).iterator(), error);
    var cursor =
        new GloballyOrderedConstructionIterator<>(
            List.of(
                    origin(first, Collections.emptyIterator(), Map.of(), null),
                    origin(second, Collections.emptyIterator(), Map.of(), null))
                .iterator(),
            1);
    assertThat(cursor.hasNext()).isTrue();
    assertThatThrownBy(cursor::close).isSameAs(error);
    assertThat(error.getSuppressed()).isEmpty();
    assertThat(first.closeCalls).isEqualTo(1);
    assertThat(second.closeCalls).isEqualTo(1);
  }

  @Test
  void deferredSortedEmissionUsesTheRecordingCapturedAtCandidateCreation() {
    var generated = moves(3);
    var orders = new IdentityHashMap<Move<Object>, Integer>();
    orders.put(generated.get(0), 2);
    orders.put(generated.get(1), 0);
    orders.put(generated.get(2), 1);
    var currentRecording = new AtomicReference<Runnable>();
    var recorded = new ArrayList<Integer>();
    Iterator<Move<Object>> input =
        new Iterator<>() {
          private int index;

          @Override
          public boolean hasNext() {
            return index < generated.size();
          }

          @Override
          public Move<Object> next() {
            var selected = index++;
            currentRecording.set(() -> recorded.add(selected));
            return generated.get(selected);
          }
        };
    SnapshotSelectionRecorder<Object> recorder =
        new SnapshotSelectionRecorder<>() {
          @Override
          public void accept(Move<Object> move) {
            currentRecording.get().run();
          }

          @Override
          public Runnable snapshot(Move<Object> move) {
            return currentRecording.get();
          }
        };
    var cursor =
        new GloballyOrderedConstructionIterator<>(
            List.of(origin(input, Collections.emptyIterator(), orders, recorder)).iterator(), 3);
    assertThat(cursor.next()).isSameAs(generated.get(1));
    assertThat(cursor.next()).isSameAs(generated.get(2));
    assertThat(cursor.next()).isSameAs(generated.get(0));
    assertThat(recorded).containsExactly(1, 2, 0);
    cursor.close();
  }

  @Test
  void selectorReleasesPlacementIndexesWhenThePlacerHasNextFails() {
    var failure = new AssertionError("ranking and close failure");
    var resets = new AtomicInteger();
    var closes = new AtomicInteger();
    var source =
        new ConstructionHeuristicNearbyMoveSelector.CandidateSource<Object>() {
          @Override
          public Iterator<OriginCandidates<Object>> iterator(ScoreDirector<Object> director) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void resetPlacement() {
            resets.incrementAndGet();
          }

          @Override
          public ProgressiveConstructionIterator<Object> placementIterator(
              ScoreDirector<Object> director, int size) {
            return new ProgressiveConstructionIterator<>() {
              @Override
              public boolean hasNext() {
                throw failure;
              }

              @Override
              public Move<Object> next() {
                throw failure;
              }

              @Override
              public void recordScore(ConstructionHeuristicMoveScope<Object> moveScope) {}

              @Override
              public boolean advanceBatch() {
                return false;
              }

              @Override
              public void close() {
                closes.incrementAndGet();
                throw failure;
              }
            };
          }
        };
    var selector =
        new ConstructionHeuristicNearbyMoveSelector<>(mock(MoveSelector.class), source, 1);
    var cursor = selector.iterator();
    assertThatThrownBy(cursor::hasNext).isSameAs(failure);
    assertThat(resets.get()).isEqualTo(2);
    assertThat(closes.get()).isEqualTo(1);
    assertThat(failure.getSuppressed()).isEmpty();
  }

  private static OriginCandidates<Object> origin(
      Iterator<Move<Object>> ranked,
      Iterator<Move<Object>> tail,
      Map<Move<Object>, Integer> orders,
      java.util.function.Consumer<Move<Object>> recorder) {
    return new OriginCandidates<>(
        ranked,
        tail,
        true,
        null,
        true,
        null,
        move -> true,
        move -> List.of(orders.getOrDefault(move, 0)),
        recorder);
  }

  @SuppressWarnings("unchecked")
  private static List<Move<Object>> moves(int count) {
    var moves = new ArrayList<Move<Object>>();
    for (var i = 0; i < count; i++) {
      moves.add(mock(Move.class));
    }
    return moves;
  }

  private static ConstructionHeuristicStepScope<Object> step() {
    InnerScoreDirector<Object, HardSoftScore> director = mock(InnerScoreDirector.class);
    when(director.getScoreDefinition()).thenReturn(new HardSoftScoreDefinition());
    ConstructionHeuristicPhaseScope<Object> phase = mock(ConstructionHeuristicPhaseScope.class);
    doReturn(director).when(phase).getScoreDirector();
    var last = new ConstructionHeuristicStepScope<>(phase, -1);
    last.setScore(InnerScore.fullyAssigned(HardSoftScore.ZERO));
    when(phase.getLastCompletedStepScope()).thenReturn(last);
    return new ConstructionHeuristicStepScope<>(phase, 0);
  }

  private static void record(
      ProgressiveConstructionIterator<Object> cursor,
      ConstructionHeuristicStepScope<Object> step,
      Move<Object> move,
      HardSoftScore score) {
    var scope = new ConstructionHeuristicMoveScope<>(step, 0, move);
    scope.setScore(InnerScore.fullyAssigned(score));
    cursor.recordScore(scope);
  }

  private static final class ClosingIterator implements Iterator<Move<Object>>, AutoCloseable {
    private final Iterator<Move<Object>> delegate;
    private final Error failure;
    private int closeCalls;

    private ClosingIterator(Iterator<Move<Object>> delegate, Error failure) {
      this.delegate = delegate;
      this.failure = failure;
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Move<Object> next() {
      return delegate.next();
    }

    @Override
    public void close() {
      closeCalls++;
      if (failure != null) {
        throw failure;
      }
    }
  }
}
