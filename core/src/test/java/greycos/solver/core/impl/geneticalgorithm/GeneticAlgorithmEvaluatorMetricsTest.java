package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Timeout(30)
class GeneticAlgorithmEvaluatorMetricsTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4})
  void bestClonesAndConstraintMetricsDescribeCommittedCoordinatorStateAcrossReuse(int workers) {
    try (var meters = new TestMeters()) {
      var solver = solver(config(phase(workers, 40)).withMonitoringConfig(monitoring()));
      solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
      var coordinator = Thread.currentThread();
      var published = new ArrayList<TestdataSolution>();
      var snapshots = new ArrayList<List<String>>();
      var completed = new AtomicInteger();
      var pooled = new AtomicInteger();
      solver.addEventListener(
          event -> {
            assertThat(Thread.currentThread()).isSameAs(coordinator);
            var clone = event.getNewBestSolution();
            assertReplay(clone);
            var working = solver.getSolverScope().getScoreDirector().getWorkingSolution();
            assertThat(clone).isNotSameAs(working);
            for (int i = 0; i < clone.getEntityList().size(); i++) {
              assertThat(clone.getEntityList().get(i)).isNotSameAs(working.getEntityList().get(i));
            }
            published.add(clone);
            snapshots.add(assignments(clone));
          });
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
              assertThat(Thread.currentThread()).isSameAs(coordinator);
              if (scope.getStepIndex() == 0) meters.assertMoveCounts(0, 0);
            }

            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              assertThat(Thread.currentThread()).isSameAs(coordinator);
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              if (!step.isSeeding()) pooled.incrementAndGet();
              meters.assertMoveCounts(1, step.isAdmitted() ? 1 : 0);
              assertThat(solver.getSolverScope().getMoveEvaluationCount())
                  .isEqualTo(completed.incrementAndGet());
              assertReplay(step.getWorkingSolution());
              meters.assertConstraintScore(
                  SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                  step.<SimpleScore>getScore().raw().score());
              meters.assertConstraintScore(
                  SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
                  step.getPhaseScope().<SimpleScore>getBestScore().raw().score());
              double count =
                  step.getScoreDirector().getConstraintMatchTotalMap().values().stream()
                      .mapToLong(total -> total.getConstraintMatchCount())
                      .sum();
              meters.assertConstraintCount(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE, count);
            }
          });

      for (int run = 0; run < 2; run++) {
        completed.set(0);
        pooled.set(0);
        var result = solver.solve(problem(20, 16));

        assertReplay(result);
        assertThat(completed).hasValue(40);
        assertThat(pooled).hasValue(37);
        assertThat(
                solver.getSolverScope().getMoveEvaluationCountPerType().values().stream()
                    .mapToLong(Long::longValue)
                    .sum())
            .isEqualTo(40);
        meters.assertConstraintScore(
            SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE, result.getScore().score());
        assertThat(published).hasSizeGreaterThan(1);
        for (int i = 0; i < published.size(); i++) {
          assertThat(assignments(published.get(i))).isEqualTo(snapshots.get(i));
          assertReplay(published.get(i));
        }
      }
    }
  }

  @Test
  void workerCountDoesNotChangeConstraintSamplesOrLogicalMoveMetricTrace() {
    var traces = new ArrayList<List<Sample>>();
    var finalTypeCounts = new ArrayList<Map<String, Long>>();
    for (int workers : new int[] {1, 2, 4}) {
      try (var meters = new TestMeters()) {
        var solver = solver(config(phase(workers, 40)).withMonitoringConfig(monitoring()));
        solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
        var trace = new ArrayList<Sample>();
        solver.addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
                var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
                trace.add(
                    new Sample(
                        step.getStepIndex(),
                        step.getMoveTypeDescription(),
                        step.getOutcome(),
                        step.isAdmitted(),
                        assignments(step.getWorkingSolution()),
                        meters.constraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE),
                        meters.constraintScore(SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE)));
              }
            });

        assertReplay(solver.solve(problem(20, 16)));

        traces.add(trace);
        finalTypeCounts.add(Map.copyOf(solver.getSolverScope().getMoveEvaluationCountPerType()));
      }
    }
    assertThat(traces.get(1)).containsExactlyElementsOf(traces.getFirst());
    assertThat(traces.get(2)).containsExactlyElementsOf(traces.getFirst());
    assertThat(finalTypeCounts)
        .allSatisfy(counts -> assertThat(counts).isEqualTo(finalTypeCounts.getFirst()));
  }

  @Test
  void failedPooledCompletionRestoresTentativeMoveGaugesAndCredit() {
    try (var meters = new TestMeters()) {
      var solver =
          solver(config(phase(3, 20).withPopulationSize(1)).withMonitoringConfig(monitoring()));
      solver.setMonitorTags(SolverTags.withProblemId(meters.tag));
      var failure = new IllegalStateException("intentional pooled completion failure");
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var step = (GeneticAlgorithmStepScope<TestdataSolution>) scope;
              assertThat(step.isSeeding()).isFalse();
              meters.assertMoveCounts(1, step.isAdmitted() ? 1 : 0);
              assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(1);
              throw failure;
            }
          });

      assertThatThrownBy(() -> solver.solve(problem(20, 16))).isSameAs(failure);

      meters.assertMoveCounts(0, 0);
      assertThat(solver.getSolverScope().getMoveEvaluationCount()).isZero();
      assertThat(
              solver.getSolverScope().getMoveEvaluationCountPerType().values().stream()
                  .mapToLong(Long::longValue)
                  .sum())
          .isZero();
      assertReplay(solver.getSolverScope().getBestSolution());
      meters.assertConstraintScore(
          SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE,
          solver.getSolverScope().getBestSolution().getScore().score());
    }
  }

  private static GeneticAlgorithmPhaseConfig phase(int workers, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withEvaluatorThreadCount(workers)
        .withPopulationSize(4)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static MonitoringConfig monitoring() {
    return new MonitoringConfig()
        .withConstraintMatchMetricSampleInterval(1)
        .withSolverMetricList(
            List.of(
                SolverMetric.MOVE_COUNT_PER_STEP,
                SolverMetric.MOVE_COUNT_PER_TYPE,
                SolverMetric.SCORE_CALCULATION_COUNT,
                SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE));
  }

  private record Sample(
      int step,
      String moveType,
      GeneticAlgorithmOutcome outcome,
      boolean admitted,
      List<String> assignments,
      double stepConstraintScore,
      double bestConstraintScore) {}

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

    private double constraintScore(SolverMetric metric) {
      var gauges = registry.find(metric.getMeterId() + ".score").tags(tags).gauges();
      assertThat(gauges).isNotEmpty();
      return gauges.stream().mapToDouble(gauge -> gauge.value()).sum();
    }

    private void assertConstraintScore(SolverMetric metric, double score) {
      assertThat(constraintScore(metric)).isEqualTo(score);
    }

    private void assertConstraintCount(SolverMetric metric, double count) {
      var gauges = registry.find(metric.getMeterId() + ".count").tags(tags).gauges();
      assertThat(gauges).isNotEmpty();
      assertThat(gauges.stream().mapToDouble(gauge -> gauge.value()).sum()).isEqualTo(count);
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
