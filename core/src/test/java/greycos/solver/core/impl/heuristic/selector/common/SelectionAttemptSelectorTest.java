package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.DummyMove;
import greycos.solver.core.impl.heuristic.selector.common.iterator.AbstractOriginalChangeIterator;
import greycos.solver.core.impl.heuristic.selector.common.iterator.AbstractRandomChangeIterator;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.decorator.FilteringEntitySelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.SelectedCountLimitMoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import greycos.solver.core.impl.move.BudgetedUnionMoveIterator;
import greycos.solver.core.impl.move.UniformRandomUnionMoveIterator;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"rawtypes", "unchecked"})
class SelectionAttemptSelectorTest {

  @Test
  void legacyNestedMoveFiltersHaveOneChargingOwner() {
    var rejectedInner = new DummyMove("inner");
    var rejectedOuter = new DummyMove("outer");
    var accepted = new DummyMove("accepted");
    MoveSelector<TestdataSolution> child = mock(MoveSelector.class);
    when(child.iterator())
        .thenReturn(
            List.<Move<TestdataSolution>>of(rejectedInner, rejectedOuter, accepted).iterator());
    var inner = FilteringMoveSelector.of(child, (scoreDirector, move) -> move != rejectedInner);
    // Preserve two actual filter layers instead of the factory's normal filter composition.
    MoveSelector<TestdataSolution> bridge = mock(MoveSelector.class);
    when(bridge.iterator()).thenAnswer(invocation -> inner.iterator());
    var outer = FilteringMoveSelector.of(bridge, (scoreDirector, move) -> move != rejectedOuter);
    var ledger = new SelectionAttemptLedger(3L);
    try (var cursor = ledger.openCursor(outer::iterator)) {
      var marker = cursor.next();
      assertThat(marker.isMarker()).isTrue();
      assertThat(marker.cost()).isEqualTo(2L);
      ledger.consume(marker);
      var proposal = cursor.next();
      assertThat(proposal.selection()).isSameAs(accepted);
      ledger.consume(proposal);
      assertThat(ledger.getConsumedCount()).isEqualTo(3L);
    }
  }

  @Test
  void uniformUnionDoesNotPrefetchOtherProposalsAtCapOne() {
    assertUnionDoesNotPrefetch(false);
  }

  @Test
  void biasedUnionDoesNotPrefetchOtherProposalsAtCapOne() {
    assertUnionDoesNotPrefetch(true);
  }

  private void assertUnionDoesNotPrefetch(boolean biased) {
    var draws = new AtomicInteger();
    var random = new Random(0);
    var ledger = new SelectionAttemptLedger(1L);
    try (SelectionAttemptCursor<Move<TestdataSolution>> cursor =
        ledger.openCursor(
            () ->
                biased
                    ? new BudgetedUnionMoveIterator<>(
                        random,
                        List.of(1, 2, 3),
                        (source, rng) -> endlessMoves(draws),
                        source -> source.doubleValue())
                    : UniformRandomUnionMoveIterator.of(
                        random, List.of(1, 2, 3), (source, rng) -> endlessMoves(draws)))) {
      var attempt = cursor.next();
      assertThat(draws).hasValue(1);
      assertThat(attempt.isMarker()).isFalse();
      ledger.consume(attempt);
      assertThat(cursor.next()).isNull();
      assertThat(draws).hasValue(1);
    }
  }

  private static Iterator<Move<TestdataSolution>> endlessMoves(AtomicInteger draws) {
    return new UpcomingSelectionIterator<>() {
      @Override
      protected Move<TestdataSolution> createUpcomingSelection() {
        return new DummyMove("move " + draws.incrementAndGet());
      }
    };
  }

  @Test
  void finiteOriginalChangeExhaustionPropagatesThroughMoveFilter() {
    var entity = new Object();
    var value = new Object();
    var entities = mock(EntitySelector.class);
    when(entities.iterator())
        .thenAnswer(invocation -> SelectionAttemptContext.iterator(List.of(entity)));
    var values = mock(ValueSelector.class);
    when(values.iterator(entity))
        .thenAnswer(invocation -> SelectionAttemptContext.iterator(List.of(value)));
    MoveSelector<TestdataSolution> child = mock(MoveSelector.class);
    when(child.iterator())
        .thenAnswer(
            invocation ->
                new AbstractOriginalChangeIterator<TestdataSolution, DummyMove>(entities, values) {
                  @Override
                  protected DummyMove newChangeSelection(
                      Object selectedEntity, Object selectedValue) {
                    return new DummyMove("last");
                  }
                });
    var filtered = FilteringMoveSelector.of(child, (director, move) -> true);
    var ledger = new SelectionAttemptLedger(1L);
    try (var cursor = ledger.openCursor(filtered::iterator)) {
      var attempt = cursor.next();
      assertThat(cursor.isSourceExhausted()).isTrue();
      ledger.consume(attempt);
      assertThat(cursor.next()).isNull();
      assertThat(cursor.isBudgetStopped()).isFalse();
    }
  }

  @Test
  void finiteSelectedCountLimitProvesExhaustionAtCapOne() {
    MoveSelector<TestdataSolution> child = mock(MoveSelector.class);
    when(child.iterator())
        .thenReturn(
            List.<Move<TestdataSolution>>of(new DummyMove("first"), new DummyMove("unused"))
                .iterator());
    var selector = new SelectedCountLimitMoveSelector<>(child, 1L);
    var ledger = new SelectionAttemptLedger(1L);
    try (var cursor = ledger.openCursor(selector::iterator)) {
      var attempt = cursor.next();
      assertThat(cursor.isSourceExhausted()).isTrue();
      ledger.consume(attempt);
      assertThat(cursor.next()).isNull();
      assertThat(cursor.isBudgetStopped()).isFalse();
    }
  }

  @Test
  void neverEndingEntityFilteringStopsBeforeReturningAnyMove() {
    var draws = new AtomicInteger();
    var child = mock(EntitySelector.class);
    when(child.isNeverEnding()).thenReturn(true);
    when(child.getSize()).thenReturn(100L);
    when(child.iterator()).thenReturn(endless(draws));
    var entities = FilteringEntitySelector.of(child, (scoreDirector, entity) -> false);
    var values = mock(ValueSelector.class);
    var ledger = new SelectionAttemptLedger(4L);
    try (var cursor = ledger.openCursor(() -> changeIterator(entities, values))) {
      var marker = cursor.next();
      assertThat(marker.isMarker()).isTrue();
      assertThat(marker.cost()).isEqualTo(4L);
      assertThat(draws).hasValue(4);
      ledger.consume(marker);
      assertThat(cursor.next()).isNull();
      assertThat(cursor.isBudgetStopped()).isTrue();
    }
  }

  @Test
  void randomChangeEmptyValueRetriesConsumeBudget() {
    var draws = new AtomicInteger();
    var entities = mock(EntitySelector.class);
    when(entities.iterator()).thenReturn(endless(draws));
    var values = mock(ValueSelector.class);
    when(values.iterator(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Collections.emptyIterator());
    var ledger = new SelectionAttemptLedger(5L);
    try (var cursor = ledger.openCursor(() -> changeIterator(entities, values))) {
      var marker = cursor.next();
      assertThat(marker.cost()).isEqualTo(5L);
      assertThat(draws).hasValue(5);
      ledger.consume(marker);
      assertThat(ledger.isExhausted()).isTrue();
    }
  }

  @Test
  void pinnedListDestinationRetriesConsumeBudget() {
    var entity = new Object();
    var descriptor = mock(ListVariableDescriptor.class);
    when(descriptor.isElementPinned(null, entity, 0)).thenReturn(true);
    var draws = new AtomicInteger();
    var destinations =
        new Iterator<ElementPosition>() {
          @Override
          public boolean hasNext() {
            return true;
          }

          @Override
          public ElementPosition next() {
            draws.incrementAndGet();
            return ElementPosition.of(entity, 0);
          }
        };
    var source =
        new Iterator<ElementPosition>() {
          @Override
          public boolean hasNext() {
            return true;
          }

          @Override
          public ElementPosition next() {
            return UpcomingSelectionIterator.findUnpinnedDestination(destinations, descriptor);
          }
        };
    var ledger = new SelectionAttemptLedger(3L);
    try (var cursor = ledger.openCursor(source)) {
      var marker = cursor.next();
      assertThat(marker.isMarker()).isTrue();
      assertThat(marker.cost()).isEqualTo(3L);
      assertThat(draws).hasValue(3);
      ledger.consume(marker);
      assertThat(cursor.next()).isNull();
    }
  }

  private static Iterator<DummyMove> changeIterator(EntitySelector entities, ValueSelector values) {
    return new AbstractRandomChangeIterator<TestdataSolution, DummyMove>(entities, values) {
      @Override
      protected DummyMove newChangeSelection(Object entity, Object toValue) {
        return new DummyMove("move");
      }
    };
  }

  private static Iterator<Object> endless(AtomicInteger draws) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Object next() {
        draws.incrementAndGet();
        return new Object();
      }
    };
  }
}
