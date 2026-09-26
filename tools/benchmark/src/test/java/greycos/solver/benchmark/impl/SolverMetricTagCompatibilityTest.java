package greycos.solver.benchmark.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.benchmark.api.PlannerBenchmarkFactory;
import greycos.solver.benchmark.config.PlannerBenchmarkConfig;
import greycos.solver.benchmark.config.ProblemBenchmarksConfig;
import greycos.solver.benchmark.config.statistic.ProblemStatisticType;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class SolverMetricTagCompatibilityTest extends AbstractMeterTest {

  @TempDir Path outputDirectory;

  @Test
  @Timeout(30)
  void standaloneManagerBenchmarkAndIslandsSharePrometheusTagKeys() throws Exception {
    var registrations = new CopyOnWriteArrayList<Meter.Id>();
    var registry =
        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT).throwExceptionOnRegistrationFailure();
    try {
      registry
          .config()
          .onMeterAdded(
              meter -> {
                if (meter
                        .getId()
                        .getName()
                        .equals(SolverMetric.SCORE_CALCULATION_COUNT.getMeterId())
                    || meter
                        .getId()
                        .getName()
                        .equals(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")) {
                  registrations.add(meter.getId());
                }
              });
      // Keep the meter family alive between solves, as a concurrently running solver would.
      for (var meterName :
          List.of(
              SolverMetric.SCORE_CALCULATION_COUNT.getMeterId(),
              SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected")) {
        Gauge.builder(meterName, new AtomicInteger(), AtomicInteger::get)
            .tags(SolverTags.withProblemId("anchor").asTags())
            .strongReference(true)
            .register(registry);
      }
      Metrics.addRegistry(registry);
      try {
        SolverFactory.<TestdataSolution>create(config()).buildSolver().solve(problem());
        try (var manager = SolverManager.<TestdataSolution>create(config())) {
          assertThat(manager.solve("managed", problem()).getFinalBestSolution().getScore())
              .isNotNull();
        }
        var benchmarkConfig =
            PlannerBenchmarkConfig.createFromSolverConfig(config())
                .withBenchmarkDirectory(outputDirectory.toFile())
                .withWarmUpMillisecondsSpentLimit(0L);
        benchmarkConfig
            .getSolverBenchmarkConfigList()
            .getFirst()
            .setProblemBenchmarksConfig(
                new ProblemBenchmarksConfig()
                    .withProblemStatisticTypes(
                        ProblemStatisticType.SCORE_CALCULATION_SPEED,
                        ProblemStatisticType.MOVE_COUNT_PER_STEP));
        PlannerBenchmarkFactory.create(benchmarkConfig)
            .buildPlannerBenchmark(problem())
            .benchmark();
        var islandConfig =
            config()
                .withMonitoringConfig(
                    new MonitoringConfig()
                        .withSolverMetricList(
                            List.of(
                                SolverMetric.SCORE_CALCULATION_COUNT,
                                SolverMetric.MOVE_COUNT_PER_STEP)))
                .withPhases(
                    new IslandModelPhaseConfig()
                        .withIslandCount(2)
                        .withPhaseConfigList(
                            List.of(
                                new AlnsPhaseConfig()
                                    .withTerminationConfig(
                                        new TerminationConfig().withStepCountLimit(2)))));
        SolverFactory.<TestdataSolution>create(islandConfig).buildSolver().solve(problem());

        assertThat(registrations)
            .allSatisfy(
                id ->
                    assertThat(id.getTags())
                        .extracting(Tag::getKey)
                        .containsExactly("island.id", "problem.id"));
        assertThat(registrations)
            .extracting(id -> id.getTag("island.id"))
            .contains("root", "0", "1");
        assertThat(registrations)
            .extracting(id -> id.getTag("problem.id"))
            .contains("managed", "1970-01-01T00:00Z")
            .anyMatch(
                id -> id != null && id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"));
        assertThat(registry.scrape()).contains("problem_id=\"anchor\"", "island_id=\"root\"");
      } finally {
        Metrics.removeRegistry(registry);
      }
    } finally {
      registry.close();
    }
  }

  private static SolverConfig config() {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    config.setClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    return config;
  }

  private static TestdataSolution problem() {
    return TestdataSolution.generateSolution(3, 6);
  }
}
