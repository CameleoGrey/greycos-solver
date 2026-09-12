package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AlnsMetricsTest {
  @Test
  void moveCountGaugesFollowActivePhaseAcrossSolverRuns() {
    var tag = UUID.randomUUID().toString();
    var tags = Tags.of("test.id", tag);
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        var config =
            moveCountSolverConfig()
                .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new AlnsPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)),
                    localSearchPhase(3, 2),
                    localSearchPhase(5, 2),
                    new AlnsPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)),
                    localSearchPhase(7, 2));
        var solver = buildSolver(config, tag);
        var completedPhases =
            assertMoveCountsAtPhaseBoundaries(solver, registry, tags, Map.of(2, 3L, 3, 5L, 5, 7L));
        for (var run = 0; run < 2; run++) {
          solver.solve(TestdataSolution.generateUninitializedSolution(3, 6));
          assertThat(completedPhases).containsExactly(1, 2, 3, 4, 5);
          assertMoveCounts(registry, tags, 7L, 7L);
          completedPhases.clear();
        }

        var replacement =
            buildSolver(
                moveCountSolverConfig()
                    .withPhases(new ConstructionHeuristicPhaseConfig(), localSearchPhase(11, 2)),
                tag);
        var replacementPhases =
            assertMoveCountsAtPhaseBoundaries(replacement, registry, tags, Map.of(1, 11L));
        replacement.solve(TestdataSolution.generateUninitializedSolution(3, 6));
        assertThat(replacementPhases).containsExactly(1);
        assertMoveCounts(registry, tags, 11L, 11L);
      } finally {
        Metrics.removeRegistry(registry);
        removeTestMeters(tag);
      }
    } finally {
      registry.close();
    }
  }

  @Test
  void localSearchWithoutCompletedStepsResetsMoveCountGauges() {
    var tag = UUID.randomUUID().toString();
    var tags = Tags.of("test.id", tag);
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        var solver =
            buildSolver(
                moveCountSolverConfig()
                    .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new AlnsPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)),
                        localSearchPhase(3, 0)),
                tag);
        var completedPhases =
            assertMoveCountsAtPhaseBoundaries(solver, registry, tags, Map.of(2, 0L));
        solver.solve(TestdataSolution.generateUninitializedSolution(3, 6));
        assertThat(completedPhases).containsExactly(1, 2);
        assertMoveCounts(registry, tags, 0L, 0L);
      } finally {
        Metrics.removeRegistry(registry);
        removeTestMeters(tag);
      }
    } finally {
      registry.close();
    }
  }

  @Test
  void moveCountGaugeHandoffPreservesOtherSolverTags() {
    var tag = UUID.randomUUID().toString();
    var tags = Tags.of("test.id", tag);
    var otherTags = tags.and("solver.id", "other");
    var otherSelected = new AtomicLong(23L);
    var otherAccepted = new AtomicLong(17L);
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        Metrics.gauge(
            SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected", otherTags, otherSelected);
        Metrics.gauge(
            SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted", otherTags, otherAccepted);
        var selectedGauge = moveCountGauge(registry, otherTags, "selected");
        var acceptedGauge = moveCountGauge(registry, otherTags, "accepted");
        var solver =
            buildSolver(
                moveCountSolverConfig()
                    .withPhases(
                        new ConstructionHeuristicPhaseConfig(),
                        new AlnsPhaseConfig()
                            .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)),
                        localSearchPhase(3, 2)),
                tag);
        solver.solve(TestdataSolution.generateUninitializedSolution(3, 6));
        assertMoveCounts(registry, tags, 3L, 3L);
        assertMoveCounts(registry, otherTags, 23L, 17L);
        assertThat(moveCountGauge(registry, otherTags, "selected")).isSameAs(selectedGauge);
        assertThat(moveCountGauge(registry, otherTags, "accepted")).isSameAs(acceptedGauge);
        otherSelected.set(29L);
        otherAccepted.set(19L);
        assertMoveCounts(registry, otherTags, 29L, 19L);
      } finally {
        Metrics.removeRegistry(registry);
        removeTestMeters(tag);
      }
    } finally {
      registry.close();
    }
  }

  @Test
  void recordsTrialsAndProbesSeparatelyAndPublishesScores() {
    var tag = UUID.randomUUID().toString();
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        var config =
            new SolverConfig()
                .withSolutionClass(TestdataSolution.class)
                .withEntityClasses(TestdataEntity.class)
                .withConstraintProviderClass(TestdataConstraintProvider.class)
                .withMonitoringConfig(
                    new MonitoringConfig()
                        .withSolverMetricList(
                            List.of(
                                SolverMetric.ALNS_STATISTICS,
                                SolverMetric.MOVE_COUNT_PER_STEP,
                                SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF,
                                SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                                SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE)))
                .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new AlnsPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(4)));
        var solver =
            (DefaultSolver<TestdataSolution>)
                SolverFactory.<TestdataSolution>create(config).buildSolver();
        solver.setMonitorTagMap(Map.of("test.id", tag));
        solver.solve(TestdataSolution.generateUninitializedSolution(3, 6));
        assertThat(
                registry.find("greycos.solver.alns.trials").tag("test.id", tag).counters().stream()
                    .mapToDouble(counter -> counter.count())
                    .sum())
            .isEqualTo(4.0);
        assertThat(
                registry.find("greycos.solver.alns.probes").tag("test.id", tag).counters().stream()
                    .mapToDouble(counter -> counter.count())
                    .sum())
            .isPositive();
        assertThat(
                registry
                    .find("greycos.solver.alns.repair.duration")
                    .tag("test.id", tag)
                    .timers()
                    .stream()
                    .mapToLong(timer -> timer.count())
                    .sum())
            .isEqualTo(4L);
        assertThat(
                registry
                    .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")
                    .tag("test.id", tag)
                    .gauge()
                    .value())
            .isEqualTo(1.0);
        assertThat(registry.getMeters())
            .anySatisfy(
                meter ->
                    assertThat(meter.getId().getName())
                        .startsWith(SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF.getMeterId()));
        assertThat(registry.getMeters())
            .anySatisfy(
                meter ->
                    assertThat(meter.getId().getName())
                        .startsWith(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE.getMeterId()));
      } finally {
        Metrics.removeRegistry(registry);
        removeTestMeters(tag);
      }
    } finally {
      registry.close();
    }
  }

  @Test
  void aNewPhaseRuntimeRebindsOperatorWeightGauge() {
    var tag = UUID.randomUUID().toString();
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        @SuppressWarnings("unchecked")
        SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
        when(solverScope.getMonitoringTags()).thenReturn(Tags.of("test.id", tag));
        when(solverScope.isMetricEnabled(SolverMetric.ALNS_STATISTICS)).thenReturn(true);
        var phaseScope = new AlnsPhaseScope<>(solverScope, 0);
        phaseScope.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
        var first = new AlnsMetrics<TestdataSolution>();
        first.phaseStarted(phaseScope);
        first.recordWeights(solverScope, Map.of("destroy/random", 2.0));
        var second = new AlnsMetrics<TestdataSolution>();
        second.phaseStarted(phaseScope);
        second.recordWeights(solverScope, Map.of("destroy/random", 7.0));
        assertThat(
                registry
                    .find("greycos.solver.alns.operator.weight")
                    .tag("test.id", tag)
                    .gauge()
                    .value())
            .isEqualTo(7.0);
      } finally {
        Metrics.removeRegistry(registry);
        removeTestMeters(tag);
      }
    } finally {
      registry.close();
    }
  }

  private static SolverConfig moveCountSolverConfig() {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(TestdataConstraintProvider.class)
        .withMonitoringConfig(
            new MonitoringConfig().withSolverMetricList(List.of(SolverMetric.MOVE_COUNT_PER_STEP)));
  }

  private static LocalSearchPhaseConfig localSearchPhase(int acceptedCountLimit, int stepLimit) {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(new ChangeMoveSelectorConfig())
        .withAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
        .withForagerConfig(
            new LocalSearchForagerConfig().withAcceptedCountLimit(acceptedCountLimit))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(stepLimit));
  }

  private static DefaultSolver<TestdataSolution> buildSolver(SolverConfig config, String tag) {
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    solver.setMonitorTagMap(Map.of("test.id", tag));
    return solver;
  }

  private static List<Integer> assertMoveCountsAtPhaseBoundaries(
      DefaultSolver<TestdataSolution> solver,
      SimpleMeterRegistry registry,
      Tags tags,
      Map<Integer, Long> expectedLocalSearchCounts) {
    var completedPhases = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> stepScope) {
            var phaseScope = stepScope.getPhaseScope();
            if (stepScope.getStepIndex() == 0
                && (phaseScope instanceof AlnsPhaseScope<?>
                    || phaseScope instanceof LocalSearchPhaseScope<?>)) {
              assertMoveCounts(registry, tags, 0L, 0L);
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            // Local search updates its gauges after stepEnded listeners have been notified.
            if (phaseScope instanceof LocalSearchPhaseScope<?> localSearchScope) {
              var expectedCount = expectedLocalSearchCounts.get(phaseScope.getPhaseIndex());
              assertThat(expectedCount).isNotNull();
              assertMoveCounts(registry, tags, expectedCount, expectedCount);
              if (localSearchScope.getNextStepIndex() > 0) {
                var step = localSearchScope.getLastCompletedStepScope();
                assertMoveCounts(
                    registry, tags, step.getSelectedMoveCount(), step.getAcceptedMoveCount());
              }
            } else if (phaseScope instanceof AlnsPhaseScope<?> alnsScope) {
              assertThat(alnsScope.getNextStepIndex()).isPositive();
              assertMoveCounts(
                  registry, tags, 1L, alnsScope.getLastCompletedStepScope().isAccepted() ? 1L : 0L);
            } else {
              return;
            }
            completedPhases.add(phaseScope.getPhaseIndex());
          }
        });
    return completedPhases;
  }

  private static void assertMoveCounts(
      SimpleMeterRegistry registry, Tags tags, long selected, long accepted) {
    assertThat(moveCountGauge(registry, tags, "selected").value()).isEqualTo((double) selected);
    assertThat(moveCountGauge(registry, tags, "accepted").value()).isEqualTo((double) accepted);
  }

  private static Gauge moveCountGauge(SimpleMeterRegistry registry, Tags tags, String suffix) {
    var gauges =
        registry
            .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + "." + suffix)
            .gauges()
            .stream()
            .filter(gauge -> Tags.of(gauge.getId().getTags()).equals(tags))
            .toList();
    assertThat(gauges).hasSize(1);
    return gauges.getFirst();
  }

  private static void removeTestMeters(String tag) {
    for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
      if (tag.equals(meter.getId().getTag("test.id"))) {
        Metrics.globalRegistry.remove(meter);
      }
    }
  }
}
