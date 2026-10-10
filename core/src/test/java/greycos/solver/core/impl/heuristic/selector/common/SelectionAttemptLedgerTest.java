package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.impl.neighborhood.stream.FilteringIterator;

import org.junit.jupiter.api.Test;

class SelectionAttemptLedgerTest {

  @Test
  void consumeOnlyAfterBookkeepingAndReleaseSpeculation() {
    var ledger = new SelectionAttemptLedger(3L);
    try (var cursor = ledger.openCursor(List.of("first", "second", "third").iterator())) {
      var first = cursor.next();
      var second = cursor.next();
      var third = cursor.next();
      assertThat(ledger.getReservedCount()).isEqualTo(3L);
      assertThat(ledger.getConsumedCount()).isZero();
      assertThat(ledger.isExhausted()).isFalse();
      assertThat(cursor.next()).isNull();
      assertThat(cursor.isBudgetStopped()).isTrue();
      assertThat(cursor.isSourceExhausted()).isFalse();
      assertThatIllegalStateException().isThrownBy(() -> ledger.consume(second));
      ledger.consume(first);
      ledger.discard(second);
      ledger.discard(third);
    }
    assertThat(ledger.getConsumedCount()).isEqualTo(1L);
    assertThat(ledger.getDiscardedCount()).isEqualTo(2L);
    assertThat(ledger.getReservedCount()).isZero();
    try (var cursor = ledger.openCursor(List.of("replacement", "last").iterator())) {
      ledger.consume(cursor.next());
      var last = cursor.next();
      assertThat(ledger.isExhausted()).isFalse();
      ledger.consume(last);
      assertThat(ledger.isExhausted()).isTrue();
    }
  }

  @Test
  void finalCandidateConfirmsFiniteExhaustionWithoutAnotherProbe() {
    var calls = new AtomicInteger();
    var source =
        new Iterator<String>() {
          @Override
          public boolean hasNext() {
            if (calls.get() != 0) {
              throw new AssertionError(
                  "Exhaustion must not probe the source after the final proposal.");
            }
            return true;
          }

          @Override
          public String next() {
            calls.incrementAndGet();
            return "last";
          }
        };
    var ledger = new SelectionAttemptLedger(1L);
    try (var cursor = ledger.openCursor(KnownExhaustionIterator.withSize(source, 1L))) {
      var last = cursor.next();
      assertThat(cursor.isSourceExhausted()).isTrue();
      assertThat(ledger.isExhausted()).isFalse();
      ledger.consume(last);
      assertThat(cursor.next()).isNull();
      assertThat(cursor.isSourceExhausted()).isTrue();
      assertThat(cursor.isBudgetStopped()).isFalse();
      assertThat(calls).hasValue(1);
    }
  }

  @Test
  void nestedFiltersChargeEachRejectionAndReturnedProposalOnce() {
    var ledger = new SelectionAttemptLedger(3L);
    var inner =
        new FilteringIterator<>(
            List.of("inner reject", "outer reject", "accepted").iterator(),
            value -> !value.equals("inner reject"));
    var outer = new FilteringIterator<>(inner, value -> value.equals("accepted"));
    try (var cursor = ledger.openCursor(outer)) {
      var marker = cursor.next();
      assertThat(marker.isMarker()).isTrue();
      assertThat(marker.cost()).isEqualTo(2L);
      assertThat(ledger.getConsumedCount()).isZero();
      ledger.consume(marker);
      var proposal = cursor.next();
      assertThat(proposal.selection()).isEqualTo("accepted");
      assertThat(proposal.cost()).isEqualTo(1L);
      ledger.consume(proposal);
      assertThat(ledger.getConsumedCount()).isEqualTo(3L);
      assertThat(cursor.next()).isNull();
    }
  }

  @Test
  void finalFilteredMarkerCanConfirmFiniteExhaustionAtExactCap() {
    var ledger = new SelectionAttemptLedger(2L);
    var source =
        new FilteringIterator<>(
            KnownExhaustionIterator.ofList(List.of("accepted", "rejected")),
            value -> value.equals("accepted"));
    try (var cursor = ledger.openCursor(source)) {
      var proposal = cursor.next();
      assertThat(proposal.selection()).isEqualTo("accepted");
      ledger.consume(proposal);
      var marker = cursor.next();
      assertThat(marker.isMarker()).isTrue();
      assertThat(cursor.isSourceExhausted()).isTrue();
      assertThat(cursor.isBudgetStopped()).isFalse();
      ledger.consume(marker);
      assertThat(ledger.isExhausted()).isTrue();
    }
  }

  @Test
  void finiteAllFilteredSourceDistinguishesNaturalExhaustion() {
    var ledger = new SelectionAttemptLedger(5L);
    try (var cursor =
        ledger.openCursor(
            new FilteringIterator<>(List.of("a", "b").iterator(), ignored -> false))) {
      var marker = cursor.next();
      assertThat(marker.cost()).isEqualTo(2L);
      assertThat(marker.isMarker()).isTrue();
      assertThat(cursor.isSourceExhausted()).isTrue();
      assertThat(cursor.isBudgetStopped()).isFalse();
      assertThat(ledger.getReservedCount()).isEqualTo(2L);
      ledger.consume(marker);
      assertThat(cursor.next()).isNull();
    }
    assertThat(ledger.getConsumedCount()).isEqualTo(2L);
    assertThat(ledger.getDiscardedCount()).isZero();
  }

  @Test
  void capOneStopsNeverEndingFilteringBeforeSecondProposal() {
    var draws = new AtomicInteger();
    var ledger = new SelectionAttemptLedger(1L);
    try (var cursor =
        ledger.openCursor(new FilteringIterator<>(endless(draws), ignored -> false))) {
      var marker = cursor.next();
      assertThat(draws).hasValue(1);
      assertThat(marker.cost()).isEqualTo(1L);
      assertThat(cursor.isBudgetStopped()).isTrue();
      assertThat(cursor.isSourceExhausted()).isFalse();
      ledger.consume(marker);
      assertThat(cursor.next()).isNull();
      assertThat(draws).hasValue(1);
    }
  }

  @Test
  void closingDiscardsBufferedMoveAndDeliveredMarkers() {
    var ledger = new SelectionAttemptLedger(4L);
    try (var cursor =
        ledger.openCursor(
            new FilteringIterator<>(
                List.of("reject", "accept").iterator(), value -> value.equals("accept")))) {
      assertThat(cursor.next().isMarker()).isTrue();
      assertThat(ledger.getReservedCount()).isEqualTo(2L);
    }
    assertThat(ledger.getConsumedCount()).isZero();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2L);
    assertThat(ledger.hasUnreservedCapacity()).isTrue();
  }

  @Test
  void iteratorConstructionAndNeverEndingRetryAreBudgeted() {
    var draws = new AtomicInteger();
    var ledger = new SelectionAttemptLedger(7L);
    try (SelectionAttemptCursor<Integer> cursor =
        ledger.openCursor(
            () -> {
              while (true) {
                draws.incrementAndGet();
                SelectionAttemptContext.failedSelection();
              }
            })) {
      var marker = cursor.next();
      assertThat(marker.cost()).isEqualTo(7L);
      assertThat(draws).hasValue(7);
      ledger.consume(marker);
      assertThat(ledger.isExhausted()).isTrue();
    }
    // The context must not leak into ordinary unbudgeted selection.
    SelectionAttemptContext.failedSelection();
  }

  @Test
  void interruptionStopsHiddenFilteringAndReleasesUnusedReservation() {
    var draws = new AtomicInteger();
    var ledger = new SelectionAttemptLedger(1000L, () -> draws.get() == 3);
    try (var cursor =
        ledger.openCursor(new FilteringIterator<>(endless(draws), ignored -> false))) {
      var marker = cursor.next();
      assertThat(marker.cost()).isEqualTo(3L);
      assertThat(cursor.isInterrupted()).isTrue();
      assertThat(cursor.isSourceExhausted()).isFalse();
      ledger.consume(marker);
      assertThat(cursor.next()).isNull();
    }
    assertThat(draws).hasValue(3);
    assertThat(ledger.getReservedCount()).isZero();
  }

  @Test
  void noBudgetPreservesFilteringSequence() {
    var iterator = new FilteringIterator<>(List.of(1, 2, 3, 4).iterator(), value -> value % 2 == 0);
    assertThat(iterator).toIterable().containsExactly(2, 4);
  }

  private static Iterator<Integer> endless(AtomicInteger draws) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Integer next() {
        return draws.incrementAndGet();
      }
    };
  }
}
