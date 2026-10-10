package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.decorator.CachingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.decorator.FilteringEntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.decorator.ProbabilityEntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.CachingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.ProbabilityValueSelector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings({"rawtypes", "unchecked"})
@Timeout(10)
class SelectionAttemptDecoratorCacheTest {

  enum Kind {
    ENTITY,
    VALUE,
    PROBABILITY_ENTITY,
    PROBABILITY_VALUE
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void exactCapConstructsCompleteCacheAndSettlesSpeculation(Kind kind) {
    var draws = new AtomicInteger();
    var cache = cache(kind, () -> finite(2, draws), false);
    var ledger = new SelectionAttemptLedger(2);
    assertThat(ledger.runSetup(() -> cache.listener.constructCache(mock(SolverScope.class))))
        .isTrue();
    assertThat(cache.size.getAsLong()).isEqualTo(2);
    assertThat(draws).hasValue(2);
    assertThat(ledger.getConsumedCount()).isZero();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void capPlusOneDoesNotDrawExtraOrInstallPartialCache(Kind kind) {
    var draws = new AtomicInteger();
    var cache = cache(kind, () -> finite(3, draws), false);
    var ledger = new SelectionAttemptLedger(2);
    assertThat(ledger.runSetup(() -> cache.listener.constructCache(mock(SolverScope.class))))
        .isFalse();
    assertThat(draws).hasValue(2);
    assertThatThrownBy(cache.size::getAsLong).isInstanceOf(NullPointerException.class);
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2);
    assertThat(ledger.getConsumedCount()).isZero();
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void incorrectlyFiniteEndlessChildStopsAtQuota(Kind kind) {
    var draws = new AtomicInteger();
    var cache = cache(kind, () -> endless(draws), false);
    var ledger = new SelectionAttemptLedger(3);
    assertThat(ledger.runSetup(() -> cache.listener.constructCache(mock(SolverScope.class))))
        .isFalse();
    assertThat(draws).hasValue(3);
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(3);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void allRejectingFilterStopsWithoutInstallingCache(Kind kind) {
    var draws = new AtomicInteger();
    var cache = cache(kind, () -> endless(draws), true);
    var ledger = new SelectionAttemptLedger(3);
    assertThat(ledger.runSetup(() -> cache.listener.constructCache(mock(SolverScope.class))))
        .isFalse();
    assertThat(draws).hasValue(3);
    assertThatThrownBy(cache.size::getAsLong).isInstanceOf(NullPointerException.class);
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(3);
  }

  @ParameterizedTest
  @EnumSource(Kind.class)
  void cancellationDuringMaterializationReleasesReservations(Kind kind) {
    var draws = new AtomicInteger();
    var cache = cache(kind, () -> endless(draws), false);
    var ledger = new SelectionAttemptLedger(100, () -> draws.get() >= 3);
    assertThat(ledger.runSetup(() -> cache.listener.constructCache(mock(SolverScope.class))))
        .isFalse();
    assertThat(draws).hasValue(3);
    assertThat(ledger.wasSetupInterrupted()).isTrue();
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(3);
  }

  private record Cache(
      SelectionCacheLifecycleListener<TestdataSolution> listener, LongSupplier size) {}

  private static Cache cache(Kind kind, Supplier<Iterator<Object>> source, boolean reject) {
    if (kind == Kind.ENTITY || kind == Kind.PROBABILITY_ENTITY) {
      EntitySelector<TestdataSolution> child = mock(EntitySelector.class);
      // A size estimate must not trigger an eager multi-gigabyte allocation in bounded setup.
      when(child.getSize()).thenReturn((long) Integer.MAX_VALUE);
      when(child.iterator()).thenAnswer(invocation -> source.get());
      if (reject) child = FilteringEntitySelector.of(child, (director, selection) -> false);
      if (kind == Kind.ENTITY) {
        var selector = new CachingEntitySelector<>(child, SelectionCacheType.STEP, false);
        return new Cache(selector, selector::getSize);
      }
      var selector =
          new ProbabilityEntitySelector<>(
              child, SelectionCacheType.STEP, (director, selection) -> 1.0);
      return new Cache(selector, selector::getSize);
    }
    IterableValueSelector<TestdataSolution> child = mock(IterableValueSelector.class);
    when(child.getSize()).thenReturn((long) Integer.MAX_VALUE);
    when(child.iterator()).thenAnswer(invocation -> source.get());
    if (reject) {
      child =
          (IterableValueSelector<TestdataSolution>)
              FilteringValueSelector.of(child, (director, selection) -> false);
    }
    if (kind == Kind.VALUE) {
      var selector = new CachingValueSelector<>(child, SelectionCacheType.STEP, false);
      return new Cache(selector, selector::getSize);
    }
    var selector =
        new ProbabilityValueSelector<>(
            child, SelectionCacheType.STEP, (director, selection) -> 1.0);
    return new Cache(selector, selector::getSize);
  }

  private static Iterator<Object> finite(int size, AtomicInteger draws) {
    return KnownExhaustionIterator.withSize(
        new Iterator<>() {
          private int remaining = size;

          @Override
          public boolean hasNext() {
            return remaining > 0;
          }

          @Override
          public Object next() {
            remaining--;
            return "selection " + draws.incrementAndGet();
          }
        },
        size);
  }

  private static Iterator<Object> endless(AtomicInteger draws) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Object next() {
        return "selection " + draws.incrementAndGet();
      }
    };
  }
}
