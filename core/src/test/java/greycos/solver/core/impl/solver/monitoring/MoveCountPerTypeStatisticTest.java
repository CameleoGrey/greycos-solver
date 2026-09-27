package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.statistic.MoveCountPerTypeStatistic;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MoveCountPerTypeStatisticTest extends AbstractMeterTest {

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void unregisterRemovesEachRecordedScopeAndPreservesOtherSolvers() {
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        DefaultSolver<TestdataSolution> solver = mock(DefaultSolver.class);
        var rootTags = SolverTags.withProblemId("owned").asTags();
        SolverScope<TestdataSolution> rootScope = scope(rootTags);
        when(solver.getSolverScope()).thenReturn(rootScope);
        var statistic = new MoveCountPerTypeStatistic<TestdataSolution>();
        statistic.register(solver);
        ArgumentCaptor<PhaseLifecycleListener<TestdataSolution>> listenerCaptor =
            ArgumentCaptor.forClass(PhaseLifecycleListener.class);
        verify(solver).addPhaseLifecycleListener(listenerCaptor.capture());
        listenerCaptor.getValue().phaseEnded(new LocalSearchPhaseScope<>(rootScope, 0));
        listenerCaptor
            .getValue()
            .phaseEnded(new LocalSearchPhaseScope<>(scope(rootTags.and("island.id", "0")), 0));
        var foreignTags = SolverTags.withProblemId("other").asTags();
        var meterName = SolverMetric.MOVE_COUNT_PER_TYPE.getMeterId() + ".ChangeMove";
        when(rootScope.getReportedMoveCountsByType()).thenReturn(Map.of("ChangeMove", 5L));
        listenerCaptor.getValue().phaseEnded(new LocalSearchPhaseScope<>(rootScope, 1));
        assertThat(registry.find(meterName).tags(rootTags).gauge().value()).isEqualTo(5);
        var foreignCounter = new AtomicLong(7);
        Metrics.gauge(meterName, foreignTags, foreignCounter);
        assertThat(registry.find(meterName).gauges()).hasSize(3);

        statistic.unregister(solver);

        assertThat(registry.find(meterName).gauges())
            .singleElement()
            .satisfies(
                gauge -> {
                  assertThat(gauge.getId().getTags()).containsExactlyElementsOf(foreignTags);
                  assertThat(gauge.value()).isEqualTo(7);
                });
      } finally {
        Metrics.removeRegistry(registry);
      }
    } finally {
      registry.close();
    }
  }

  @SuppressWarnings("unchecked")
  private static SolverScope<TestdataSolution> scope(Tags tags) {
    SolverScope<TestdataSolution> scope = mock(SolverScope.class);
    when(scope.getMonitoringTags()).thenReturn(tags);
    when(scope.getReportedMoveCountsByType()).thenReturn(Map.of("ChangeMove", 3L));
    when(scope.getMetricRun()).thenReturn(new SolverMetricRun());
    return scope;
  }
}
