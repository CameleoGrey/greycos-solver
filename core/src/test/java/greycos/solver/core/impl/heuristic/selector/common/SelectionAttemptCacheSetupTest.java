package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.move.DummyMove;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.CachingMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.event.PhaseLifecycleSupport;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"rawtypes", "unchecked"})
class SelectionAttemptCacheSetupTest {

  @Test
  void exactFiniteCacheFillIsSpeculativeAndSelectionIsChargedOnce() {
    var moves = List.<Move<TestdataSolution>>of(new DummyMove("a"), new DummyMove("b"));
    var cache = cache(moves, new AtomicInteger());
    var ledger = new SelectionAttemptLedger(2L);
    assertThat(ledger.runSetup(() -> cache.constructCache(mock(SolverScope.class)))).isTrue();
    assertThat(cache.getSize()).isEqualTo(2L);
    assertThat(ledger.getConsumedCount()).isZero();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2L);
    try (var cursor = ledger.openCursor(cache::iterator)) {
      ledger.consume(cursor.next());
      ledger.consume(cursor.next());
      assertThat(cursor.isSourceExhausted()).isTrue();
    }
    assertThat(ledger.getConsumedCount()).isEqualTo(2L);
  }

  @Test
  void oversizedCacheDoesNotGenerateBeyondSetupQuotaOrInstallPartialContents() {
    var draws = new AtomicInteger();
    var cache = cache(List.of(new DummyMove("a"), new DummyMove("b"), new DummyMove("c")), draws);
    var ledger = new SelectionAttemptLedger(2L);
    assertThat(ledger.runSetup(() -> cache.constructCache(mock(SolverScope.class)))).isFalse();
    assertThat(draws).hasValue(2);
    assertThat(ledger.wasSetupInterrupted()).isFalse();
    assertThat(ledger.getConsumedCount()).isZero();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2L);
    assertThatCode(() -> cache.disposeCache(mock(SolverScope.class))).doesNotThrowAnyException();
  }

  @Test
  void cachedFilteringHonorsLiveCancellationBeforeReturningAnyCandidate() {
    var draws = new AtomicInteger();
    MoveSelector<TestdataSolution> child = mock(MoveSelector.class);
    when(child.getSize()).thenReturn(10_000L);
    when(child.iterator()).thenAnswer(invocation -> endless(draws));
    var filtered = FilteringMoveSelector.of(child, (director, move) -> false);
    var cache = new CachingMoveSelector<>(filtered, SelectionCacheType.PHASE, false);
    var ledger = new SelectionAttemptLedger(1_000L, () -> draws.get() >= 3);
    assertThat(ledger.runSetup(() -> cache.constructCache(mock(SolverScope.class)))).isFalse();
    assertThat(draws).hasValue(3);
    assertThat(ledger.wasSetupInterrupted()).isTrue();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(3L);
  }

  @Test
  void partialPhaseStartupDisposesAttemptedCacheAndSkipsUnstartedListeners() {
    var solverScope = mock(SolverScope.class);
    var phaseScope = mock(AbstractPhaseScope.class);
    when(phaseScope.getSolverScope()).thenReturn(solverScope);
    var cache = cache(List.of(new DummyMove("a"), new DummyMove("b")), new AtomicInteger());
    var cacheBridge = new SelectionCacheLifecycleBridge<>(SelectionCacheType.PHASE, cache);
    var unstarted = mock(PhaseLifecycleListener.class);
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(cacheBridge);
    support.addEventListener(unstarted);
    var ledger = new SelectionAttemptLedger(1L);
    assertThat(ledger.runSetup(() -> support.firePhaseStarted(phaseScope))).isFalse();
    assertThatCode(() -> support.firePhaseEnded(phaseScope)).doesNotThrowAnyException();
    assertThatCode(() -> support.fireSolvingEnded(solverScope)).doesNotThrowAnyException();
    verify(unstarted, never()).phaseStarted(any());
    verify(unstarted, never()).phaseEnded(any());
  }

  @Test
  void partialSolverStartupDisposesAttemptedCacheAndSkipsUnstartedListeners() {
    var solverScope = mock(SolverScope.class);
    var cache = cache(List.of(new DummyMove("a"), new DummyMove("b")), new AtomicInteger());
    var cacheBridge = new SelectionCacheLifecycleBridge<>(SelectionCacheType.SOLVER, cache);
    var unstarted = mock(PhaseLifecycleListener.class);
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(cacheBridge);
    support.addEventListener(unstarted);
    var ledger = new SelectionAttemptLedger(1L);
    assertThat(ledger.runSetup(() -> support.fireSolvingStarted(solverScope))).isFalse();
    assertThatCode(() -> support.fireSolvingEnded(solverScope)).doesNotThrowAnyException();
    verify(unstarted, never()).solvingStarted(any());
    verify(unstarted, never()).solvingEnded(any());
  }

  @Test
  void cancellationBetweenCallbacksReleasesProvisionalReservation() {
    var cancelled = new AtomicBoolean();
    var entered = mock(PhaseLifecycleListener.class);
    var unstarted = mock(PhaseLifecycleListener.class);
    doAnswer(
            invocation -> {
              cancelled.set(true);
              return null;
            })
        .when(entered)
        .phaseStarted(any());
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(entered);
    support.addEventListener(unstarted);
    var ledger = new SelectionAttemptLedger(2L, cancelled::get);
    assertThat(ledger.runSetup(() -> support.firePhaseStarted(mock(AbstractPhaseScope.class))))
        .isFalse();
    assertThat(ledger.wasSetupInterrupted()).isTrue();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isZero();
    verify(unstarted, never()).phaseStarted(any());
  }

  @Test
  void setupRetriesRecoverWithFreshCompleteLifecycle() {
    var scope = mock(SolverScope.class);
    when(scope.getScoreDirector()).thenReturn(mock(InnerScoreDirector.class));
    var failedOnce = new AtomicInteger();
    var listener = mock(SelectionCacheLifecycleListener.class);
    doAnswer(
            invocation -> {
              if (failedOnce.getAndIncrement() == 0) {
                while (true) SelectionAttemptContext.failedSelection();
              }
              return null;
            })
        .when(listener)
        .constructCache(scope);
    var bridge = new SelectionCacheLifecycleBridge<>(SelectionCacheType.SOLVER, listener);
    var ledger = new SelectionAttemptLedger(2L);
    assertThat(ledger.runSetup(() -> bridge.solvingStarted(scope))).isFalse();
    assertThatCode(() -> bridge.solvingEnded(scope)).doesNotThrowAnyException();
    assertThat(ledger.runSetup(() -> bridge.solvingStarted(scope))).isTrue();
    assertThatCode(() -> bridge.solvingEnded(scope)).doesNotThrowAnyException();
    // No setup context leaked into unrelated callbacks.
    SelectionAttemptContext.failedSelection();
  }

  private static CachingMoveSelector<TestdataSolution> cache(
      List<? extends Move<TestdataSolution>> moves, AtomicInteger draws) {
    MoveSelector<TestdataSolution> child = mock(MoveSelector.class);
    when(child.getSize()).thenReturn((long) moves.size());
    when(child.iterator())
        .thenAnswer(
            invocation ->
                KnownExhaustionIterator.withSize(
                    new Iterator<Move<TestdataSolution>>() {
                      private final Iterator<? extends Move<TestdataSolution>> delegate =
                          moves.iterator();

                      @Override
                      public boolean hasNext() {
                        return delegate.hasNext();
                      }

                      @Override
                      public Move<TestdataSolution> next() {
                        draws.incrementAndGet();
                        return delegate.next();
                      }
                    },
                    moves.size()));
    return new CachingMoveSelector<>(child, SelectionCacheType.PHASE, false);
  }

  private static Iterator<Move<TestdataSolution>> endless(AtomicInteger draws) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Move<TestdataSolution> next() {
        return new DummyMove("move " + draws.incrementAndGet());
      }
    };
  }
}
