package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;
import greycos.solver.core.impl.solver.monitoring.statistic.PickedMoveBestScoreDiffStatistic;
import greycos.solver.core.impl.solver.monitoring.statistic.PickedMoveStepScoreDiffStatistic;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AlnsMigrationMetricTest {
  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void importedImprovementIsNeverCreditedToTheFollowingOperatorPair() {
    var tag = UUID.randomUUID().toString();
    var registry = new SimpleMeterRegistry();
    Metrics.addRegistry(registry);
    var config = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    var solver =
        spy(
            (DefaultSolver<TestdataSolution>)
                SolverFactory.<TestdataSolution>create(config).buildSolver());
    solver.setMonitorTagMap(Map.of("test.id", tag));
    var stepStatistic = new PickedMoveStepScoreDiffStatistic<TestdataSolution>();
    var bestStatistic = new PickedMoveBestScoreDiffStatistic<TestdataSolution, SimpleScore>();
    try {
      stepStatistic.register(solver);
      bestStatistic.register(solver);
      ArgumentCaptor<PhaseLifecycleListener> captor =
          ArgumentCaptor.forClass(PhaseLifecycleListener.class);
      verify(solver, times(2)).addPhaseLifecycleListener(captor.capture());
      var listeners = captor.getAllValues();
      var scope = new AlnsPhaseScope<>(solver.getSolverScope(), 0);
      solver.getSolverScope().setBestScore(InnerScore.fullyAssigned(SimpleScore.of(-100)));
      scope.reset();
      listeners.forEach(listener -> listener.phaseStarted(scope));
      var first = trial(scope, 0, -100, -90, AlnsOutcome.NEW_BEST);
      listeners.forEach(listener -> listener.stepEnded(first));
      assertGauge(registry, tag, SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF, 10.0);
      assertGauge(registry, tag, SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF, 10.0);

      // Migration improves the incumbent from -90 to -50 between trials, without a trial event.
      solver.getSolverScope().setBestScore(InnerScore.fullyAssigned(SimpleScore.of(-50)));
      var rejected = trial(scope, 1, -50, -50, AlnsOutcome.REJECTED);
      listeners.forEach(listener -> listener.stepEnded(rejected));
      assertGauge(registry, tag, SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF, 0.0);

      // The operator's next genuine improvement is 5, even though the previous best event was -90.
      var improved = trial(scope, 2, -50, -45, AlnsOutcome.NEW_BEST);
      listeners.forEach(listener -> listener.stepEnded(improved));
      assertGauge(registry, tag, SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF, 5.0);
      assertGauge(registry, tag, SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF, 5.0);
    } finally {
      stepStatistic.unregister(solver);
      bestStatistic.unregister(solver);
      solver.getSolverScope().getScoreDirector().close();
      Metrics.removeRegistry(registry);
      registry.close();
      for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
        if (tag.equals(meter.getId().getTag("test.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
    }
  }

  private static AlnsStepScope<TestdataSolution> trial(
      AlnsPhaseScope<TestdataSolution> scope,
      int index,
      int before,
      int after,
      AlnsOutcome outcome) {
    var step = new AlnsStepScope<>(scope, index);
    step.setScore(InnerScore.fullyAssigned(SimpleScore.of(after)));
    step.setBestScoreImproved(outcome == AlnsOutcome.NEW_BEST);
    step.setTrialResult(
        new AlnsTrialResult<>(
            index,
            "destroy",
            "repair",
            outcome,
            SimpleScore.of(before),
            SimpleScore.of(after),
            SimpleScore.of(after),
            SimpleScore.of(before),
            SimpleScore.of(after),
            1,
            0,
            1,
            10));
    return step;
  }

  private static void assertGauge(
      SimpleMeterRegistry registry, String tag, SolverMetric metric, double expected) {
    assertThat(
            registry
                .find(SolverMetricUtil.getGaugeName(metric, "score"))
                .tags("test.id", tag, "move.type", "destroy/repair")
                .gauge()
                .value())
        .isEqualTo(expected);
  }
}
