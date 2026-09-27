package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.AbstractMeterTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class IslandAccountingAndMetricsTest extends AbstractMeterTest {
  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void includesIslandWorkAndMetricsAcrossMixedPhasesAndRepeatedSolves(String workers) {
    var config = config(workers);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var samples = new ArrayList<SolverMetricSample>();
    solver.getSolverScope().addMetricSampleListener(samples::add);
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        for (int run = 0; run < 2; run++) {
          samples.clear();
          CountingCalculator.calls.set(0);
          solver.solve(problem());
          var scope = solver.getSolverScope();
          assertThat(scope.getReportedScoreCalculationCount())
              .isEqualTo(CountingCalculator.calls.get());
          assertThat(scope.getReportedMoveEvaluationCount())
              .isGreaterThan(scope.getMoveEvaluationCount());
          assertThat(scope.getReportedMoveCountsByType().values()).allMatch(count -> count > 0);
          var childSteps =
              samples.stream()
                  .filter(sample -> sample.kind() == SolverMetricSample.Kind.STEP)
                  .filter(sample -> !sample.source().equals("root"))
                  .toList();
          assertThat(childSteps).hasSize(10);
          assertThat(childSteps)
              .extracting(SolverMetricSample::source)
              .contains("phase-1/island-0", "phase-1/island-1");
          var meterNames =
              childSteps.stream()
                  .flatMap(sample -> sample.measurements().keySet().stream())
                  .map(id -> id.getName())
                  .distinct()
                  .toList();
          assertThat(meterNames)
              .anyMatch(
                  name ->
                      name.startsWith(SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF.getMeterId()));
          assertThat(meterNames)
              .anyMatch(
                  name ->
                      name.startsWith(SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF.getMeterId()));
          assertThat(samples.getLast().kind()).isEqualTo(SolverMetricSample.Kind.FINAL);
          assertThat(samples.getLast().work().scoreCalculationCount())
              .isEqualTo(CountingCalculator.calls.get());
          assertThat(scope.getMetricRun().isActive()).isFalse();
          var bestSamples =
              samples.stream()
                  .filter(sample -> sample.kind() == SolverMetricSample.Kind.BEST)
                  .filter(sample -> sample.source().equals("root"))
                  .toList();
          assertThat(bestSamples).hasSizeGreaterThan(1);
          assertThat(
                  bestSamples
                      .getFirst()
                      .gaugeValue(
                          SolverMetric.BEST_SOLUTION_MUTATION.getMeterId(),
                          scope.getMonitoringTags()))
              .isZero();
          assertThat(bestSamples)
              .anyMatch(
                  sample -> {
                    var mutations =
                        sample.gaugeValue(
                            SolverMetric.BEST_SOLUTION_MUTATION.getMeterId(),
                            scope.getMonitoringTags());
                    return mutations != null && mutations > 0;
                  });
          var meterCount = registry.getMeters().size();
          var childScope = new SolverScope<TestdataSolution>();
          childScope.setMetricSource("phase-1/island-0");
          childScope.setMetricRun(scope.getMetricRun());
          childScope.setMonitoringTags(scope.getMonitoringTags().and("island.id", "0"));
          @SuppressWarnings("unchecked")
          DefaultSolver<TestdataSolution> childSolver = mock(DefaultSolver.class);
          when(childSolver.getSolverScope()).thenReturn(childScope);
          SolverMetric.SCORE_CALCULATION_COUNT.register(childSolver);
          assertThat(registry.getMeters()).hasSize(meterCount);
        }
      } finally {
        Metrics.removeRegistry(registry);
      }
    } finally {
      registry.close();
    }
  }

  @ParameterizedTest
  @EnumSource(AlnsMoveThreadingMode.class)
  void includesPhysicalAlnsWorkerCalculationsWithoutDoubleCounting(AlnsMoveThreadingMode mode) {
    var alns =
        new AlnsPhaseConfig()
            .withMoveThreadCount("2")
            .withMoveThreadingMode(mode)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(3));
    if (mode == AlnsMoveThreadingMode.REPAIR_ATTEMPTS) {
      alns.withRepairAttemptCount(2)
          .withRepairOperators(
              new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY));
    }
    var config =
        config("NONE")
            .withPhases(
                new IslandModelPhaseConfig().withIslandCount(2).withPhaseConfigList(List.of(alns)));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var samples = new ArrayList<SolverMetricSample>();
    solver.getSolverScope().addMetricSampleListener(samples::add);
    CountingCalculator.calls.set(0);
    solver.solve(problem());
    assertThat(solver.getSolverScope().getReportedScoreCalculationCount())
        .isEqualTo(CountingCalculator.calls.get());
    assertThat(solver.getSolverScope().getReportedMoveEvaluationCount()).isEqualTo(6);
    assertThat(samples.stream().filter(sample -> sample.kind() == SolverMetricSample.Kind.STEP))
        .hasSize(6);
    assertThat(samples.getLast().work().scoreCalculationCount())
        .isEqualTo(CountingCalculator.calls.get());
  }

  private static SolverConfig config(String workers) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(0L)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig().withEasyScoreCalculatorClass(CountingCalculator.class))
        .withMonitoringConfig(
            new MonitoringConfig()
                .withSolverMetricList(
                    List.of(
                        SolverMetric.BEST_SCORE,
                        SolverMetric.BEST_SOLUTION_MUTATION,
                        SolverMetric.SCORE_CALCULATION_COUNT,
                        SolverMetric.MOVE_EVALUATION_COUNT,
                        SolverMetric.MOVE_COUNT_PER_TYPE,
                        SolverMetric.MOVE_COUNT_PER_STEP,
                        SolverMetric.STEP_SCORE,
                        SolverMetric.PICKED_MOVE_TYPE_STEP_SCORE_DIFF,
                        SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF)))
        .withPhases(
            new LocalSearchPhaseConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)),
            new IslandModelPhaseConfig()
                .withIslandCount(2)
                .withMoveThreadCount(workers)
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(5)),
            new LocalSearchPhaseConfig()
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
  }

  private static TestdataSolution problem() {
    var problem = new TestdataSolution("problem");
    var values = List.of(new TestdataValue("v1"), new TestdataValue("v2"), new TestdataValue("v3"));
    problem.setValueList(values);
    problem.setEntityList(
        List.of(
            new TestdataEntity("e1", values.getFirst()),
            new TestdataEntity("e2", values.getFirst()),
            new TestdataEntity("e3", values.getFirst()),
            new TestdataEntity("e4", values.getFirst())));
    return problem;
  }

  public static final class CountingCalculator extends TestdataEasyScoreCalculator {
    static final AtomicLong calls = new AtomicLong();

    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      calls.incrementAndGet();
      return super.calculateScore(solution);
    }
  }
}
