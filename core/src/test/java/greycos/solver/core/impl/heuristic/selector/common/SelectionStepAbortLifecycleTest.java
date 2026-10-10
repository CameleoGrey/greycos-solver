package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.event.PhaseLifecycleSupport;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings({"rawtypes", "unchecked"})
class SelectionStepAbortLifecycleTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void abortedAndCommittedStepsDisposeExactlyOnceAndAllowNextStep(boolean abort) {
    var scopes = scopes();
    var cache = mock(SelectionCacheLifecycleListener.class);
    var bridge = new SelectionCacheLifecycleBridge<>(SelectionCacheType.STEP, cache);
    bridge.solvingStarted(scopes.solver);
    bridge.phaseStarted(scopes.phase);
    for (int i = 0; i < 2; i++) {
      bridge.stepStarted(scopes.step);
      if (abort) bridge.stepAborted(scopes.step);
      else bridge.stepEnded(scopes.step);
      // A fallback cleanup must not dispose an already released cache.
      bridge.stepAborted(scopes.step);
    }
    bridge.phaseEnded(scopes.phase);
    bridge.solvingEnded(scopes.solver);
    verify(cache, times(2)).constructCache(scopes.solver);
    verify(cache, times(2)).disposeCache(scopes.solver);
  }

  @Test
  void partialStepStartAbortsEnteredListenersInRegistrationOrderAndAggregatesFailures() {
    var scopes = scopes();
    var events = new ArrayList<String>();
    var startFailure = new IllegalArgumentException("step startup");
    var firstCleanup = new IllegalStateException("first cleanup");
    var secondCleanup = new IllegalStateException("second cleanup");
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(listener("first", events, null, firstCleanup));
    support.addEventListener(listener("second", events, startFailure, secondCleanup));
    var unstarted = mock(PhaseLifecycleListener.class);
    support.addEventListener(unstarted);
    var ledger = new SelectionAttemptLedger(4);
    assertThatThrownBy(() -> ledger.runSetup(() -> support.fireStepStarted(scopes.step)))
        .isSameAs(startFailure);
    assertThatThrownBy(() -> support.fireStepAborted(scopes.step))
        .isSameAs(firstCleanup)
        .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(secondCleanup));
    assertThat(events)
        .containsExactly("first start", "second start", "first abort", "second abort");
    verify(unstarted, never()).stepStarted(any());
    verify(unstarted, never()).stepAborted(any());
    verify(unstarted, never()).stepEnded(any());
    assertThatCode(() -> support.fireStepAborted(scopes.step)).doesNotThrowAnyException();
    assertThat(ledger.getReservedCount()).isZero();
  }

  @Test
  void cancellationBetweenStepCallbacksAbortsOnlyEnteredListener() {
    var scopes = scopes();
    var cancelled = new AtomicBoolean();
    var entered = mock(PhaseLifecycleListener.class);
    var unstarted = mock(PhaseLifecycleListener.class);
    doAnswer(
            invocation -> {
              cancelled.set(true);
              return null;
            })
        .when(entered)
        .stepStarted(any());
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(entered);
    support.addEventListener(unstarted);
    var ledger = new SelectionAttemptLedger(4, cancelled::get);
    assertThat(ledger.runSetup(() -> support.fireStepStarted(scopes.step))).isFalse();
    support.fireStepAborted(scopes.step);
    verify(entered).stepAborted(scopes.step);
    verify(entered, never()).stepEnded(any());
    verify(unstarted, never()).stepAborted(any());
    assertThat(ledger.getReservedCount()).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionCacheType.class,
      names = {"STEP", "PHASE", "SOLVER"})
  void budgetStopPreservesDisposalFailureSettlesReservationsAndAllowsFreshSetup(
      SelectionCacheType type) {
    var scopes = scopes();
    var failOnce = new AtomicBoolean(true);
    var disposalFailure = new IllegalStateException("cache disposal");
    var cache = mock(SelectionCacheLifecycleListener.class);
    doAnswer(
            invocation -> {
              if (failOnce.get()) {
                while (true) {
                  SelectionAttemptContext.beforeSelection();
                  SelectionAttemptContext.recordSetupProposal();
                }
              }
              return null;
            })
        .when(cache)
        .constructCache(scopes.solver);
    doAnswer(
            invocation -> {
              if (failOnce.getAndSet(false)) throw disposalFailure;
              return null;
            })
        .when(cache)
        .disposeCache(scopes.solver);
    var bridge = new SelectionCacheLifecycleBridge<>(type, cache);
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(bridge);
    var ledger = new SelectionAttemptLedger(2);
    Runnable startup =
        () -> {
          support.fireSolvingStarted(scopes.solver);
          support.firePhaseStarted(scopes.phase);
          support.fireStepStarted(scopes.step);
        };
    assertThatThrownBy(() -> ledger.runSetup(startup)).isSameAs(disposalFailure);
    assertThat(ledger.getReservedCount()).isZero();
    assertThat(ledger.getDiscardedCount()).isEqualTo(2);
    assertThat(ledger.getConsumedCount()).isZero();
    support.fireStepAborted(scopes.step);
    if (type != SelectionCacheType.SOLVER) support.firePhaseEnded(scopes.phase);
    support.fireSolvingEnded(scopes.solver);
    verify(cache).disposeCache(scopes.solver);

    assertThat(ledger.runSetup(startup)).isTrue();
    support.fireStepAborted(scopes.step);
    support.firePhaseEnded(scopes.phase);
    support.fireSolvingEnded(scopes.solver);
    verify(cache, times(2)).disposeCache(scopes.solver);
    assertThat(SelectionAttemptContext.isActive()).isFalse();
  }

  @Test
  void throwingStepDisposalStillClearsBridgeAndDisposesFollowingCache() {
    var scopes = scopes();
    var firstCache = mock(SelectionCacheLifecycleListener.class);
    var secondCache = mock(SelectionCacheLifecycleListener.class);
    var failure = new IllegalStateException("dispose once");
    doThrow(failure).doNothing().when(firstCache).disposeCache(scopes.solver);
    var support = new PhaseLifecycleSupport<TestdataSolution>();
    support.addEventListener(
        new SelectionCacheLifecycleBridge<>(SelectionCacheType.STEP, firstCache));
    support.addEventListener(
        new SelectionCacheLifecycleBridge<>(SelectionCacheType.STEP, secondCache));
    var ledger = new SelectionAttemptLedger(4);
    assertThat(ledger.runSetup(() -> support.fireStepStarted(scopes.step))).isTrue();
    assertThatThrownBy(() -> support.fireStepAborted(scopes.step)).isSameAs(failure);
    verify(secondCache).disposeCache(scopes.solver);
    assertThat(ledger.runSetup(() -> support.fireStepStarted(scopes.step))).isTrue();
    support.fireStepAborted(scopes.step);
    support.firePhaseEnded(scopes.phase);
    verify(firstCache, times(2)).disposeCache(scopes.solver);
    verify(secondCache, times(2)).disposeCache(scopes.solver);
  }

  private static PhaseLifecycleListener<TestdataSolution> listener(
      String name,
      List<String> events,
      RuntimeException startFailure,
      RuntimeException cleanupFailure) {
    return new PhaseLifecycleListenerAdapter<>() {
      @Override
      public void stepStarted(AbstractStepScope<TestdataSolution> step) {
        events.add(name + " start");
        if (startFailure != null) throw startFailure;
      }

      @Override
      public void stepAborted(AbstractStepScope<TestdataSolution> step) {
        events.add(name + " abort");
        if (cleanupFailure != null) throw cleanupFailure;
      }
    };
  }

  private record Scopes(
      SolverScope<TestdataSolution> solver,
      AbstractPhaseScope<TestdataSolution> phase,
      AbstractStepScope<TestdataSolution> step) {}

  private static Scopes scopes() {
    var solver = mock(SolverScope.class);
    var phase = mock(AbstractPhaseScope.class);
    var step = mock(AbstractStepScope.class);
    when(solver.getScoreDirector()).thenReturn(mock(InnerScoreDirector.class));
    when(phase.getSolverScope()).thenReturn(solver);
    when(step.getPhaseScope()).thenReturn(phase);
    return new Scopes(solver, phase, step);
  }
}
