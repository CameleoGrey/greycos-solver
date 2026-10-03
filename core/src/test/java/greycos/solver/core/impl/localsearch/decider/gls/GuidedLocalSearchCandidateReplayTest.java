package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchCandidateReplayTest {

  @Test
  void preparedResultsOnlyBecomeEligibleInTheNextRound() {
    var replay = new GuidedLocalSearchCandidateReplay<Object>();
    Move<Object> request = mock(PreparableMove.class);
    Move<Object> frozen = mock(Move.class);
    var first = replay.round(List.of(request).iterator(), move -> false);
    var selected = first.next();
    assertThat(selected.move()).isSameAs(request);
    assertThat(selected.retainResult()).isTrue();
    assertThat(selected.ordinaryOrigin()).isFalse();
    replay.retain(frozen, selected.ordinaryOrigin());
    assertThat(first).isExhausted();

    var retry = replay.round(Collections.emptyIterator(), move -> true);
    var retained = retry.next();
    assertThat(retained.move()).isSameAs(frozen);
    assertThat(retained.retainResult()).isFalse();
    assertThat(retained.ordinaryOrigin()).isFalse();
    assertThat(retry).isExhausted();
    assertThatThrownBy(retry::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void retriesInterleaveBothSourcesAndReplayEachRetainedCandidateAtMostOnce() {
    var replay = new GuidedLocalSearchCandidateReplay<Object>();
    replay.round(Collections.emptyIterator(), move -> true);
    Move<Object> a = mock(Move.class);
    Move<Object> b = mock(Move.class);
    Move<Object> freshA = mock(Move.class);
    Move<Object> freshB = mock(Move.class);
    replay.retain(a, true);
    replay.retain(b, true);

    var retry = replay.round(List.of(freshA, freshB).iterator(), move -> true);
    assertThat(retry)
        .toIterable()
        .extracting(GuidedLocalSearchCandidateReplay.Selection::move)
        .containsExactly(a, freshA, b, freshB);
    var next = replay.round(List.of(freshA, freshB).iterator(), move -> true);
    assertThat(next)
        .toIterable()
        .extracting(GuidedLocalSearchCandidateReplay.Selection::move)
        .containsExactly(freshA, a, freshB, b);
  }

  @Test
  void oneAttemptRoundsAdvanceTheRetainedCursorAndLeaveRoomForFreshCandidates() {
    var replay = new GuidedLocalSearchCandidateReplay<Object>();
    replay.round(Collections.emptyIterator(), move -> true);
    Move<Object> a = mock(Move.class);
    Move<Object> b = mock(Move.class);
    Move<Object> c = mock(Move.class);
    Move<Object> fresh = mock(Move.class);
    replay.retain(a, true);
    replay.retain(b, true);
    replay.retain(c, true);

    for (var expected : List.of(a, fresh, b, fresh, c, fresh, a)) {
      var round = replay.round(Collections.nCopies(10, fresh).iterator(), move -> true);
      assertThat(round.next().move()).isSameAs(expected);
    }
  }

  @Test
  void aRetainedFirstRoundDoesNotConsumeFreshRequestsSpeculatively() {
    var replay = new GuidedLocalSearchCandidateReplay<Object>();
    replay.round(Collections.emptyIterator(), move -> true);
    // Earlier rounds with no prepared result must not change which source gets the first retry.
    replay.round(Collections.emptyIterator(), move -> true);
    Move<Object> frozen = mock(Move.class);
    replay.retain(frozen, true);
    Iterator<Move<Object>> fresh = mock(Iterator.class);

    var retry = replay.round(fresh, move -> true);
    assertThat(retry.hasNext()).isTrue();
    assertThat(retry.next().move()).isSameAs(frozen);
    verifyNoInteractions(fresh);
  }
}
