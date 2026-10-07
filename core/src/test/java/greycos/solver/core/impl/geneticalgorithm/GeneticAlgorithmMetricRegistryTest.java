package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.SolverMetricRun;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class GeneticAlgorithmMetricRegistryTest {

  @Test
  @SuppressWarnings("unchecked")
  void moveCountGaugeOwnershipAndRollbackRemainPhaseLocal() {
    try (var meters = new TestMeters()) {
      SolverScope<TestdataSolution> solverScope = mock(SolverScope.class);
      when(solverScope.getMetricRun()).thenReturn(new SolverMetricRun());
      when(solverScope.getMonitoringTags()).thenReturn(meters.tags);
      when(solverScope.isMetricEnabled(SolverMetric.MOVE_COUNT_PER_STEP)).thenReturn(true);
      var phase = new GeneticAlgorithmPhaseScope<>(solverScope, 0);
      phase.getLastCompletedStepScope().setScore(InnerScore.fullyAssigned(SimpleScore.ZERO));
      var previous = phase.getLastCompletedStepScope();
      var first = new GeneticAlgorithmMetrics<TestdataSolution>();
      first.phaseStarted(phase);
      meters.assertMoveCounts(0, 0);
      var admitted = new GeneticAlgorithmStepScope<>(phase, 0);
      admitted.setAdmitted(true);
      first.record(admitted);
      meters.assertMoveCounts(1, 1);
      first.restoreStepCounts(previous);
      meters.assertMoveCounts(0, 0);
      first.record(admitted);
      var rejected = new GeneticAlgorithmStepScope<>(phase, 1);
      first.record(rejected);
      meters.assertMoveCounts(1, 0);
      first.restoreStepCounts(admitted);
      meters.assertMoveCounts(1, 1);

      var replacement = new GeneticAlgorithmMetrics<TestdataSolution>();
      replacement.phaseStarted(phase);
      meters.assertMoveCounts(0, 0);
      // The new gauges must own the replacement helper's counters even while the old helper lives.
      first.record(admitted);
      meters.assertMoveCounts(0, 0);
      replacement.record(rejected);
      meters.assertMoveCounts(1, 0);
      solverScope.getMetricRun().seal();
      replacement.record(admitted);
      meters.assertMoveCounts(1, 0);
    }
  }

  @Test
  void realSolvesPublishCurrentAttemptAndConstraintValuesAcrossPhasesAndReuse() {
    try (var meters = new TestMeters()) {
      var first = phase(9);
      var second = phase(6);
      var solver =
          solver(config(first).withPhases(first, second).withMonitoringConfig(monitoring()));
      solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
      var completed = new AtomicInteger();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepStarted(AbstractStepScope<TestdataSolution> step) {
              if (step.getStepIndex() == 0) {
                meters.assertMoveCounts(0, 0);
              }
            }

            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              meters.assertMoveCounts(1, step.isAdmitted() ? 1 : 0);
              assertThat(step.getPhaseScope().getSolverScope().getMoveEvaluationCount())
                  .isEqualTo(completed.incrementAndGet());
              meters.assertConstraintScore(
                  SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                  step.<SimpleScore>getScore().raw().score());
              meters.assertConstraintScore(
                  SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
                  step.getPhaseScope().<SimpleScore>getBestScore().raw().score());
            }
          });
      for (var run = 0; run < 2; run++) {
        completed.set(0);
        var result = solver.solve(problem(5, 8));
        assertReplay(result);
        assertThat(completed).hasValue(15);
        meters.assertConstraintScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, result.getScore().score());
        assertThat(
                meters
                    .registry
                    .find(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE.getMeterId() + ".count")
                    .tags(meters.tags)
                    .gauges())
            .isNotEmpty();
      }
    }
  }

  @Test
  void completionCallbackFailureRollsBackTentativeMoveCountGaugesAndCredit() {
    try (var meters = new TestMeters()) {
      var solver = solver(config(phase(4)).withMonitoringConfig(monitoring()));
      solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
      var failure = new IllegalStateException("injected GA completion callback failure");
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              meters.assertMoveCounts(1, step.isAdmitted() ? 1 : 0);
              assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(1);
              throw failure;
            }
          });
      assertThatThrownBy(() -> solver.solve(problem(5, 8))).isSameAs(failure);
      meters.assertMoveCounts(0, 0);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
    }
  }

  private static GeneticAlgorithmPhaseConfig phase(int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withPopulationSize(3)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static MonitoringConfig monitoring() {
    return new MonitoringConfig()
        .withConstraintMatchMetricSampleInterval(1)
        .withSolverMetricList(
            List.of(
                SolverMetric.MOVE_COUNT_PER_STEP,
                SolverMetric.MOVE_EVALUATION_COUNT,
                SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE));
  }

  private static final class TestMeters implements AutoCloseable {
    private final String tag = UUID.randomUUID().toString();
    private final Tags tags = SolverTags.withProblemId(tag).asTags();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private TestMeters() {
      Metrics.addRegistry(registry);
    }

    private void assertMoveCounts(long selected, long accepted) {
      assertThat(
              registry
                  .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")
                  .tags(tags)
                  .gauge())
          .isNotNull()
          .satisfies(gauge -> assertThat(gauge.value()).isEqualTo((double) selected));
      assertThat(
              registry
                  .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted")
                  .tags(tags)
                  .gauge())
          .isNotNull()
          .satisfies(gauge -> assertThat(gauge.value()).isEqualTo((double) accepted));
    }

    private void assertConstraintScore(SolverMetric metric, double score) {
      var gauges = registry.find(metric.getMeterId() + ".score").tags(tags).gauges();
      assertThat(gauges).isNotEmpty();
      assertThat(gauges.stream().mapToDouble(gauge -> gauge.value()).sum()).isEqualTo(score);
    }

    @Override
    public void close() {
      Metrics.removeRegistry(registry);
      registry.close();
      for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
        if (tag.equals(meter.getId().getTag("problem.id"))) {
          Metrics.globalRegistry.remove(meter);
        }
      }
    }
  }
}
